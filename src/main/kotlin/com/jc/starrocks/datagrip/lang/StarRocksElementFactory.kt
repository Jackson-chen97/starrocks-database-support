package com.jc.starrocks.datagrip.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.tree.IElementType
import com.intellij.sql.dialects.base.SqlElementFactory
import com.intellij.sql.psi.SqlCompositeElementTypes
import com.intellij.sql.psi.SqlTokenType
import com.intellij.sql.psi.impl.SqlAlterStatementImpl
import com.intellij.sql.psi.impl.SqlAlterTableStatementImpl
import com.intellij.sql.psi.impl.SqlCommitStatementImpl
import com.intellij.sql.psi.impl.SqlCompositeElementImpl
import com.intellij.sql.psi.impl.SqlColumnDefinitionImpl
import com.intellij.sql.psi.impl.SqlColumnAliasListImpl
import com.intellij.sql.psi.impl.SqlCreateCatalogStatementImpl
import com.intellij.sql.psi.impl.SqlCreateIndexStatementImpl
import com.intellij.sql.psi.impl.SqlCreateSchemaStatementImpl
import com.intellij.sql.psi.impl.SqlCreateTableStatementImpl
import com.intellij.sql.psi.impl.SqlCreateViewStatementImpl
import com.intellij.sql.psi.impl.SqlDeleteStatementImpl
import com.intellij.sql.psi.impl.SqlExplainStatementImpl
import com.intellij.sql.psi.impl.SqlExpressionListImpl
import com.intellij.sql.psi.impl.SqlFromClauseImpl
import com.intellij.sql.psi.impl.SqlFunctionCallExpressionImpl
import com.intellij.sql.psi.impl.SqlFunctionCallTableExpressionImpl
import com.intellij.sql.psi.impl.SqlGrantStatementImpl
import com.intellij.sql.psi.impl.SqlInsertStatementImpl
import com.intellij.sql.psi.impl.SqlIndexDefinitionImpl
import com.intellij.sql.psi.impl.SqlJoinConditionClauseImpl
import com.intellij.sql.psi.impl.SqlJoinExpressionImpl
import com.intellij.sql.psi.impl.SqlLiteralExpressionImpl
import com.intellij.sql.psi.impl.SqlMergeStatementImpl
import com.intellij.sql.psi.impl.SqlNumericLiteralExpressionImpl
import com.intellij.sql.psi.impl.SqlRevokeStatementImpl
import com.intellij.sql.psi.impl.SqlRollbackStatementImpl
import com.intellij.sql.psi.impl.SqlSetStatementImpl
import com.intellij.sql.psi.impl.SqlSpecialLiteralExpressionImpl
import com.intellij.sql.psi.impl.SqlStatementImpl
import com.intellij.sql.psi.impl.SqlStringLiteralExpressionImpl
import com.intellij.sql.psi.impl.SqlTableExpressionImpl
import com.intellij.sql.psi.impl.SqlTableElementListImpl
import com.intellij.sql.psi.impl.SqlTruncateTableStatementImpl
import com.intellij.sql.psi.impl.SqlUpdateStatementImpl
import com.intellij.sql.psi.impl.SqlUseDatabaseStatementImpl
import com.intellij.sql.psi.impl.SqlUsingClauseImpl
import com.intellij.sql.util.SqlTokenRegistry

class StarRocksElementFactory : SqlElementFactory(), StarRocksTokens {
    override fun getStaticInfo(): Info = INFO

    override fun createElementNode(type: IElementType): CompositeElement {
        if (type == SqlCompositeElementTypes.SQL_TABLE_ELEMENT_LIST) {
            return SqlTableElementListImpl(type, null)
        }
        if (type == StarRocksElementTypes.STARROCKS_COLUMN_ALIAS_DEFINITION) {
            return StarRocksColumnAliasDefinition(type)
        }
        if (type == SqlCompositeElementTypes.SQL_TABLE_PROCEDURE_CALL_EXPRESSION) {
            return SqlFunctionCallTableExpressionImpl(type)
        }
        if (type in StarRocksPlatformElementSets.registeredStarRocksTypes) {
            return super.createElementNode(type) ?: CompositeElement(type)
        }
        if (type in StarRocksPlatformElementSets.registeredPlatformTypes) {
            return CompositeElement(type)
        }
        if (type in StarRocksPlatformElementSets.genericStatementTypes) {
            return SqlStatementImpl(type)
        }
        if (type in StarRocksPlatformElementSets.statementTypes) {
            return SqlStatementImpl(type)
        }
        if (type is StarRocksElementType) {
            return SqlCompositeElementImpl(type)
        }
        // getDefaultRegistrations(INFO) is the complete platform SQL PSI baseline.
        // Only genuinely StarRocks-specific nodes fall back to a generic composite.
        return super.createElementNode(type) ?: SqlCompositeElementImpl(type)
    }

