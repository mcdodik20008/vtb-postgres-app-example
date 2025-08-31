--liquibase formatted sql

--changeset mcdodik:insert_customers
INSERT INTO core.customers(full_name, email, phone, created_at)
SELECT
    'Customer ' || g,
    'user' || g || '@mail.com',
    '79' || floor(random()*100000000)::TEXT,
    now() - (random() * interval '365 days')
FROM generate_series(1, 500000) g;

--changeset mcdodik:insert_products
INSERT INTO core.products(name, price, category, created_at)
SELECT
    'Product ' || g,
    round((10 + random()*1000)::numeric, 2),
    (ARRAY['electronics','clothes','food','books'])[ceil(random()*4)],
    now() - (random() * interval '100 days')
FROM generate_series(1, 20000) g;

--changeset mcdodik:insert_orders
INSERT INTO core.orders(customer_id, status, total_amount, created_at)
SELECT
    c.customer_id,
    (ARRAY['NEW','PAID','CANCELLED'])[ceil(random()*3)],
    round((random()*5000)::numeric, 2),
    now() - (random() * interval '29 days')
FROM core.customers c
         CROSS JOIN generate_series(1, 2) g;  -- по 2 заказа на клиента (≈1M строк)

--changeset mcdodik:insert_order_items
INSERT INTO core.order_items(order_id, product_id, quantity, total_price)
SELECT
    o.order_id,
    floor(random()*20000)::BIGINT + 1,
    ceil(random()*5)::INT,
    round((random()*500)::numeric, 2)
FROM core.orders o
         CROSS JOIN generate_series(1, 2) g;



--changeset mcdodik:insert_payments
INSERT INTO core.payments(order_id, amount, method, paid_at)
SELECT
    o.order_id,
    round((random()*5000)::numeric, 2),
    (ARRAY['CARD','CASH','TRANSFER'])[ceil(random()*3)],
    o.created_at + (random() * interval '5 days')
FROM core.orders o
WHERE o.status = 'PAID';



--changeset mcdodik:insert_activity_log
INSERT INTO core.activity_log(customer_id, action, details, created_at)
SELECT
    c.customer_id,
    (ARRAY['LOGIN','LOGOUT','VIEW_PRODUCT','ADD_TO_CART','CHECKOUT'])[ceil(random()*5)],
    'Details ' || g,
    now() - (random() * interval '90 days')
FROM core.customers c
         TABLESAMPLE SYSTEM (10) -- случайный поднабор клиентов
         CROSS JOIN generate_series(1, 400) g;
