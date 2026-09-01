SELECT
    abs(-1),
    concat('a', 'b'),
    coalesce(NULL, 'fallback'),
    ifnull(NULL, 'fallback'),
    date_trunc('day', event_time),
    get_json_string(event_json, '$.store_id'),
    array_length(tags),
    to_bitmap(user_id)
FROM function_sample;

SELECT
    store_id,
    sum(item_qty),
    count(DISTINCT order_id),
    avg(sale_amt),
    bitmap_union(user_bitmap),
    bitmap_union_int(CAST(member_id AS BIGINT)),
    row_number() OVER (PARTITION BY store_id ORDER BY sale_amt DESC) AS rn
FROM function_sample
GROUP BY store_id;
