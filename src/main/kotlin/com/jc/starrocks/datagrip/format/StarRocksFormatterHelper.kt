package com.jc.starrocks.datagrip.format

import com.jc.starrocks.datagrip.lang.StarRocksElementTypes
import com.intellij.psi.tree.IElementType
import com.intellij.sql.formatter.SqlFormatterHelper
import com.intellij.sql.formatter.model.SqlBlock
import com.intellij.sql.formatter.model.NodeBlockFactoryRecord
import com.intellij.sql.formatter.model.SqlCreateTableBlock
import com.intellij.sql.formatter.model.SqlExplainPlanBlock
import com.intellij.sql.formatter.model.SqlFunctionCallBlock
import com.intellij.sql.formatter.model.SqlMergeBlock
import com.intellij.sql.formatter.model.SqlParenthesizedSubqueryBlock
import com.intellij.sql.formatter.model.SqlPostfixClauseBlock
import com.intellij.sql.formatter.model.SqlQueryBlock
import com.intellij.sql.formatter.model.SqlStatementBlock
import com.intellij.sql.formatter.model.SqlUnsortedBlock
import com.intellij.sql.formatter.model.matchTypeWhen
import com.intellij.sql.psi.SqlCompositeElementTypes
import kotlin.reflect.KFunction0

class StarRocksFormatterHelper : SqlFormatterHelper {
    override val complexBlockCreation: List<NodeBlockFactoryRecord> = listOf(
        NodeBlockFactoryRecord(
            matchTypeWhen(
                SqlCompositeElementTypes.SQL_COLUMN_ALIAS_LIST,
                "StarRocks view column alias list"
            ) { node ->
                generateSequence(node) { it.treeParent }.any { ancestor ->
                    ancestor.elementType == SqlCompositeElementTypes.SQL_CREATE_VIEW_STATEMENT ||
                        ancestor.elementType == SqlCompositeElementTypes.SQL_CREATE_MATERIALIZED_VIEW_STATEMENT
                }
            },
            ::StarRocksViewColumnAliasListBlock
        )
    )

