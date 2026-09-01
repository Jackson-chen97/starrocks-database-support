package com.jc.starrocks.datagrip.format

import com.jc.starrocks.datagrip.lang.StarRocksElementTypes
import com.intellij.formatting.WrapType
import com.intellij.lang.ASTNode
import com.intellij.sql.formatter.model.BlockRole
import com.intellij.sql.formatter.model.FlowPattern
import com.intellij.sql.formatter.model.NONE_INDENT
import com.intellij.sql.formatter.model.NORMAL_INDENT
import com.intellij.sql.formatter.model.SingletonExpandPattern
import com.intellij.sql.formatter.model.SingletonPattern
import com.intellij.sql.formatter.model.SqlCommentBlock
import com.intellij.sql.formatter.model.SqlKeyword
import com.intellij.sql.formatter.model.SqlNameBlock
import com.intellij.sql.formatter.model.SqlNodeBlock
import com.intellij.sql.formatter.model.SqlPhraseBlock
import com.intellij.sql.formatter.model.SqlQueryBlock
import com.intellij.sql.formatter.model.SqlRangeBlock
import com.intellij.sql.formatter.model.SqlTableParenthesizedColumnsSection
import com.intellij.sql.formatter.model.StartStopPattern
import com.intellij.sql.formatter.model.TailPattern
import com.intellij.sql.formatter.model.UntilPattern
import com.intellij.sql.formatter.model.matchElementClass
import com.intellij.sql.formatter.model.matchEverything
import com.intellij.sql.formatter.model.matchType
import com.intellij.sql.psi.SqlCommonKeywords
import com.intellij.sql.psi.SqlCompositeElementTypes
import com.intellij.sql.psi.SqlExpression

/**
 * View formatting with StarRocks-aware clauses before AS.
 *
 * Ordinary views use the same StarRocks-aware block as materialized views because
 * StarRocks column comments are composite clauses that the platform block does
 * not classify as view options.
 */
class StarRocksViewBlock : SqlNodeBlock() {
    override fun flowPatterns(): List<FlowPattern> = PATTERNS

    override fun whetherToFlatten(node: ASTNode): Boolean =
        node.elementType == SqlCompositeElementTypes.SQL_AS_QUERY_CLAUSE

    override fun configureFormattingAttributes() {
        val sql = context.sql
        val clauseWrap = makeWrap(WrapType.ALWAYS, false)
        val firstContent = nestedBlocks.indexOfFirst { it !is SqlCommentBlock }
        var hasExpandingOptions = false

        if (firstContent > 0) {
            nestedBlocks.subList(0, firstContent).forEach { it.myIndent = NONE_INDENT }
        }

        nestedBlocks.drop(firstContent.coerceAtLeast(0)).forEach { block ->
            when (block.role) {
                BlockRole.HEAD -> block.myIndent = NONE_INDENT
                BlockRole.ALIAS1 -> {
                    block.myWrap = null
                    block.myIndent = NORMAL_INDENT
                }
                BlockRole.PREFIX -> {
                    block.myWrap = null
                    block.myIndent = NONE_INDENT
                    hasExpandingOptions = block.shape.expanding
                }
                BlockRole.AS -> {
                    block.myWrap = if (sql.VIEW_WRAP_AS || hasExpandingOptions) clauseWrap else null
                    block.myIndent = NONE_INDENT
                }
                BlockRole.BODY -> {
                    block.myWrap = if (sql.VIEW_WRAP_QUERY) clauseWrap else null
                    block.myIndent = if (sql.VIEW_INDENT_QUERY) NORMAL_INDENT else NONE_INDENT
                }
                BlockRole.SUFFIX -> {
                    block.myWrap = clauseWrap
                    block.myIndent = NONE_INDENT
                }
                else -> block.myWrap = null
            }
        }
    }

    override fun userRequiresExpand(): Boolean = true

    private companion object {
        val PATTERNS: List<FlowPattern> = listOf(
            StartStopPattern(
                0,
                1,
                matchType(SqlCommonKeywords.SQL_CREATE),
                matchType(SqlCommonKeywords.SQL_VIEW),
                BlockRole.HEAD,
                ::SqlPhraseBlock
            ),
            SingletonExpandPattern(
                1,
                2,
                matchType(
                    SqlCompositeElementTypes.SQL_VIEW_REFERENCE,
                    SqlCompositeElementTypes.SQL_MATERIALIZED_VIEW_REFERENCE
                ),
                BlockRole.ALIAS1,
                ::SqlNameBlock
            ),
            UntilPattern(
                2,
                null,
                matchType(SqlCommonKeywords.SQL_AS, SqlCommonKeywords.SQL_IS),
                false,
                BlockRole.PREFIX,
                ::StarRocksViewOptionsBlock
            ),
            SingletonPattern(
                null,
                3,
                matchType(SqlCommonKeywords.SQL_AS, SqlCommonKeywords.SQL_IS),
                BlockRole.AS,
                ::SqlKeyword
            ),
            SingletonPattern(
                3,
                4,
                matchElementClass(SqlExpression::class.java),
                BlockRole.BODY,
                ::SqlQueryBlock
            ),
            TailPattern(4, null, matchEverything(), BlockRole.SUFFIX, ::SqlPhraseBlock)
        )
    }
}

private class StarRocksViewOptionsBlock : SqlRangeBlock() {
    override fun determineRole(node: ASTNode): BlockRole = when {
        node.elementType == SqlCompositeElementTypes.SQL_COLUMN_ALIAS_LIST -> BlockRole.HEAD
        node.elementType == StarRocksElementTypes.COMMENT_CLAUSE -> BlockRole.ALIAS2
        node.elementType in CLAUSE_TYPES -> BlockRole.ELEMENT
        else -> super.determineRole(node)
    }

    override fun configureFormattingAttributes() {
        val clauseWrap = makeWrap(WrapType.ALWAYS, false)
        nestedBlocks.forEach { block ->
            when (block.role) {
                BlockRole.HEAD -> {
                    block.myWrap = null
                    block.myIndent = NONE_INDENT
                }
                BlockRole.ELEMENT -> {
                    block.myWrap = clauseWrap
                    block.myIndent = NORMAL_INDENT
                }
                BlockRole.ALIAS2 -> {
                    block.myWrap = makeWrap(WrapType.CHOP_DOWN_IF_LONG, false)
                    block.myIndent = NORMAL_INDENT
                }
                else -> {
                    block.myWrap = null
                    block.myIndent = com.intellij.sql.formatter.model.CONTINUATION_INDENT
                }
            }
        }
    }

    private companion object {
        val CLAUSE_TYPES = setOf(
            StarRocksElementTypes.PARTITION_CLAUSE,
            StarRocksElementTypes.DISTRIBUTION_CLAUSE,
            StarRocksElementTypes.BUCKETS_CLAUSE,
            StarRocksElementTypes.MATERIALIZED_VIEW_ORDER_BY_CLAUSE,
            StarRocksElementTypes.REFRESH_CLAUSE,
            StarRocksElementTypes.PROPERTIES_CLAUSE
        )
    }
}

/**
 * View column lists follow the platform table-column layout, while table alias
 * lists retain the platform cortege behavior used by FROM aliases.
 */
class StarRocksViewColumnAliasListBlock : SqlTableParenthesizedColumnsSection() {
    override fun determineRole(node: ASTNode): BlockRole =
        if (node.elementType == StarRocksElementTypes.STARROCKS_COLUMN_ALIAS_DEFINITION) {
            BlockRole.ELEMENT
        } else {
            super.determineRole(node)
        }
}
