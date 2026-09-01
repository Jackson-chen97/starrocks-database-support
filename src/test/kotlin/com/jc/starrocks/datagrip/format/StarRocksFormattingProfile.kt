package com.jc.starrocks.datagrip.format

// Assertions describing expected formatter behavior for tests.

object StarRocksFormattingProfile {
    val QUERY_CLAUSE_ORDER: List<String> = listOf(
        "WITH",
        "SELECT",
        "FROM",
        "WHERE",
        "GROUP BY",
        "HAVING",
        "QUALIFY",
        "ORDER BY",
        "LIMIT"
    )

    val TABLE_DDL_CLAUSE_ORDER: List<String> = listOf(
        "CREATE TABLE",
        "ENGINE",
        "PRIMARY KEY",
        "DUPLICATE KEY",
        "UNIQUE KEY",
        "AGGREGATE KEY",
        "COMMENT",
        "PARTITION BY",
        "DISTRIBUTED BY",
        "ORDER BY",
        "PROPERTIES",
        "AS SELECT"
    )

    val MATERIALIZED_VIEW_CLAUSE_ORDER: List<String> = listOf(
        "CREATE MATERIALIZED VIEW",
        "COMMENT",
        "DISTRIBUTED BY",
        "REFRESH",
        "PARTITION BY",
        "ORDER BY",
        "PROPERTIES",
        "AS SELECT"
    )

    val DML_CLAUSE_ORDER: List<String> = listOf(
        "INSERT",
        "UPDATE",
        "DELETE",
        "MERGE",
        "SET",
        "USING",
        "ON",
        "WHEN",
        "VALUES",
        "SELECT"
    )

    val LINE_BREAK_BEFORE_CLAUSES: Set<String> = (
        QUERY_CLAUSE_ORDER +
            TABLE_DDL_CLAUSE_ORDER +
            MATERIALIZED_VIEW_CLAUSE_ORDER +
            listOf("PROPERTIES", "REFRESH", "VALUES", "WHEN MATCHED", "WHEN NOT MATCHED")
        ).toSet()

    const val USE_PLATFORM_SQL_FORMATTER: Boolean = true
    const val USE_GENERIC_SQL_FORMATTER_BRIDGE: Boolean = false
    const val USE_SAFE_DDL_FORMATTER: Boolean = false
    const val USE_WHOLE_FILE_STRING_REWRITE: Boolean = false
}
