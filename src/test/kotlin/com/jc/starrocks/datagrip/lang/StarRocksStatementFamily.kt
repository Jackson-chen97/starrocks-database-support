package com.jc.starrocks.datagrip.lang

enum class StarRocksStatementFamily {
    QUERY,
    DML,
    TABLE_DDL,
    VIEW,
    MATERIALIZED_VIEW,
    CATALOG,
    RESOURCE,
    LOAD,
    ROUTINE_LOAD,
    TASK,
    EXPORT,
    BACKUP_RESTORE,
    ADMIN
}
