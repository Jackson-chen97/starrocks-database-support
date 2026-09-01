package com.jc.starrocks.datagrip.lang

import com.jc.starrocks.datagrip.dialect.StarRocksDialect
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.sql.dialects.SqlLanguageDialectEx
import com.intellij.sql.dialects.base.SqlGeneratedParserUtil
import com.intellij.sql.dialects.base.SqlParser
import com.intellij.sql.dialects.base.SqlParserUtil
import com.intellij.sql.injection.SqlSuggestedInjection
import com.intellij.sql.psi.SqlCompositeElementTypes
import com.intellij.sql.psi.SqlTokens.SQL_IDENT_DELIMITED
import com.intellij.sql.psi.SqlTokens.SQL_ASTERISK
import com.intellij.database.DatabaseBundle

class StarRocksParser : SqlParser(StarRocksDialect.INSTANCE) {
    override fun getLanguage(): SqlLanguageDialectEx = StarRocksDialect.INSTANCE

    override fun getCurrentSqlInjection(): SqlSuggestedInjection = STARROCKS_INJECTION

    override fun getExtendsTokenSets(): Array<TokenSet> = emptyArray()

    override fun parseExtraRoots(root: IElementType, builder: PsiBuilder, level: Int): Boolean {
        return StarRocksGeneratedParser.parse_root_(root, builder, level)
    }

    override fun parseSqlStatement(builder: PsiBuilder, level: Int): Boolean {
        return StarRocksGeneratedParser.statement(builder, level)
    }

    override fun parseQueryExpression(builder: PsiBuilder, level: Int): Boolean {
        return StarRocksGeneratedParser.top_query_expression(builder, level)
    }

    override fun parseDataTypeExt(builder: PsiBuilder): Boolean {
        return parseTableDataType(builder) || StarRocksGeneratedParser.type_element(builder, 0)
    }

    override fun parseDataType(builder: PsiBuilder, level: Int, ext: Boolean): Boolean {
        return StarRocksGeneratedParser.type_element(builder, level)
    }

    override fun parseCastDataType(builder: PsiBuilder, level: Int): Boolean {
        return StarRocksGeneratedParser.cast_type(builder, level)
    }

    override fun parseValueExpression(builder: PsiBuilder, level: Int, immediate: Boolean, strict: Boolean): Boolean {
        if (builder.tokenType == SQL_ASTERISK) {
            val marker = builder.mark()
            builder.advanceLexer()
            marker.done(SqlCompositeElementTypes.SQL_COLUMN_REFERENCE)
            return true
        }
        val parsed = StarRocksGeneratedParser.value_expression(builder, level)
        if (!parsed && !immediate) {
            builder.error(DatabaseBundle.message("parsing.error.expression.expected"))
        }
        return parsed
    }

    override fun parseEvaluableExpression(builder: PsiBuilder, level: Int): Boolean {
        return SqlGeneratedParserUtil.parseAndRemapToGenericReference(builder, level, StarRocksGeneratedParser::value_expression)
    }

    override fun parseForeignKeyRefList(builder: PsiBuilder, level: Int): Boolean {
        return StarRocksGeneratedParser.table_column_list(builder, level)
    }

    override fun allowNoopStringConcatenation(builder: PsiBuilder, strict: Boolean): Boolean {
        return (!strict && SqlParserUtil.nextTokenIs(builder, SQL_IDENT_DELIMITED)) ||
            super.allowNoopStringConcatenation(builder, strict)
    }

    private companion object {
        val STARROCKS_INJECTION = SqlSuggestedInjection("StarRocks")
    }
}
