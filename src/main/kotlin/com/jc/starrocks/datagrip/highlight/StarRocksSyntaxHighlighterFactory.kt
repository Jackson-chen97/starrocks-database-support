package com.jc.starrocks.datagrip.highlight

import com.jc.starrocks.datagrip.dialect.StarRocksDialect
import com.intellij.sql.dialects.base.SqlSyntaxHighlighterFactory

/**
 * Use the platform SQL highlighter so semantic annotations can classify PSI
 * objects (functions, tables, columns, and types) instead of guessing from
 * neighboring characters in a dialect-specific lexer.
 */
class StarRocksSyntaxHighlighterFactory : SqlSyntaxHighlighterFactory.Base(StarRocksDialect.INSTANCE)
