package com.jc.starrocks.datagrip.lang

import com.intellij.psi.tree.IElementType
import com.intellij.sql.psi.SqlCompositeElementTypes

internal object StarRocksPlatformElementSets {
    val statementTypes: Set<IElementType> by lazy {
        StarRocksStatementElementSets.STARROCKS_STATEMENT_TYPES
    }

    val genericStatementTypes: Set<IElementType> = setOf(
        SqlCompositeElementTypes.SQL_START_TRANSACTION_STATEMENT
    )

    val registeredStarRocksTypes: Set<IElementType> by lazy {
        setOf(
            StarRocksElementTypes.SQL_FROM_CLAUSE,
            StarRocksElementTypes.SQL_TABLE_EXPRESSION,
            StarRocksElementTypes.SQL_PARENTHESIZED_JOIN_EXPRESSION,
            StarRocksElementTypes.SQL_JOIN_EXPRESSION,
            StarRocksElementTypes.SQL_JOIN_CONDITION_CLAUSE,
            StarRocksElementTypes.SQL_USING_CLAUSE,
            StarRocksElementTypes.SQL_COLUMN_DEFINITION,
            SqlCompositeElementTypes.SQL_TABLE_REFERENCE,
            StarRocksElementTypes.SQL_INSERT_STATEMENT,
            StarRocksElementTypes.SQL_UPDATE_STATEMENT,
            StarRocksElementTypes.SQL_DELETE_STATEMENT,
            StarRocksElementTypes.SQL_MERGE_STATEMENT,
            StarRocksElementTypes.SQL_CREATE_CATALOG_STATEMENT,
            StarRocksElementTypes.SQL_CREATE_SCHEMA_STATEMENT,
            StarRocksElementTypes.SQL_CREATE_INDEX_STATEMENT,
            StarRocksElementTypes.SQL_ALTER_TABLE_STATEMENT,
            StarRocksElementTypes.SQL_ALTER_SCHEMA_STATEMENT,
            StarRocksElementTypes.SQL_ALTER_VIEW_STATEMENT,
            StarRocksElementTypes.SQL_ALTER_CATALOG_STATEMENT,
            StarRocksElementTypes.SQL_SET_STATEMENT,
            StarRocksElementTypes.SQL_EXPLAIN_STATEMENT,
            SqlCompositeElementTypes.SQL_GRANT_STATEMENT,
            SqlCompositeElementTypes.SQL_REVOKE_STATEMENT,
            StarRocksElementTypes.GRANT_STATEMENT,
            StarRocksElementTypes.REVOKE_STATEMENT,
            StarRocksElementTypes.SQL_COMMIT_STATEMENT,
            StarRocksElementTypes.SQL_ROLLBACK_STATEMENT,
            StarRocksElementTypes.SQL_TRUNCATE_TABLE_STATEMENT
        )
    }

    val registeredPlatformTypes: Set<IElementType> by lazy {
        setOf(
            StarRocksElementTypes.SQL_CREATE_TABLE_STATEMENT,
            StarRocksElementTypes.SQL_CREATE_VIEW_STATEMENT,
            StarRocksElementTypes.SQL_CREATE_MATERIALIZED_VIEW_STATEMENT,
            StarRocksElementTypes.SQL_USE_SCHEMA_STATEMENT,
            SqlCompositeElementTypes.SQL_USE_CATALOG_STATEMENT,
            SqlCompositeElementTypes.SQL_USE_NAMESPACE_STATEMENT,
            SqlCompositeElementTypes.SQL_INDEX_DEFINITION,
            SqlCompositeElementTypes.SQL_PARTITION_DEFINITION,
            StarRocksElementTypes.SQL_CALL_STATEMENT
        )
    }
}
