CREATE VIEW IF NOT EXISTS dws.v_trade_sale_summary
(
    biz_date COMMENT 'business date',
    order_id COMMENT 'order id',
    total_amount COMMENT 'total amount'
)
COMMENT 'daily sale summary view'
AS
SELECT
    biz_date,
    order_id,
    sum(amount) AS total_amount
FROM dws.dws_trade_sale_by_order_ri
GROUP BY biz_date, order_id;

SELECT
    biz_date,
    total_amount
FROM dws.v_trade_sale_summary;
