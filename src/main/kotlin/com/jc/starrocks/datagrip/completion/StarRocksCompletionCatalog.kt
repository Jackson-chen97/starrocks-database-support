package com.jc.starrocks.datagrip.completion

object StarRocksCompletionCatalog {
    val PROPERTIES: List<String> = listOf(
        "aws.s3.region",
        "aws.s3.use_instance_profile",
        "column_separator",
        "compression",
        "datacache.enable",
        "desired_concurrent_number",
        "enable_metastore_cache",
        "enable_persistent_index",
        "hive.metastore.uris",
        "kafka_broker_list",
        "kafka_topic",
        "line_delimiter",
        "replicated_storage",
        "spark.executor.memory",
        "spark.master",
        "timeout",
        "type"
    ).sorted()

    val SNIPPETS: List<StarRocksSnippet> = listOf(
        StarRocksSnippet(
            lookup = "CREATE TABLE",
            insertText = "CREATE TABLE table_name (\n    id BIGINT NOT NULL\n)\nDUPLICATE KEY (id)\nDISTRIBUTED BY HASH(id) BUCKETS 8\nPROPERTIES (\n    \"compression\" = \"LZ4\"\n);"
        ),
        StarRocksSnippet(
            lookup = "CREATE MATERIALIZED VIEW",
            insertText = "CREATE MATERIALIZED VIEW mv_name\nDISTRIBUTED BY HASH(id) BUCKETS 8\nREFRESH ASYNC\nPROPERTIES (\n    \"replicated_storage\" = \"true\"\n)\nAS\nSELECT id\nFROM table_name;"
        ),
        StarRocksSnippet(
            lookup = "QUALIFY",
            insertText = "QUALIFY row_number() OVER (PARTITION BY id ORDER BY updated_at DESC) = 1"
        ),
        StarRocksSnippet(
            lookup = "UNNEST",
            insertText = "UNNEST(array_column)"
        ),
        StarRocksSnippet(
            lookup = "INSERT OVERWRITE",
            insertText = "INSERT OVERWRITE target_table\nSELECT *\nFROM source_table;"
        )
    )
}

data class StarRocksSnippet(
    val lookup: String,
    val insertText: String
)
