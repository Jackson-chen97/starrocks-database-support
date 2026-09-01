package com.jc.starrocks.datagrip.resolve

import com.jc.starrocks.datagrip.lang.StarRocksStatementFamily
import com.jc.starrocks.datagrip.lang.StarRocksStatementWordsClassifier

data class StarRocksTextSpan(
    val startOffset: Int,
    val endOffset: Int
) {
    fun contains(offset: Int): Boolean = offset in startOffset until endOffset
}

data class StarRocksLocalColumn(
    val name: String,
    val normalizedName: String,
    val nameSpan: StarRocksTextSpan,
    val typeText: String?
)

data class StarRocksLocalTable(
    val name: String,
    val normalizedName: String,
    val nameSpan: StarRocksTextSpan,
    val columns: List<StarRocksLocalColumn>
) {
    fun column(name: String): StarRocksLocalColumn? {
        val normalized = StarRocksLocalSqlContextAnalyzer.normalizeIdentifier(name)
        return columns.firstOrNull { it.normalizedName == normalized }
    }
}

data class StarRocksLocalCte(
    val name: String,
    val normalizedName: String,
    val nameSpan: StarRocksTextSpan
)

data class StarRocksQueryTableReference(
    val name: String,
    val normalizedName: String,
    val nameSpan: StarRocksTextSpan,
    val alias: String?,
    val aliasSpan: StarRocksTextSpan?,
    val tableFunction: Boolean
)

data class StarRocksDmlTargetReference(
    val statementKind: String,
    val tableName: String,
    val normalizedTableName: String,
    val nameSpan: StarRocksTextSpan,
    val resolvedTable: StarRocksLocalTable?
) {
    val resolvedColumns: List<StarRocksLocalColumn>
        get() = resolvedTable?.columns.orEmpty()
}

data class StarRocksLocalSqlContext(
    val tables: List<StarRocksLocalTable>,
    val ctes: List<StarRocksLocalCte>,
    val queryTableReferences: List<StarRocksQueryTableReference>,
    val dmlTargets: List<StarRocksDmlTargetReference>
) {
    fun resolveTable(name: String, beforeOffset: Int = Int.MAX_VALUE): StarRocksLocalTable? {
        val normalized = StarRocksLocalSqlContextAnalyzer.normalizeIdentifier(name)
        return tables.lastOrNull { table ->
            table.nameSpan.startOffset < beforeOffset && StarRocksLocalSqlContextAnalyzer.namesMatch(table.normalizedName, normalized)
        }
    }
}

object StarRocksLocalSqlContextAnalyzer {
    fun analyze(sql: String): StarRocksLocalSqlContext {
        val tokens = StarRocksSqlTokenizer.tokenize(sql)
        val statements = splitStatements(tokens)
        val tables = statements.mapNotNull { parseCreateRelation(it) }
        val ctes = statements.flatMap { parseCtes(it) }
        val queryReferences = statements.flatMap { parseQueryTableReferences(it) }
        val dmlTargets = statements.flatMap { parseDmlTargetReferences(it, tables) }
        return StarRocksLocalSqlContext(
            tables = tables,
            ctes = ctes,
            queryTableReferences = queryReferences,
            dmlTargets = dmlTargets
        )
    }

    fun normalizeIdentifier(identifier: String): String {
        return identifier
            .split(".")
            .filter { it.isNotBlank() }
            .joinToString(".") { unquoteIdentifier(it).lowercase() }
    }

    fun namesMatch(declaredName: String, referenceName: String): Boolean {
        return declaredName == referenceName ||
            declaredName.substringAfterLast(".") == referenceName ||
            referenceName.substringAfterLast(".") == declaredName
    }

