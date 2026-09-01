WITH base AS (
    SELECT
        store_id,
        order_id,
        sale_amt,
        event_time
    FROM dws.current_orders
),
archived AS (
    SELECT
        store_id,
        order_id,
        sale_amt,
        event_time
    FROM dws.history_orders
)
SELECT
    store_id,
    order_id,
    row_number() OVER recent_orders AS rn
FROM base
WINDOW recent_orders AS (PARTITION BY store_id ORDER BY event_time DESC)
UNION ALL
SELECT
    store_id,
    order_id,
    row_number() OVER recent_orders AS rn
FROM archived
WINDOW recent_orders AS (PARTITION BY store_id ORDER BY event_time DESC)
EXCEPT
SELECT
    store_id,
    order_id,
    1 AS rn
FROM dws.refund_orders;
