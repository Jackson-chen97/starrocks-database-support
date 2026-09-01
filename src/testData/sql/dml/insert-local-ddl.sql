CREATE TABLE IF NOT EXISTS dws.dws_trade_sale_by_order_ri
(
    biz_date DATE NOT NULL COMMENT 'business date',
    store_id STRING NOT NULL COMMENT 'store id',
    order_id STRING NOT NULL COMMENT 'order id',
    sale_time DATETIME NULL COMMENT 'sale time',
    member_card_no STRING NULL COMMENT 'member card number'
)
PRIMARY KEY (biz_date, store_id, order_id)
DISTRIBUTED BY HASH(order_id) BUCKETS 12
PROPERTIES (
    "compression" = "LZ4"
);

INSERT INTO dws.dws_trade_sale_by_order_ri
SELECT
    biz_date,
    store_id,
    order_id,
    sale_time,
    member_card_no
FROM ods.ods_trade_sale_detail;
