package com.jc.starrocks.datagrip.lang

// Test fixture coverage metadata; not part of the runtime plugin model.

/**
 * Grammar coverage milestones for the StarRocks parser.
 *
 * These entries track the acceptance scenarios covered by grammar fixtures.
 */
enum class StarRocksGrammarMilestone {
    QUERY,
    EXPRESSIONS,
    TYPES,
    DML,
    TABLE_DDL,
    MATERIALIZED_VIEW,
    CATALOG_RESOURCE,
    LOAD_EXPORT,
    TASK_ANALYZE,
    BACKUP_RESTORE,
    ADMINISTRATION,
    FUNCTIONS,
    LOCAL_RESOLUTION,
    FORMATTING,
    COMPLETION,

    @Deprecated("Use QUERY.")
    CORE_QUERY,
    @Deprecated("Use QUERY.")
    QUALIFY_AND_WINDOWS,
    @Deprecated("Use QUERY.")
    TABLE_FUNCTIONS,
    @Deprecated("Use TABLE_DDL.")
    CREATE_TABLE_DDL,
    @Deprecated("Use MATERIALIZED_VIEW.")
    MATERIALIZED_VIEW_DDL,
    @Deprecated("Use LOCAL_RESOLUTION.")
    LOCAL_DDL_RESOLUTION
}
