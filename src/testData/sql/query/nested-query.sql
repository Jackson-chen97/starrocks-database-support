WITH active_users AS (
    SELECT user_id
    FROM dim_users
    WHERE status = 'ACTIVE'
)
SELECT
    o.order_id,
    (
        SELECT max(event_time)
        FROM order_events e
        WHERE e.order_id = o.order_id
    ) AS latest_event_time
FROM fact_orders o
WHERE EXISTS (
    SELECT 1
    FROM active_users u
    WHERE u.user_id = o.user_id
)
  AND o.store_id IN (
    SELECT store_id
    FROM dim_store
    WHERE region = 'north'
);
