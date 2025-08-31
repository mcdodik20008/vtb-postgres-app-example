--liquibase formatted sql

--changeset mcdodik:create-model
-- Клиенты
CREATE TABLE customers
(
    customer_id BIGSERIAL PRIMARY KEY,
    full_name   TEXT NOT NULL,
    email       TEXT, -- часто ищут, но индекс отсутствует
    phone       TEXT,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- Заказы
CREATE TABLE orders
(
    order_id     BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT NOT NULL REFERENCES customers (customer_id),
    status       TEXT   NOT NULL, -- "NEW", "PAID", "CANCELLED"
    total_amount NUMERIC(12, 2),
    created_at   TIMESTAMPTZ DEFAULT now()
);

-- Товары
CREATE TABLE products
(
    product_id BIGSERIAL PRIMARY KEY,
    name       TEXT           NOT NULL,
    price      NUMERIC(12, 2) NOT NULL,
    category   TEXT, -- денормализация, а не справочник
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Позиции заказов
CREATE TABLE order_items
(
    item_id     BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES orders (order_id),
    product_id  BIGINT NOT NULL REFERENCES products (product_id),
    quantity    INT    NOT NULL,
    total_price NUMERIC(12, 2) -- дублирует product.price*quantity
);

-- Платежи
CREATE TABLE payments
(
    payment_id BIGSERIAL PRIMARY KEY,
    order_id   BIGINT REFERENCES orders (order_id),
    amount     NUMERIC(12, 2) NOT NULL,
    method     TEXT, -- "CARD", "CASH", "TRANSFER"
    paid_at    TIMESTAMPTZ DEFAULT now()
);

-- Лог действий
CREATE TABLE activity_log
(
    log_id      BIGSERIAL PRIMARY KEY,
    customer_id BIGINT REFERENCES customers (customer_id),
    action      TEXT NOT NULL,
    details     TEXT,
    created_at  TIMESTAMPTZ DEFAULT now()
);
