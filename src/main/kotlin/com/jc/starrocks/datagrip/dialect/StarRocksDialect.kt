package com.jc.starrocks.datagrip.dialect

import com.jc.starrocks.datagrip.StarRocksIcons
import com.jc.starrocks.datagrip.database.StarRocksDataType
import com.jc.starrocks.datagrip.database.StarRocksDbms
import com.jc.starrocks.datagrip.lang.StarRocksElementTypes
import com.jc.starrocks.datagrip.lang.StarRocksTokens
import com.intellij.database.Dbms
import com.intellij.database.model.ObjectKind
import com.intellij.database.model.ObjectName
import com.intellij.database.psi.DbDataSource
import com.intellij.database.util.TreePattern
import com.intellij.database.util.TreePatternNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveState
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.BuiltinFunction
import com.intellij.sql.dialects.base.SqlLanguageDialectBase
import com.intellij.sql.dialects.base.TokensHelper
import com.intellij.sql.dialects.functions.SqlFunctionsUtil
import com.intellij.sql.dialects.SqlDialectImplUtilCore
import com.intellij.sql.psi.SqlGroupByClause
import com.intellij.sql.psi.SqlHavingClause
import com.intellij.sql.psi.SqlOrderByClause
import com.intellij.sql.psi.SqlQualifyClause
import com.intellij.sql.psi.SqlQueryExpression
import com.intellij.sql.psi.SqlScopeProcessor
import com.intellij.psi.tree.IElementType
import javax.swing.Icon

class StarRocksDialect private constructor() : SqlLanguageDialectBase("StarRocks") {
    override fun getDbms(): Dbms = StarRocksDbms.INSTANCE
    override fun getIcon(): Icon = StarRocksIcons.Dialect
    override fun createTokensHelper(): TokensHelper = TokensHelper(
        StarRocksTokens::class.java,
        SqlFunctionsUtil.loadFunctionDefinition(this)
    )

    override fun addTypes(types: MutableMap<String, BuiltinFunction.Type>) {
        super.addTypes(types)
        StarRocksDataType.entries.forEach { type ->
            SqlFunctionsUtil.addSimpleType(types, type.sqlName, type.sqlName, this)
        }
    }
    override fun isOperatorSupported(token: IElementType?): Boolean = true
    override fun getSystemVariables(): Set<String> = emptySet()

    override fun canContainDdl(element: PsiElement): Boolean {
        return element.node.elementType in DDL_CONTAINER_TYPES || super.canContainDdl(element)
    }

    override fun <T : MutableCollection<ObjectKind>> getParentDbTypes(result: T, type: ObjectKind): T {
        super.getParentDbTypes(result, type)
        val parentType = when (type) {
            ObjectKind.MAT_VIEW, ObjectKind.VIEW -> ObjectKind.SCHEMA
            ObjectKind.SCHEMA -> ObjectKind.DATABASE
            else -> null
        }
        if (parentType != null && parentType !in result) {
            result.add(parentType)
        }
        return result
    }

    override fun getBaseImports(dataSource: DbDataSource?, path: Array<out ObjectName?>?): TreePattern {
        if (dataSource == null || path == null) {
            return super.getBaseImports(dataSource, path)
        }
        val defaultNamespace = getDefaultNamespace(dataSource, null)
        return if (defaultNamespace != null) {
            SqlDialectImplUtilCore.createObjectPattern(
                path,
                defaultNamespace,
                *emptyArray<TreePatternNode.Group>()
            )
        } else {
            getSchemaBaseImports(dataSource, path, false)
        }
    }

    override fun processUnqualifiedResolve(
        processor: SqlScopeProcessor,
        state: ResolveState,
        reference: PsiReference
    ): Boolean {
        if (PsiTreeUtil.getParentOfType(reference.element, SqlQueryExpression::class.java) == null) {
            return super.processUnqualifiedResolve(processor, state, reference)
        }
        val resolveAliases = SELECT_ALIAS_CLAUSES.any { clause ->
            PsiTreeUtil.getParentOfType(reference.element, clause, false, SqlQueryExpression::class.java) != null
        }
        return (!resolveAliases || processAliases(processor, state, reference)) &&
            super.processUnqualifiedResolve(processor, state, reference)
    }

    companion object {
        private val DDL_CONTAINER_TYPES = setOf(
            StarRocksElementTypes.STATEMENT,
            StarRocksElementTypes.DDL_STATEMENT,
            StarRocksElementTypes.CREATE_STATEMENT,
            StarRocksElementTypes.ALTER_STATEMENT,
            StarRocksElementTypes.DROP_STATEMENT
        )
        private val SELECT_ALIAS_CLAUSES = listOf(
            SqlGroupByClause::class.java,
            SqlHavingClause::class.java,
            SqlQualifyClause::class.java,
            SqlOrderByClause::class.java
        )

        @JvmField
        val INSTANCE: StarRocksDialect = StarRocksDialect()
    }
}