    private fun parseCreateRelation(tokens: List<SqlToken>): StarRocksLocalTable? {
        val words = words(tokens)
        if (words.firstOrNull() != "CREATE") {
            return null
        }
        val family = StarRocksStatementWordsClassifier.classify(words)
        if (family !in CREATE_RELATION_FAMILIES) {
            return null
        }
        val objectKeyword = when (family) {
            StarRocksStatementFamily.TABLE_DDL -> "TABLE"
            StarRocksStatementFamily.VIEW,
            StarRocksStatementFamily.MATERIALIZED_VIEW -> "VIEW"
            else -> return null
        }
        val objectKeywordIndex = tokens.indexOfFirstWord(objectKeyword)
        if (objectKeywordIndex < 0) {
            return null
        }
        var nameIndex = objectKeywordIndex + 1
        if (tokens.matchesWords(nameIndex, "IF", "NOT", "EXISTS")) {
            nameIndex += 3
        }
        val tableName = readQualifiedIdentifier(tokens, nameIndex) ?: return null
        val firstClauseIndex = tokens.indexOfFirst(tableName.endIndex) { it.uppercaseText in CREATE_RELATION_BODY_BOUNDARIES }
            .takeIf { it >= 0 }
            ?: tokens.size
        val firstClauseStartOffset = tokens.getOrNull(firstClauseIndex)?.startOffset ?: Int.MAX_VALUE
        val columnListStart = tokens.indexOfFirst(tableName.endIndex) { it.text == "(" && it.startOffset < firstClauseStartOffset }
        val columnListEnd = if (columnListStart >= 0) matchingTokenIndex(tokens, columnListStart, "(", ")") else -1
        val columns = if (columnListStart >= 0 && columnListEnd > columnListStart) {
            parseColumnDefinitions(tokens.subList(columnListStart + 1, columnListEnd))
        } else {
            emptyList()
        }
        return StarRocksLocalTable(
            name = tableName.text,
            normalizedName = tableName.normalizedText,
            nameSpan = tableName.span,
            columns = columns
        )
    }

    private fun parseColumnDefinitions(tokens: List<SqlToken>): List<StarRocksLocalColumn> {
        return splitTopLevelItems(tokens).mapNotNull { item ->
            val first = item.firstOrNull { it.isIdentifierLike() } ?: return@mapNotNull null
            if (first.uppercaseText in COLUMN_DEFINITION_SKIP_WORDS) {
                return@mapNotNull null
            }
            val typeTokens = item
                .dropWhile { it.startOffset <= first.startOffset }
                .takeWhile { it.uppercaseText !in COLUMN_ATTRIBUTE_STARTERS }
            StarRocksLocalColumn(
                name = unquoteIdentifier(first.text),
                normalizedName = normalizeIdentifier(first.text),
                nameSpan = first.span,
                typeText = typeTokens.joinToString(" ") { it.text }.takeIf { it.isNotBlank() }
            )
        }
    }

    private fun parseCtes(tokens: List<SqlToken>): List<StarRocksLocalCte> {
        if (words(tokens).firstOrNull() != "WITH") {
            return emptyList()
        }
        val result = mutableListOf<StarRocksLocalCte>()
        var index = tokens.indexOfFirstWord("WITH") + 1
        while (index in tokens.indices) {
            val cteName = readQualifiedIdentifier(tokens, index) ?: break
            result += StarRocksLocalCte(
                name = cteName.text,
                normalizedName = cteName.normalizedText,
                nameSpan = cteName.span
            )
            index = cteName.endIndex
            if (tokens.getOrNull(index)?.text == "(") {
                index = matchingTokenIndex(tokens, index, "(", ")").takeIf { it >= 0 }?.plus(1) ?: break
            }
            val asIndex = tokens.indexOfFirstWord("AS", index)
            if (asIndex < 0 || tokens.getOrNull(asIndex + 1)?.text != "(") {
                break
            }
            index = matchingTokenIndex(tokens, asIndex + 1, "(", ")").takeIf { it >= 0 }?.plus(1) ?: break
            if (tokens.getOrNull(index)?.text == ",") {
                index++
                continue
            }
            break
        }
        return result
    }

