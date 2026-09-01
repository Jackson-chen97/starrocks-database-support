CREATE TABLE IF NOT EXISTS dws.dws_trade_sale_by_order_ri
(
    biz_date DATE NOT NULL COMMENT 'business date',
    store_id STRING NOT NULL COMMENT 'store id',
    order_id STRING NOT NULL COMMENT 'order id',
    sale_time DATETIME NULL COMMENT 'sale time',
    member_card_no STRING NULL COMMENT 'member card number'
)
PRIMARY KEY (biz_date, store_id, order_id)
COMMENT 'daily order summary'
PARTITION BY biz_date
DISTRIBUTED BY HASH(order_id) BUCKETS 12
PROPERTIES (
    "datacache.enable" = "true",
    "enable_persistent_index" = "true",
    "compression" = "LZ4"
);

CREATE TABLE dws.dws_trade_sale_by_order_ctas
PRIMARY KEY (biz_date, store_id, order_id)
DISTRIBUTED BY HASH(order_id) BUCKETS 12
PROPERTIES (
    "compression" = "LZ4"
)
AS
SELECT
    biz_date,
    store_id,
    order_id,
    sale_time,
    member_card_no
FROM dws.dws_trade_sale_by_order_ri;

CREATE TABLE dws.dws_trade_sale_by_order_copy LIKE dws.dws_trade_sale_by_order_ri;

CREATE TEMPORARY EXTERNAL TABLE IF NOT EXISTS dws.dws_trade_sale_by_order_daily
PARTITION BY date_trunc('day', biz_date)
DISTRIBUTED BY HASH(order_id) BUCKETS 8
PROPERTIES ("replication_num" = "1")
LIKE dws.dws_trade_sale_by_order_ri;