    override val basicBlockCreation: Map<IElementType, KFunction0<out SqlBlock>> =
        mapOf(
            StarRocksElementTypes.SQL_SELECT_STATEMENT to ::SqlQueryBlock,
            StarRocksElementTypes.SQL_QUERY_EXPRESSION to ::SqlQueryBlock,
            SqlCompositeElementTypes.SQL_WITH_QUERY_EXPRESSION to ::SqlQueryBlock,
            StarRocksElementTypes.SQL_INSERT_STATEMENT to ::SqlQueryBlock,
            StarRocksElementTypes.SQL_UPDATE_STATEMENT to ::SqlQueryBlock,
            StarRocksElementTypes.SQL_DELETE_STATEMENT to ::SqlQueryBlock,
            StarRocksElementTypes.SQL_MERGE_STATEMENT to ::SqlMergeBlock,
            StarRocksElementTypes.SQL_EXPLAIN_STATEMENT to ::SqlExplainPlanBlock,
            StarRocksElementTypes.SQL_PARENTHESIZED_QUERY_EXPRESSION to ::SqlParenthesizedSubqueryBlock,
            StarRocksElementTypes.SQL_CREATE_TABLE_STATEMENT to ::SqlCreateTableBlock,
            StarRocksElementTypes.SQL_CREATE_CATALOG_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_CREATE_SCHEMA_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_CREATE_INDEX_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_ALTER_SCHEMA_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_ALTER_TABLE_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_ALTER_VIEW_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_ALTER_CATALOG_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_CALL_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_START_TRANSACTION_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_COMMIT_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_ROLLBACK_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_TRUNCATE_TABLE_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_CREATE_VIEW_STATEMENT to ::StarRocksViewBlock,
            StarRocksElementTypes.SQL_CREATE_MATERIALIZED_VIEW_STATEMENT to ::StarRocksViewBlock,
            StarRocksElementTypes.CREATE_USER_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.ALTER_USER_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.DROP_USER_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.CREATE_ROLE_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.ALTER_ROLE_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.DROP_ROLE_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SET_PASSWORD_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.GRANT_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.REVOKE_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SECURITY_PRINCIPAL to ::SqlUnsortedBlock,
            StarRocksElementTypes.PRIVILEGE_LIST to ::SqlUnsortedBlock,
            StarRocksElementTypes.PRIVILEGE_TARGET to ::SqlUnsortedBlock,
            StarRocksElementTypes.DROP_SCHEMA_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.DROP_INDEX_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.ANALYZE_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.DESCRIBE_STATEMENT to ::SqlStatementBlock,
            StarRocksElementTypes.SQL_SELECT_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_FROM_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_WHERE_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_GROUP_BY_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_HAVING_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_QUALIFY_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_WINDOW_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_ORDER_BY_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_LIMIT_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_AS_EXPRESSION to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_NAMED_QUERY_DEFINITION to ::SqlUnsortedBlock,
            SqlCompositeElementTypes.SQL_ANALYTIC_CLAUSE to ::SqlPostfixClauseBlock,
            StarRocksElementTypes.SQL_UPDATE_DML_INSTRUCTION to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_DELETE_DML_INSTRUCTION to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_SET_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_SET_ASSIGNMENT to ::SqlUnsortedBlock,
            StarRocksElementTypes.MERGE_USING_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.MERGE_ON_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.MERGE_WHEN_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_TABLE_EXPRESSION to ::SqlUnsortedBlock,
            SqlCompositeElementTypes.SQL_TABLE_REFERENCE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_JOIN_EXPRESSION to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_PARENTHESIZED_JOIN_EXPRESSION to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_JOIN_CONDITION_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_USING_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.ORDERING_ITEM to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_FUNCTION_CALL to ::SqlFunctionCallBlock,
            SqlCompositeElementTypes.SQL_TYPE_CAST_EXPRESSION to ::SqlFunctionCallBlock,
            StarRocksElementTypes.CAST_TYPE to ::SqlUnsortedBlock,
            StarRocksElementTypes.ENGINE_CLAUSE to ::StarRocksDdlClauseBlock,
            StarRocksElementTypes.KEY_MODEL_CLAUSE to ::StarRocksDdlClauseBlock,
            StarRocksElementTypes.COMMENT_CLAUSE to ::StarRocksDdlClauseBlock,
            StarRocksElementTypes.PARTITION_CLAUSE to ::StarRocksDdlClauseBlock,
            StarRocksElementTypes.PARTITION_EXPRESSION to ::SqlUnsortedBlock,
            StarRocksElementTypes.DISTRIBUTION_CLAUSE to ::StarRocksDdlClauseBlock,
            StarRocksElementTypes.DISTRIBUTION_EXPRESSION to ::SqlUnsortedBlock,
            StarRocksElementTypes.BUCKETS_CLAUSE to ::StarRocksDdlClauseBlock,
            StarRocksElementTypes.REFRESH_CLAUSE to ::StarRocksDdlClauseBlock,
            StarRocksElementTypes.PROPERTIES_CLAUSE to ::StarRocksDdlClauseBlock,
            StarRocksElementTypes.ROLLUP_CLAUSE to ::StarRocksDdlClauseBlock,
            StarRocksElementTypes.TABLE_ORDER_BY_CLAUSE to ::StarRocksDdlClauseBlock,
            StarRocksElementTypes.MATERIALIZED_VIEW_ORDER_BY_CLAUSE to ::StarRocksDdlClauseBlock,
            StarRocksElementTypes.PROPERTY_PAIR to ::SqlUnsortedBlock,
            StarRocksElementTypes.RESOURCE_REFERENCE to ::SqlUnsortedBlock,
            StarRocksElementTypes.ANALYZE_TARGET to ::SqlUnsortedBlock,
            StarRocksElementTypes.ANALYZE_HISTOGRAM_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.SQL_ON_TARGET_CLAUSE to ::SqlUnsortedBlock,
            StarRocksElementTypes.USE_TARGET to ::SqlUnsortedBlock
        )
}
