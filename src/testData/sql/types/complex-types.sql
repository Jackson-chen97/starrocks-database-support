CREATE TABLE complex_type_sample
(
    id BIGINT NOT NULL,
    tags ARRAY<VARCHAR(64)>,
    attributes MAP<VARCHAR(64), VARCHAR(256)>,
    profile STRUCT<name VARCHAR(64), age INT>,
    event_json JSON,
    user_bitmap BITMAP,
    visit_hll HLL,
    amount DECIMAL128(38, 6)
)
DUPLICATE KEY (id)
DISTRIBUTED BY HASH(id) BUCKETS 8
PROPERTIES (
    "compression" = "LZ4"
);

SELECT
    CAST(id AS LARGEINT),
    CAST(amount AS DECIMAL64(18, 2)),
    tags[1],
    attributes['channel'],
    profile.name
FROM complex_type_sample;
