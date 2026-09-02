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

CREATE MATERIALIZED VIEW `mv_ads_ztc_goods_active_rate_ba_region_monthly` (
    `stat_month` COMMENT "统计月份",
    `ba_region_id` COMMENT "大区ID",
    `ba_region_name` COMMENT "大区名称",
    `goods_id` COMMENT "商品ID",
    `goods_name` COMMENT "商品名称",
    `active_goods_cnt` COMMENT "动销商品数",
    `total_goods_cnt` COMMENT "在架商品总数",
    `active_rate` COMMENT "商品动销率",
    `active_amt` COMMENT "动销金额",
    `etl_time` COMMENT "etl时间"
) COMMENT "【指标】商品动销率-大区-月度"
DISTRIBUTED BY HASH(`stat_month`)
REFRESH ASYNC EVERY(INTERVAL 1 HOUR)
PROPERTIES ("replicated_storage"="true","replication_num"="1","storage_medium"="HDD")
AS WITH ba_regions AS (
    SELECT region_id, MAX(region_name) AS region_name
    FROM mv_dim_ztc_ba_service_provider
    WHERE region_id IS NOT NULL
    GROUP BY region_id
), monthly_goods AS (
    SELECT stat_month, ba_region_id, goods_id, active_amt
    FROM dws.dws_trade_sale_by_order_ri
)
SELECT
    m.stat_month,
    r.ba_region_id,
    MAX(r.region_name) AS ba_region_name,
    COUNT(DISTINCT m.goods_id) AS active_goods_cnt,
    SUM(m.active_amt) AS active_amt,
    CURRENT_TIMESTAMP() AS etl_time
FROM monthly_goods m
JOIN ba_regions r ON m.ba_region_id = r.region_id
GROUP BY m.stat_month, r.ba_region_id;