    override fun createCompositeElement(node: ASTNode): PsiElement {
        if (node.elementType in StarRocksPlatformElementSets.registeredStarRocksTypes) {
            return (node as? PsiElement) ?: super.createCompositeElement(node)
        }
        if (node.elementType is StarRocksElementType) {
            return (node as? PsiElement) ?: ASTWrapperPsiElement(node)
        }
        if (node.elementType in StarRocksPlatformElementSets.registeredPlatformTypes) {
            return super.createCompositeElement(node)
        }
        if (node.elementType in StarRocksPlatformElementSets.genericStatementTypes) {
            return (node as? PsiElement) ?: ASTWrapperPsiElement(node)
        }
        if (node.elementType in StarRocksPlatformElementSets.statementTypes) {
            return (node as? PsiElement) ?: super.createCompositeElement(node)
        }
        return super.createCompositeElement(node)
    }

    companion object {
        init {
            StarRocksTokenInitializer.ensureInitialized()
        }

        @JvmStatic
        fun token(text: String): SqlTokenType = StarRocksElementTypeRegistry.token(text)

        @JvmStatic
        fun elementType(name: String): IElementType = StarRocksElementTypeRegistry.elementType(name)

        private val INFO: Info by lazy {
            Info().also { info ->
            getDefaultRegistrations(info)
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_CREATE_TABLE_STATEMENT,
                SqlCreateTableStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_COLUMN_DEFINITION,
                SqlColumnDefinitionImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_INDEX_DEFINITION,
                SqlIndexDefinitionImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_COLUMN_ALIAS_LIST,
                SqlColumnAliasListImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_TABLE_PROCEDURE_CALL_EXPRESSION,
                SqlFunctionCallTableExpressionImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_FUNCTION_CALL,
                SqlFunctionCallExpressionImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_EXPRESSION_LIST,
                SqlExpressionListImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_ARRAY_LITERAL,
                SqlLiteralExpressionImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_NUMERIC_LITERAL,
                SqlNumericLiteralExpressionImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_STRING_LITERAL,
                SqlStringLiteralExpressionImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_SPECIAL_LITERAL,
                SqlSpecialLiteralExpressionImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_INSERT_STATEMENT,
                SqlInsertStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_UPDATE_STATEMENT,
                SqlUpdateStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_DELETE_STATEMENT,
                SqlDeleteStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_MERGE_STATEMENT,
                SqlMergeStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_CREATE_VIEW_STATEMENT,
                SqlCreateViewStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_CREATE_MATERIALIZED_VIEW_STATEMENT,
                SqlCreateViewStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_CREATE_CATALOG_STATEMENT,
                SqlCreateCatalogStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_CREATE_SCHEMA_STATEMENT,
                SqlCreateSchemaStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_CREATE_INDEX_STATEMENT,
                SqlCreateIndexStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_ALTER_TABLE_STATEMENT,
                SqlAlterTableStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_ALTER_SCHEMA_STATEMENT,
                SqlAlterStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_ALTER_VIEW_STATEMENT,
                SqlAlterStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_ALTER_CATALOG_STATEMENT,
                SqlAlterStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_USE_SCHEMA_STATEMENT,
                SqlUseDatabaseStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_USE_CATALOG_STATEMENT,
                SqlUseDatabaseStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_USE_NAMESPACE_STATEMENT,
                SqlUseDatabaseStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_SET_STATEMENT,
                SqlSetStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_EXPLAIN_STATEMENT,
                SqlExplainStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_GRANT_STATEMENT,
                SqlGrantStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_REVOKE_STATEMENT,
                SqlRevokeStatementImpl::class.java
            )
            registerImplementation(
                info,
                StarRocksElementTypes.GRANT_STATEMENT,
                SqlGrantStatementImpl::class.java
            )
            registerImplementation(
                info,
                StarRocksElementTypes.REVOKE_STATEMENT,
                SqlRevokeStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_COMMIT_STATEMENT,
                SqlCommitStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_ROLLBACK_STATEMENT,
                SqlRollbackStatementImpl::class.java
            )
            registerImplementation(
                info,
                SqlCompositeElementTypes.SQL_TRUNCATE_TABLE_STATEMENT,
                SqlTruncateTableStatementImpl::class.java
            )
            registerImplementation(
                info,
                StarRocksElementTypes.SQL_FROM_CLAUSE,
                SqlFromClauseImpl::class.java
            )
            registerImplementation(
                info,
                StarRocksElementTypes.SQL_TABLE_EXPRESSION,
                SqlTableExpressionImpl::class.java
            )
            registerImplementation(
                info,
                StarRocksElementTypes.SQL_PARENTHESIZED_JOIN_EXPRESSION,
                SqlTableExpressionImpl::class.java
            )
            registerImplementation(
                info,
                StarRocksElementTypes.SQL_JOIN_EXPRESSION,
                SqlJoinExpressionImpl::class.java
            )
            registerImplementation(
                info,
                StarRocksElementTypes.SQL_JOIN_CONDITION_CLAUSE,
                SqlJoinConditionClauseImpl::class.java
            )
            registerImplementation(
                info,
                StarRocksElementTypes.SQL_USING_CLAUSE,
                SqlUsingClauseImpl::class.java
            )
            }
        }
    }
}
