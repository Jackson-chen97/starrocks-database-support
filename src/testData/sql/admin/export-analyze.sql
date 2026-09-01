EXPORT TABLE dws.dws_trade_sale_by_order_ri
TO "s3://bucket/export/order/"
PROPERTIES (
    "column_separator" = ",",
    "line_delimiter" = "\n"
);

CANCEL EXPORT FROM dws WHERE QUERYID = "query_id";

ANALYZE TABLE dws.dws_trade_sale_by_order_ri;
ANALYZE TABLE dws.dws_trade_sale_by_order_ri UPDATE HISTOGRAM ON biz_date, store_id;
SHOW ANALYZE STATUS;
SHOW STATS META;
