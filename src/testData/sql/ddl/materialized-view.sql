CREATE MATERIALIZED VIEW mv_ads_trade_sale_by_order_ri
COMMENT 'order materialized view'
DISTRIBUTED BY HASH(order_id) BUCKETS 12
REFRESH ASYNC
PARTITION BY date_trunc('day', biz_date)
PROPERTIES (
    "replicated_storage" = "true"
)
AS
SELECT
    biz_date,
    order_id,
    sale_time,
    member_card_no
FROM dws.dws_trade_sale_by_order_ri;

CREATE MATERIALIZED VIEW mv_scheduled_orders
COMMENT 'scheduled orders'
DISTRIBUTED BY HASH(order_id, region) BUCKETS 12
REFRESH DEFERRED ASYNC START('2026-07-01 10:00:00') EVERY (INTERVAL 1 DAY)
PARTITION BY date_trunc('day', sale_time)
ORDER BY (biz_date, order_id)
PROPERTIES (
    "partition_refresh_number" = "3",
    "query_rewrite_consistency" = "force_mv"
)
AS
SELECT biz_date, region, sale_time, order_id
FROM dws.dws_trade_sale_by_order_ri;

CREATE MATERIALIZED VIEW mv_incremental_orders
REFRESH DEFERRED MANUAL
PARTITION BY biz_date
PROPERTIES ("refresh_mode" = "INCREMENTAL")
AS SELECT biz_date, order_id
FROM dws.dws_trade_sale_by_order_ri;
