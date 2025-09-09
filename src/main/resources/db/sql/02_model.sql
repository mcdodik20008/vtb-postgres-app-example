--liquibase formatted sql

--changeset mcdodik:create-model
CREATE TABLE core.customers
(
    customer_id BIGSERIAL PRIMARY KEY,
    full_name   TEXT NOT NULL,
    email       TEXT,
    phone       TEXT,
    profile     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- PARTITIONED: orders по времени
CREATE TABLE core.orders
(
    order_id     BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT NOT NULL,
    status       TEXT   NOT NULL,
    total_amount NUMERIC(12, 2),
    metadata     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT orders_status_chk CHECK (status IN ('NEW','PAID','CANCELLED')),
    CONSTRAINT orders_customer_fk
        FOREIGN KEY (customer_id) REFERENCES core.customers(customer_id)
            ON DELETE CASCADE
            DEFERRABLE INITIALLY DEFERRED
) PARTITION BY RANGE (created_at);

-- default-partition, чтобы не падать на «забытых» диапазонах
CREATE TABLE core.orders_p_default PARTITION OF core.orders DEFAULT;

CREATE TABLE core.products
(
    product_id BIGSERIAL PRIMARY KEY,
    name       TEXT           NOT NULL,
    price      NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    category   TEXT,
    attrs      JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE core.order_items
(
    item_id     BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    quantity    INT    NOT NULL CHECK (quantity > 0),
    total_price NUMERIC(12, 2) CHECK (total_price >= 0),
    unit_price  NUMERIC(12,2) GENERATED ALWAYS AS (CASE WHEN quantity <> 0 THEN total_price / quantity ELSE NULL END) STORED,
    CONSTRAINT oi_order_fk
        FOREIGN KEY (order_id)   REFERENCES core.orders(order_id)
            ON DELETE CASCADE
            DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT oi_product_fk
        FOREIGN KEY (product_id) REFERENCES core.products(product_id)
            ON DELETE RESTRICT
            DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE core.payments
(
    payment_id BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    amount     NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    method     TEXT NOT NULL CHECK (method IN ('CARD','CASH','TRANSFER')),
    paid_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT payments_order_fk
        FOREIGN KEY (order_id) REFERENCES core.orders(order_id)
            ON DELETE CASCADE
            DEFERRABLE INITIALLY DEFERRED
);

-- PARTITIONED: activity_log по времени
CREATE TABLE core.activity_log
(
    log_id      BIGSERIAL PRIMARY KEY,
    customer_id BIGINT,
    action      TEXT NOT NULL,
    details     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT al_customer_fk
        FOREIGN KEY (customer_id) REFERENCES core.customers(customer_id)
            ON DELETE SET NULL
            DEFERRABLE INITIALLY DEFERRED
) PARTITION BY RANGE (created_at);

CREATE TABLE core.activity_log_p_default PARTITION OF core.activity_log DEFAULT;

--changeset mcdodik:indexes
-- emails ищутся часто: partial unique + нормализация
CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_email_nz
    ON core.customers (lower(email))
    WHERE email IS NOT NULL;

-- GIN по JSONB: широкие запросы по профилю пользователя
CREATE INDEX IF NOT EXISTS idx_customers_profile_gin
    ON core.customers USING GIN (profile);

-- продукты: поиск по имени (равенство/ILIKE без trgm) + GIN по attrs
CREATE INDEX IF NOT EXISTS idx_products_name_lower
    ON core.products (lower(name));
CREATE INDEX IF NOT EXISTS idx_products_attrs_gin
    ON core.products USING GIN (attrs);

-- orders: HOT-путь «клиент + время», включаем проекции
CREATE INDEX IF NOT EXISTS idx_orders_customer_created_inc
    ON core.orders (customer_id, created_at DESC) INCLUDE (status, total_amount);

-- orders: оплаченные — частый фильтр
CREATE INDEX IF NOT EXISTS idx_orders_paid_partial
    ON core.orders (created_at DESC)
    WHERE status = 'PAID';

-- orders/activity_log: BRIN по времени (большие ленты)
CREATE INDEX IF NOT EXISTS brin_orders_created
    ON core.orders USING BRIN (created_at) WITH (pages_per_range = 32);
CREATE INDEX IF NOT EXISTS brin_activity_log_created
    ON core.activity_log USING BRIN (created_at) WITH (pages_per_range = 32);

-- payments: покрывающий под выборки по заказу
CREATE INDEX IF NOT EXISTS idx_payments_order_inc
    ON core.payments (order_id, paid_at DESC) INCLUDE (amount, method);

-- activity_log.details: «тяжёлый» GIN по path_ops (быстрый @> / ?& / ?|)
CREATE INDEX IF NOT EXISTS idx_activity_details_gin_path
    ON core.activity_log USING GIN (details jsonb_path_ops);

--changeset mcdodik:audit-triggers
CREATE OR REPLACE FUNCTION core.set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END $$;

CREATE TRIGGER tg_customers_updated_at
    BEFORE UPDATE ON core.customers
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TRIGGER tg_orders_updated_at
    BEFORE UPDATE ON core.orders
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TRIGGER tg_products_updated_at
    BEFORE UPDATE ON core.products
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

--changeset mcdodik:rls (runOnChange:true)
-- RLS-заготовки. НЕ включаем по умолчанию, чтобы не поломать чтение.
-- Для боевого включения: ALTER TABLE core.orders ENABLE ROW LEVEL SECURITY;
-- И политика, например, по текущему app-контексту:
DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM pg_policies WHERE schemaname='core' AND tablename='orders' AND policyname='orders_allow_all'
        ) THEN
            EXECUTE 'CREATE POLICY orders_allow_all ON core.orders FOR ALL USING (true) WITH CHECK (true)';
        END IF;
    END$$;

DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM pg_policies WHERE schemaname='core' AND tablename='activity_log' AND policyname='al_allow_all'
        ) THEN
            EXECUTE 'CREATE POLICY al_allow_all ON core.activity_log FOR ALL USING (true) WITH CHECK (true)';
        END IF;
    END$$;

--changeset mcdodik:constraints-and-fks-validate
-- Отдельный шаг на VALIDATE при необходимости (можно отложить на внепиковое окно)
-- ALTER TABLE core.orders       VALIDATE CONSTRAINT orders_customer_fk;
-- ALTER TABLE core.order_items  VALIDATE CONSTRAINT oi_order_fk;
-- ALTER TABLE core.order_items  VALIDATE CONSTRAINT oi_product_fk;
-- ALTER TABLE core.payments     VALIDATE CONSTRAINT payments_order_fk;
-- ALTER TABLE core.activity_log VALIDATE CONSTRAINT al_customer_fk;
