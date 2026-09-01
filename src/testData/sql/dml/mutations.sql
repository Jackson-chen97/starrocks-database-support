INSERT OVERWRITE dws.dws_trade_sale_by_order_ri
SELECT
    biz_date,
    store_id,
    order_id,
    sale_time,
    member_card_no
FROM tmp_trade_sale_by_order_ri;

UPDATE dws.dws_trade_sale_by_order_ri
SET member_card_no = source.member_card_no
FROM tmp_member_card source
WHERE dws.dws_trade_sale_by_order_ri.order_id = source.order_id;

DELETE FROM dws.dws_trade_sale_by_order_ri
WHERE biz_date < '2024-01-01';

MERGE INTO dws.dws_trade_sale_by_order_ri target
USING tmp_trade_sale_by_order_ri source
ON target.order_id = source.order_id
WHEN MATCHED THEN UPDATE SET
    target.member_card_no = source.member_card_no
WHEN NOT MATCHED THEN INSERT
(
    biz_date,
    store_id,
    order_id,
    sale_time,
    member_card_no
)
VALUES
(
    source.biz_date,
    source.store_id,
    source.order_id,
    source.sale_time,
    source.member_card_no
);
