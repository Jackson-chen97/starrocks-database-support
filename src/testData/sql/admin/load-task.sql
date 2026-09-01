LOAD LABEL db1.label1
(
    DATA INFILE("s3://bucket/path/file.csv")
    INTO TABLE fact_order
    COLUMNS TERMINATED BY ","
    FORMAT AS "csv"
)
WITH BROKER
PROPERTIES (
    "timeout" = "3600"
);

CREATE ROUTINE LOAD db1.routine_load_job ON fact_order
COLUMNS TERMINATED BY ","
PROPERTIES (
    "desired_concurrent_number" = "3"
)
FROM KAFKA (
    "kafka_broker_list" = "localhost:9092",
    "kafka_topic" = "orders"
);

SUBMIT TASK refresh_mv_task AS
REFRESH MATERIALIZED VIEW mv_ads_trade_sale_by_order_ri;

REFRESH MATERIALIZED VIEW mv_scheduled_orders PARTITION START('2026-01-01') END('2026-02-01') FORCE;

CANCEL REFRESH MATERIALIZED VIEW mv_scheduled_orders;