    private fun parseQueryTableReferences(tokens: List<SqlToken>): List<StarRocksQueryTableReference> {
        val result = mutableListOf<StarRocksQueryTableReference>()
        var inFromClause = false
        var expectTableFactor = false
        var index = 0
        var parenDepth = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token.text == "(") {
                parenDepth++
            } else if (token.text == ")" && parenDepth > 0) {
                parenDepth--
            }

            if (parenDepth == 0 && token.uppercaseText in QUERY_CLAUSE_END_WORDS) {
                inFromClause = false
                expectTableFactor = false
            }
            if (parenDepth == 0 && token.uppercaseText == "FROM") {
                inFromClause = true
                expectTableFactor = true
                index++
                continue
            }
            if (inFromClause && parenDepth == 0 && token.text == ",") {
                expectTableFactor = true
                index++
                continue
            }
            if (inFromClause && parenDepth == 0 && token.uppercaseText == "JOIN") {
                expectTableFactor = true
                index++
                continue
            }
            if (inFromClause && expectTableFactor && token.uppercaseText in TABLE_FACTOR_PREFIX_WORDS) {
                index++
                continue
            }
            if (inFromClause && expectTableFactor) {
                val parsed = parseTableFactor(tokens, index)
                if (parsed != null) {
                    result += parsed.reference
                    expectTableFactor = false
                    index = parsed.nextIndex
                    continue
                }
            }
            index++
        }
        return result
    }

    private fun parseTableFactor(tokens: List<SqlToken>, startIndex: Int): ParsedTableFactor? {
        if (tokens.getOrNull(startIndex)?.text == "(") {
            val closeIndex = matchingTokenIndex(tokens, startIndex, "(", ")")
            val alias = if (closeIndex >= 0) readAlias(tokens, closeIndex + 1) else null
            return ParsedTableFactor(
                reference = StarRocksQueryTableReference(
                    name = "<subquery>",
                    normalizedName = "<subquery>",
                    nameSpan = tokens[startIndex].span,
                    alias = alias?.text,
                    aliasSpan = alias?.span,
                    tableFunction = false
                ),
                nextIndex = alias?.endIndex ?: (closeIndex + 1).coerceAtLeast(startIndex + 1)
            )
        }

        val name = readQualifiedIdentifier(tokens, startIndex) ?: return null
        val functionCall = tokens.getOrNull(name.endIndex)?.text == "("
        val factorEndIndex = if (functionCall) {
            matchingTokenIndex(tokens, name.endIndex, "(", ")").takeIf { it >= 0 }?.plus(1) ?: name.endIndex
        } else {
            name.endIndex
        }
        val alias = readAlias(tokens, factorEndIndex)
        return ParsedTableFactor(
            reference = StarRocksQueryTableReference(
                name = name.text,
                normalizedName = name.normalizedText,
                nameSpan = if (functionCall) StarRocksTextSpan(name.span.startOffset, tokens[factorEndIndex - 1].endOffset) else name.span,
                alias = alias?.text,
                aliasSpan = alias?.span,
                tableFunction = functionCall
            ),
            nextIndex = alias?.endIndex ?: factorEndIndex
        )
    }

    private fun parseDmlTargetReferences(
        tokens: List<SqlToken>,
        tables: List<StarRocksLocalTable>
    ): List<StarRocksDmlTargetReference> {
        val words = words(tokens)
        val firstWord = words.firstOrNull() ?: return emptyList()
        if (StarRocksStatementWordsClassifier.classify(words) != StarRocksStatementFamily.DML) {
            return emptyList()
        }
        val targetIndex = when (firstWord) {
            "INSERT" -> insertTargetStart(tokens)
            "UPDATE" -> tokens.indexOfFirstWord("UPDATE").takeIf { it >= 0 }?.plus(1)
            "DELETE" -> tokens.indexOfFirstWord("FROM").takeIf { it >= 0 }?.plus(1)
            "MERGE" -> tokens.indexOfFirstWord("INTO").takeIf { it >= 0 }?.plus(1)
            else -> null
        } ?: return emptyList()
        val targetName = readQualifiedIdentifier(tokens, targetIndex) ?: return emptyList()
        val resolvedTable = tables.lastOrNull { table ->
            table.nameSpan.startOffset < targetName.span.startOffset && namesMatch(table.normalizedName, targetName.normalizedText)
        }
        return listOf(
            StarRocksDmlTargetReference(
                statementKind = firstWord,
                tableName = targetName.text,
                normalizedTableName = targetName.normalizedText,
                nameSpan = targetName.span,
                resolvedTable = resolvedTable
            )
        )
    }

    private fun insertTargetStart(tokens: List<SqlToken>): Int? {
        val insertIndex = tokens.indexOfFirstWord("INSERT")
        if (insertIndex < 0) {
            return null
        }
        val intoIndex = tokens.indexOfFirstWord("INTO", insertIndex + 1)
        if (intoIndex >= 0) {
            return intoIndex + 1
        }
        val overwriteIndex = tokens.indexOfFirstWord("OVERWRITE", insertIndex + 1)
        if (overwriteIndex >= 0) {
            return if (tokens.getOrNull(overwriteIndex + 1)?.uppercaseText == "TABLE") overwriteIndex + 2 else overwriteIndex + 1
        }
        return null
    }

    private fun readAlias(tokens: List<SqlToken>, index: Int): QualifiedIdentifier? {
        var aliasIndex = index
        if (tokens.getOrNull(aliasIndex)?.uppercaseText == "AS") {
            aliasIndex++
        }
        val token = tokens.getOrNull(aliasIndex) ?: return null
        if (!token.isIdentifierLike() || token.uppercaseText in RESERVED_ALIAS_BOUNDARIES) {
            return null
        }
        return QualifiedIdentifier(
            text = unquoteIdentifier(token.text),
            normalizedText = normalizeIdentifier(token.text),
            span = token.span,
            endIndex = aliasIndex + 1
        )
    }

    private fun readQualifiedIdentifier(tokens: List<SqlToken>, startIndex: Int): QualifiedIdentifier? {
        var index = startIndex
        val first = tokens.getOrNull(index) ?: return null
        if (!first.isIdentifierLike()) {
            return null
        }
        val parts = mutableListOf(first)
        index++
        while (tokens.getOrNull(index)?.text == "." && tokens.getOrNull(index + 1)?.isIdentifierLike() == true) {
            parts += tokens[index]
            parts += tokens[index + 1]
            index += 2
        }
        val text = parts.joinToString("") { it.text }
        return QualifiedIdentifier(
            text = text,
            normalizedText = normalizeIdentifier(text),
            span = StarRocksTextSpan(parts.first().startOffset, parts.last().endOffset),
            endIndex = index
        )
    }

    private fun splitStatements(tokens: List<SqlToken>): List<List<SqlToken>> {
        val result = mutableListOf<List<SqlToken>>()
        var startIndex = 0
        var parenDepth = 0
        tokens.forEachIndexed { index, token ->
            when (token.text) {
                "(" -> parenDepth++
                ")" -> if (parenDepth > 0) parenDepth--
                ";" -> if (parenDepth == 0) {
                    if (startIndex < index) {
                        result += tokens.subList(startIndex, index)
                    }
                    startIndex = index + 1
                }
            }
        }
        if (startIndex < tokens.size) {
            result += tokens.subList(startIndex, tokens.size)
        }
        return result.filter { it.isNotEmpty() }
    }

    private fun splitTopLevelItems(tokens: List<SqlToken>): List<List<SqlToken>> {
        val result = mutableListOf<List<SqlToken>>()
        var startIndex = 0
        var parenDepth = 0
        var angleDepth = 0
        tokens.forEachIndexed { index, token ->
            when (token.text) {
                "(" -> parenDepth++
                ")" -> if (parenDepth > 0) parenDepth--
                "<" -> angleDepth++
                ">" -> if (angleDepth > 0) angleDepth--
                "," -> if (parenDepth == 0 && angleDepth == 0) {
                    result += tokens.subList(startIndex, index)
                    startIndex = index + 1
                }
            }
        }
        if (startIndex < tokens.size) {
            result += tokens.subList(startIndex, tokens.size)
        }
        return result.map { item -> item.dropWhile { it.text == "," } }.filter { it.isNotEmpty() }
    }

    private fun matchingTokenIndex(tokens: List<SqlToken>, openIndex: Int, openText: String, closeText: String): Int {
        var depth = 0
        for (index in openIndex until tokens.size) {
            when (tokens[index].text) {
                openText -> depth++
                closeText -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return -1
    }

    private fun words(tokens: List<SqlToken>): List<String> = tokens
        .filter { it.kind == SqlTokenKind.WORD || it.kind == SqlTokenKind.QUOTED_IDENTIFIER }
        .map { it.uppercaseText }

    private fun List<SqlToken>.indexOfFirstWord(word: String, startIndex: Int = 0): Int {
        return indexOfFirst(startIndex) { it.uppercaseText == word }
    }

    private fun List<SqlToken>.indexOfToken(text: String, startIndex: Int = 0): Int {
        return indexOfFirst(startIndex) { it.text == text }
    }

    private fun List<SqlToken>.indexOfFirst(startIndex: Int, predicate: (SqlToken) -> Boolean): Int {
        for (index in startIndex until size) {
            if (predicate(this[index])) {
                return index
            }
        }
        return -1
    }

    private fun List<SqlToken>.matchesWords(startIndex: Int, vararg words: String): Boolean {
        return words.indices.all { offset -> getOrNull(startIndex + offset)?.uppercaseText == words[offset] }
    }

    private fun unquoteIdentifier(identifier: String): String {
        return if (identifier.length >= 2 && identifier.first() == '`' && identifier.last() == '`') {
            identifier.substring(1, identifier.length - 1).replace("``", "`")
        } else {
            identifier
        }
    }

    private val COLUMN_DEFINITION_SKIP_WORDS = setOf(
        "AGGREGATE",
        "DUPLICATE",
        "INDEX",
        "KEY",
        "PRIMARY",
        "UNIQUE"
    )

    private val COLUMN_ATTRIBUTE_STARTERS = setOf(
        "AGGREGATE",
        "AUTO_INCREMENT",
        "COMMENT",
        "DEFAULT",
        "KEY",
        "NOT",
        "NULL"
    )

    private val CREATE_RELATION_FAMILIES = setOf(
        StarRocksStatementFamily.TABLE_DDL,
        StarRocksStatementFamily.VIEW,
        StarRocksStatementFamily.MATERIALIZED_VIEW
    )

    private val CREATE_RELATION_BODY_BOUNDARIES = setOf(
        "AGGREGATE",
        "AS",
        "COMMENT",
        "DISTRIBUTED",
        "DUPLICATE",
        "ENGINE",
        "LIKE",
        "ORDER",
        "PARTITION",
        "PRIMARY",
        "PROPERTIES",
        "REFRESH",
        "UNIQUE"
    )

    private val QUERY_CLAUSE_END_WORDS = setOf(
        "WHERE",
        "GROUP",
        "HAVING",
        "INTERSECT",
        "QUALIFY",
        "ORDER",
        "LIMIT",
        "MINUS",
        "UNION",
        "EXCEPT",
        "WINDOW"
    )

    private val TABLE_FACTOR_PREFIX_WORDS = setOf(
        "LATERAL"
    )

    private val RESERVED_ALIAS_BOUNDARIES = setOf(
        "CROSS",
        "FULL",
        "GROUP",
        "HAVING",
        "INNER",
        "INTERSECT",
        "JOIN",
        "LEFT",
        "LIMIT",
        "MINUS",
        "ON",
        "ORDER",
        "QUALIFY",
        "RIGHT",
        "EXCEPT",
        "UNION",
        "WINDOW",
        "WHERE"
    )
}

private data class ParsedTableFactor(
    val reference: StarRocksQueryTableReference,
    val nextIndex: Int
)

private data class QualifiedIdentifier(
    val text: String,
    val normalizedText: String,
    val span: StarRocksTextSpan,
    val endIndex: Int
)

private data class SqlToken(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val kind: SqlTokenKind
) {
    val uppercaseText: String = text.uppercase()
    val span: StarRocksTextSpan = StarRocksTextSpan(startOffset, endOffset)

    fun isIdentifierLike(): Boolean = kind == SqlTokenKind.WORD || kind == SqlTokenKind.QUOTED_IDENTIFIER
}

private enum class SqlTokenKind {
    WORD,
    QUOTED_IDENTIFIER,
    STRING,
    NUMBER,
    SYMBOL
}

private object StarRocksSqlTokenizer {
    fun tokenize(sql: String): List<SqlToken> {
        val result = mutableListOf<SqlToken>()
        var index = 0
        while (index < sql.length) {
            val char = sql[index]
            when {
                char.isWhitespace() -> index++
                sql.startsWith("--", index) -> index = consumeLineComment(sql, index)
                sql.startsWith("/*", index) -> index = consumeBlockComment(sql, index)
                char == '\'' || char == '"' -> {
                    val end = consumeQuoted(sql, index, char)
                    result += SqlToken(sql.substring(index, end), index, end, SqlTokenKind.STRING)
                    index = end
                }
                char == '`' -> {
                    val end = consumeBacktickIdentifier(sql, index)
                    result += SqlToken(sql.substring(index, end), index, end, SqlTokenKind.QUOTED_IDENTIFIER)
                    index = end
                }
                char.isDigit() -> {
                    val end = consumeWhile(sql, index) { it.isDigit() || it == '.' }
                    result += SqlToken(sql.substring(index, end), index, end, SqlTokenKind.NUMBER)
                    index = end
                }
                isIdentifierStart(char) -> {
                    val end = consumeWhile(sql, index) { isIdentifierPart(it) }
                    result += SqlToken(sql.substring(index, end), index, end, SqlTokenKind.WORD)
                    index = end
                }
                else -> {
                    result += SqlToken(char.toString(), index, index + 1, SqlTokenKind.SYMBOL)
                    index++
                }
            }
        }
        return result
    }

    private fun consumeLineComment(sql: String, start: Int): Int {
        var index = start + 2
        while (index < sql.length && sql[index] != '\n' && sql[index] != '\r') {
            index++
        }
        return index
    }

    private fun consumeBlockComment(sql: String, start: Int): Int {
        var index = start + 2
        while (index + 1 < sql.length) {
            if (sql[index] == '*' && sql[index + 1] == '/') {
                return index + 2
            }
            index++
        }
        return sql.length
    }

    private fun consumeQuoted(sql: String, start: Int, quote: Char): Int {
        var index = start + 1
        while (index < sql.length) {
            val char = sql[index]
            index++
            if (char == quote) {
                if (index < sql.length && sql[index] == quote) {
                    index++
                    continue
                }
                break
            }
            if (char == '\\' && index < sql.length) {
                index++
            }
        }
        return index
    }

    private fun consumeBacktickIdentifier(sql: String, start: Int): Int {
        var index = start + 1
        while (index < sql.length) {
            val char = sql[index]
            index++
            if (char == '`') {
                if (index < sql.length && sql[index] == '`') {
                    index++
                    continue
                }
                break
            }
        }
        return index
    }

    private fun consumeWhile(sql: String, start: Int, predicate: (Char) -> Boolean): Int {
        var index = start
        while (index < sql.length && predicate(sql[index])) {
            index++
        }
        return index
    }

    private fun isIdentifierStart(char: Char): Boolean = char == '_' || char == '@' || char.isLetter()

    private fun isIdentifierPart(char: Char): Boolean = char == '_' || char == '@' || char == '$' || char.isLetterOrDigit()
}
