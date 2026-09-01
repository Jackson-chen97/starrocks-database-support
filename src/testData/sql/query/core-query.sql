SELECT
    concat('2020-01-01', ' '),
    CAST('20200101' AS BIGINT),
    biz_date,
    store_id,
    order_id,
    settle_time,
    settle_status
FROM dwm.dwm_trade_pay_ri
WHERE biz_date >= @start_date
  AND biz_date < @end_date
  AND settle_status IS NOT NULL
QUALIFY row_number() OVER (PARTITION BY biz_date, store_id, order_id ORDER BY settle_time DESC) = 1;

SELECT
    store_id,
    SUM(item_qty) AS item_qty,
    COUNT(DISTINCT order_id) AS order_qty,
    bitmap_union_int(CAST(member_card_no AS BIGINT)) AS member_qty
FROM sale_data
GROUP BY GROUPING SETS ((store_id), ());

SELECT
    order_id,
    tag
FROM sale_data
JOIN UNNEST(tags) AS unnested_tags(tag)
WHERE tag IS NOT NULL;

SELECT
    DATE_FORMAT(biz_date, '%Y-%m') AS biz_m,
    COUNT(*) AS order_qty
FROM dwm.dwm_trade_sale_ri_v2
GROUP BY biz_m;

SELECT *
FROM (
    SELECT
        DATE_FORMAT(biz_date, '%Y-%m-%d') AS biz_m,
        order_id,
        order_detail_id
    FROM dwm.dwm_trade_sale_ri
    WHERE biz_date = '2025-10-27'
    GROUP BY biz_m, order_id, order_detail_id
) a1
FULL JOIN (
    SELECT
        DATE_FORMAT(biz_date, '%Y-%m-%d') AS biz_m,
        order_id,
        order_detail_id
    FROM dwm.dwm_trade_sale_ri_v2
    WHERE biz_date = '2025-10-27'
) a2
ON a1.order_id = a2.order_id;
