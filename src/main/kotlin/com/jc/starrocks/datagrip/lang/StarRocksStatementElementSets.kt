package com.jc.starrocks.datagrip.lang

import com.intellij.psi.tree.IElementType
import com.intellij.sql.psi.SqlCompositeElementTypes

object StarRocksStatementElementSets {
    val PLATFORM_STATEMENT_TYPES: Set<IElementType> = setOf(
        StarRocksElementTypes.SQL_SELECT_STATEMENT,
        StarRocksElementTypes.SQL_INSERT_STATEMENT,
        StarRocksElementTypes.SQL_UPDATE_STATEMENT,
        StarRocksElementTypes.SQL_DELETE_STATEMENT,
        StarRocksElementTypes.SQL_MERGE_STATEMENT,
        StarRocksElementTypes.SQL_SET_STATEMENT,
        StarRocksElementTypes.SQL_CALL_STATEMENT,
        StarRocksElementTypes.SQL_START_TRANSACTION_STATEMENT,
        StarRocksElementTypes.SQL_COMMIT_STATEMENT,
        StarRocksElementTypes.SQL_ROLLBACK_STATEMENT,
        StarRocksElementTypes.SQL_USE_SCHEMA_STATEMENT,
        SqlCompositeElementTypes.SQL_USE_SCHEMA_STATEMENT,
        SqlCompositeElementTypes.SQL_USE_CATALOG_STATEMENT,
        SqlCompositeElementTypes.SQL_USE_NAMESPACE_STATEMENT,
        StarRocksElementTypes.SQL_EXPLAIN_STATEMENT,
        StarRocksElementTypes.SQL_TRUNCATE_TABLE_STATEMENT,
        StarRocksElementTypes.SQL_CREATE_TABLE_STATEMENT,
        StarRocksElementTypes.SQL_CREATE_VIEW_STATEMENT,
        StarRocksElementTypes.SQL_CREATE_MATERIALIZED_VIEW_STATEMENT,
        StarRocksElementTypes.SQL_CREATE_CATALOG_STATEMENT,
        StarRocksElementTypes.SQL_CREATE_SCHEMA_STATEMENT,
        StarRocksElementTypes.SQL_CREATE_INDEX_STATEMENT,
        StarRocksElementTypes.SQL_ALTER_SCHEMA_STATEMENT,
        StarRocksElementTypes.SQL_ALTER_TABLE_STATEMENT,
        StarRocksElementTypes.SQL_ALTER_VIEW_STATEMENT,
        StarRocksElementTypes.SQL_ALTER_CATALOG_STATEMENT
    )

    val STARROCKS_STATEMENT_TYPES: Set<IElementType> = setOf(
        StarRocksElementTypes.ALTER_MATERIALIZED_VIEW_STATEMENT,
        StarRocksElementTypes.ALTER_RESOURCE_STATEMENT,
        StarRocksElementTypes.ALTER_ROUTINE_LOAD_STATEMENT,
        StarRocksElementTypes.CREATE_RESOURCE_STATEMENT,
        StarRocksElementTypes.CREATE_REPOSITORY_STATEMENT,
        StarRocksElementTypes.CREATE_ROUTINE_LOAD_STATEMENT,
        StarRocksElementTypes.DROP_CATALOG_STATEMENT,
        StarRocksElementTypes.DROP_INDEX_STATEMENT,
        StarRocksElementTypes.DROP_MATERIALIZED_VIEW_STATEMENT,
        StarRocksElementTypes.DROP_REPOSITORY_STATEMENT,
        StarRocksElementTypes.DROP_RESOURCE_STATEMENT,
        StarRocksElementTypes.DROP_SCHEMA_STATEMENT,
        StarRocksElementTypes.DROP_TABLE_STATEMENT,
        StarRocksElementTypes.DROP_VIEW_STATEMENT,
        StarRocksElementTypes.REFRESH_MATERIALIZED_VIEW_STATEMENT,
        StarRocksElementTypes.LOAD_STATEMENT,
        StarRocksElementTypes.CANCEL_LOAD_STATEMENT,
        StarRocksElementTypes.CANCEL_REFRESH_MATERIALIZED_VIEW_STATEMENT,
        StarRocksElementTypes.TASK_STATEMENT,
        StarRocksElementTypes.EXPORT_STATEMENT,
        StarRocksElementTypes.BACKUP_RESTORE_STATEMENT,
        StarRocksElementTypes.ANALYZE_STATEMENT,
        StarRocksElementTypes.DESCRIBE_STATEMENT,
        StarRocksElementTypes.ADMIN_STATEMENT,
        StarRocksElementTypes.SHOW_STATEMENT,
        StarRocksElementTypes.KILL_STATEMENT,
        StarRocksElementTypes.SYNC_STATEMENT,
        StarRocksElementTypes.UNSET_STATEMENT,
        StarRocksElementTypes.CREATE_USER_STATEMENT,
        StarRocksElementTypes.ALTER_USER_STATEMENT,
        StarRocksElementTypes.DROP_USER_STATEMENT,
        StarRocksElementTypes.CREATE_ROLE_STATEMENT,
        StarRocksElementTypes.ALTER_ROLE_STATEMENT,
        StarRocksElementTypes.DROP_ROLE_STATEMENT,
        StarRocksElementTypes.SET_PASSWORD_STATEMENT,
        StarRocksElementTypes.GRANT_STATEMENT,
        StarRocksElementTypes.REVOKE_STATEMENT
    )

    val STATEMENT_TYPES: Set<IElementType> = PLATFORM_STATEMENT_TYPES + STARROCKS_STATEMENT_TYPES

    val QUERY_SCOPE_TYPES: Set<IElementType> = setOf(
        StarRocksElementTypes.SQL_SELECT_STATEMENT,
        StarRocksElementTypes.SQL_INSERT_STATEMENT,
        StarRocksElementTypes.SQL_UPDATE_STATEMENT,
        StarRocksElementTypes.SQL_DELETE_STATEMENT,
        StarRocksElementTypes.SQL_MERGE_STATEMENT,
        StarRocksElementTypes.SQL_QUERY_EXPRESSION,
        StarRocksElementTypes.SQL_PARENTHESIZED_QUERY_EXPRESSION,
        StarRocksElementTypes.SQL_WITH_QUERY_EXPRESSION
    )
}
