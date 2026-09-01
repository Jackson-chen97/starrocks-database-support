package com.jc.starrocks.datagrip

import com.jc.starrocks.datagrip.completion.StarRocksCompletionCatalog
import com.jc.starrocks.datagrip.dialect.StarRocksDialect
import com.jc.starrocks.datagrip.database.StarRocksDefinitionProvider
import com.jc.starrocks.datagrip.database.StarRocksDdlStatements
import com.jc.starrocks.datagrip.database.StarRocksTypeSystem
import com.jc.starrocks.datagrip.format.StarRocksFormatterHelper
import com.jc.starrocks.datagrip.format.StarRocksFormattingProfile
import com.jc.starrocks.datagrip.format.StarRocksViewBlock
import com.jc.starrocks.datagrip.highlight.StarRocksSyntaxHighlighterFactory
import com.jc.starrocks.datagrip.lang.StarRocksFeature
import com.jc.starrocks.datagrip.lang.StarRocksElementFactory
import com.jc.starrocks.datagrip.lang.StarRocksElementTypes
import com.jc.starrocks.datagrip.lang.StarRocksGeneratedParser
import com.jc.starrocks.datagrip.lang.StarRocksGrammarMilestone
import com.jc.starrocks.datagrip.lang.StarRocksParserLexer
import com.jc.starrocks.datagrip.lang.StarRocksParser
import com.jc.starrocks.datagrip.lang.StarRocksStatementElementSets
import com.jc.starrocks.datagrip.lang.StarRocksTokens
import com.jc.starrocks.datagrip.resolve.StarRocksLocalSqlContextAnalyzer
import com.intellij.database.model.ObjectKind
import com.intellij.database.types.DasTypeCategory
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import com.intellij.sql.editor.SqlCodeBlockProviderUtils
import com.intellij.sql.dialects.base.TokenClasses
import com.intellij.sql.psi.SqlCompositeElementTypes
import com.intellij.sql.psi.SqlExpression
import com.intellij.sql.psi.SqlFromClause
import com.intellij.sql.psi.SqlJoinConditionClause
import com.intellij.sql.psi.SqlJoinExpression
import com.intellij.sql.psi.SqlStatement
import com.intellij.sql.psi.SqlTableExpression
import com.intellij.sql.psi.SqlUnionExpression
import com.intellij.sql.psi.SqlTokens.SQL_COMMA
import com.intellij.sql.psi.SqlTokens.SQL_LEFT_PAREN
import com.intellij.sql.psi.SqlTokens.SQL_PERIOD
import com.intellij.sql.psi.SqlTokens.SQL_RIGHT_PAREN
import com.intellij.sql.psi.SqlTokens.SQL_SEMICOLON
import com.intellij.sql.psi.SqlUsingClause
import com.intellij.sql.util.SqlTokenRegistry
import com.intellij.ui.scale.JBUIScale
import java.io.File

object StarRocksScenarioValidator {
    @JvmStatic
    fun main(args: Array<String>) {
        JBUIScale.setSystemScaleFactor(1.0f)
        JBUIScale.setUserScaleFactorForTest(1.0f)
        val projectDir = args.firstOrNull()?.let(::File) ?: File(".")
        val testDataDir = projectDir.resolve("src/testData/sql")
        val manifest = loadManifest(testDataDir.resolve("scenarios.properties"))

        manifest.forEach { (fileName, _) ->
            val sql = testDataDir.resolve(fileName).readText()
            val statements = splitStatements(sql)
            check(statements.isNotEmpty()) { "$fileName does not contain SQL statements." }
        }

        validateCoreQuery(testDataDir.resolve("query/core-query.sql").readText())
        validateSetOperationAndWindowQuery(testDataDir.resolve("query/set-window-query.sql").readText())
        validateCreateTable(testDataDir.resolve("ddl/create-table.sql").readText())
        validateMaterializedView(testDataDir.resolve("ddl/materialized-view.sql").readText())
        validateCreateView(testDataDir.resolve("ddl/view.sql").readText())
        validateLocalDdlReference(testDataDir.resolve("dml/insert-local-ddl.sql").readText())
        validateComplexTypes(testDataDir.resolve("types/complex-types.sql").readText())
        validateSupplementaryCompletionCatalog()
        validateLexerKeywordTokens()
        validateSyntaxHighlighterArchitecture()
        validateDialectTokens()
        validateGeneratedGrammarSkeleton(projectDir)
        validateFormattingProfile()
        validateElementFactory()
        validateEditorArchitecture(projectDir)
        validateDatabaseIntegration(projectDir)
        validateLegacyTextAnalyzersAreTestOnly(projectDir)
        validateDmlMutationFixtureCoverage(testDataDir.resolve("dml/mutations.sql").readText())
        validateSecurityAndTransactionFixtureCoverage(testDataDir.resolve("admin/security-transaction.sql").readText())
        validateSchemaAndIndexFixtureCoverage(testDataDir.resolve("ddl/schema-index.sql").readText())
    }

    private fun loadManifest(file: File): Map<String, ScenarioMetadata> {
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val parts = line.split("=", limit = 2)
                check(parts.size == 2) { "Invalid scenario manifest line: $line" }
                val metadata = parts[1].split("|", limit = 2)
                check(metadata.size == 2) { "Invalid scenario metadata for ${parts[0]}" }
                parts[0].trim() to ScenarioMetadata(
                    milestone = StarRocksGrammarMilestone.valueOf(metadata[0].trim()),
                    features = metadata[1].split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .mapTo(mutableSetOf()) { StarRocksFeature.valueOf(it) }
                )
            }
    }

    private fun validateCoreQuery(sql: String) {
        check(sql.contains("QUALIFY", ignoreCase = true)) { "Core query fixture must cover QUALIFY." }
        check(sql.contains("CAST('20200101' AS BIGINT)", ignoreCase = true)) {
            "Core query fixture must cover CAST(... AS BIGINT)."
        }
        check(sql.contains("GROUPING SETS", ignoreCase = true)) {
            "Core query fixture must cover GROUPING SETS."
        }
        val context = StarRocksLocalSqlContextAnalyzer.analyze(sql)
        check(context.queryTableReferences.any { it.tableFunction && it.normalizedName == "unnest" }) {
            "Core query fixture must expose UNNEST as a table function reference."
        }
    }

    private fun validateSetOperationAndWindowQuery(sql: String) {
        check(sql.contains("UNION ALL", ignoreCase = true)) {
            "Set/window query fixture must cover UNION ALL."
        }
        check(sql.contains("EXCEPT", ignoreCase = true)) {
            "Set/window query fixture must cover EXCEPT."
        }
        check(sql.contains("WINDOW recent_orders AS", ignoreCase = true)) {
            "Set/window query fixture must cover named WINDOW definitions."
        }
        val statements = splitStatements(sql)
        check(statements.size == 1) { "Set/window fixture should be a single compound query statement." }
        val context = StarRocksLocalSqlContextAnalyzer.analyze(sql)
        check(context.ctes.map { it.normalizedName }.containsAll(listOf("base", "archived"))) {
            "Set/window fixture CTE names were not captured."
        }
        val references = context.queryTableReferences.map { it.normalizedName }
        check(references.containsAll(listOf("base", "archived", "dws.refund_orders"))) {
            "Set/window fixture table references are incomplete: $references"
        }
    }

    private fun validateCreateTable(sql: String) {
        val context = StarRocksLocalSqlContextAnalyzer.analyze(sql)
        val table = context.resolveTable("dws.dws_trade_sale_by_order_ri")
        check(table != null) { "CREATE TABLE fixture did not declare the expected table." }
        check(table.columns.map { it.normalizedName }.containsAll(listOf("biz_date", "store_id", "order_id"))) {
            "CREATE TABLE fixture did not expose expected local columns."
        }
        val ctas = context.resolveTable("dws.dws_trade_sale_by_order_ctas")
        check(ctas != null) { "CREATE TABLE AS SELECT fixture did not declare the expected CTAS table." }
        check(ctas.columns.isEmpty()) { "CTAS table without explicit column list should not steal SELECT expressions as columns." }
        check(context.queryTableReferences.any { it.normalizedName == "dws.dws_trade_sale_by_order_ri" }) {
            "CTAS source table was not captured from AS SELECT."
        }
    }

    private fun validateMaterializedView(sql: String) {
        val statements = splitStatements(sql)
        check(statements.first().contains("CREATE MATERIALIZED VIEW", ignoreCase = true)) {
            "Materialized view fixture must start from a CREATE MATERIALIZED VIEW statement."
        }
        val context = StarRocksLocalSqlContextAnalyzer.analyze(sql)
        check(context.queryTableReferences.any { it.normalizedName == "dws.dws_trade_sale_by_order_ri" }) {
            "Materialized view AS SELECT source table was not captured."
        }
    }

    private fun validateCreateView(sql: String) {
        val statements = splitStatements(sql)
        check(statements.first().contains("CREATE VIEW", ignoreCase = true)) {
            "View fixture must start from a CREATE VIEW statement."
        }
        val context = StarRocksLocalSqlContextAnalyzer.analyze(sql)
        val view = context.resolveTable("dws.v_trade_sale_summary")
        check(view != null) { "CREATE VIEW fixture did not declare the expected local view." }
        check(view.columns.map { it.normalizedName }.containsAll(listOf("biz_date", "order_id", "total_amount"))) {
            "CREATE VIEW fixture did not expose expected view columns."
        }
        check(context.queryTableReferences.any { it.normalizedName == "dws.dws_trade_sale_by_order_ri" }) {
            "View AS SELECT source table was not captured."
        }
        check(context.queryTableReferences.any { it.normalizedName == "dws.v_trade_sale_summary" }) {
            "Query reference to the local view was not captured."
        }
    }

    private fun validateLocalDdlReference(sql: String) {
        val context = StarRocksLocalSqlContextAnalyzer.analyze(sql)
        val insert = context.dmlTargets.singleOrNull { it.statementKind == "INSERT" }
        check(insert != null) { "Expected one INSERT target in local DDL fixture." }
        check(insert.resolvedTable != null) { "INSERT target did not resolve to earlier local CREATE TABLE." }
        check(insert.resolvedColumns.map { it.normalizedName }.containsAll(listOf("biz_date", "store_id", "order_id", "sale_time"))) {
            "Resolved INSERT target columns are incomplete."
        }
    }

    private fun validateComplexTypes(sql: String) {
        val context = StarRocksLocalSqlContextAnalyzer.analyze(sql)
        val table = context.resolveTable("complex_type_sample")
        check(table != null) { "Complex type fixture did not declare complex_type_sample." }
        val columnsByName = table.columns.associateBy { it.normalizedName }
        check(columnsByName["tags"]?.typeText?.contains("ARRAY", ignoreCase = true) == true) {
            "ARRAY type was not captured for tags."
        }
        check(columnsByName["attributes"]?.typeText?.contains("MAP", ignoreCase = true) == true) {
            "MAP type was not captured for attributes."
        }
        check(columnsByName["profile"]?.typeText?.contains("STRUCT", ignoreCase = true) == true) {
            "STRUCT type was not captured for profile."
        }
    }

    private fun validateSupplementaryCompletionCatalog() {
        check(StarRocksCompletionCatalog.PROPERTIES.isNotEmpty() && StarRocksCompletionCatalog.SNIPPETS.isNotEmpty()) {
            "Supplementary completion should retain only StarRocks-specific properties and snippets."
        }
    }

    private fun validateLexerKeywordTokens() {
        listOf(
            "SELECT",
            "INSERT",
            "WITH",
            "SET",
            "ALL",
            "EXCEPT",
            "INTERSECT",
            "FROM",
            "WHERE",
            "AND",
            "LIMIT",
            "JOIN",
            "ORDER",
            "GROUP",
            "HAVING",
            "QUALIFY",
            "INTERVAL",
            "INTO",
            "ASC",
            "DESC",
            "WINDOW"
        ).forEach { keyword ->
            val keywordLexer = StarRocksParserLexer()
            keywordLexer.start(keyword, 0, keyword.length, 0)
            check(keywordLexer.tokenType == SqlTokenRegistry.getType(keyword)) {
                "StarRocks lexer should classify official keyword $keyword as its registered platform SQL token."
            }
        }

        listOf("select", "FrOm", "where", "limit").forEach { keyword ->
            val keywordLexer = StarRocksParserLexer()
            keywordLexer.start(keyword, 0, keyword.length, 0)
            check(keywordLexer.tokenType == SqlTokenRegistry.getType(keyword.uppercase())) {
                "StarRocks lexer should classify $keyword case-insensitively as its registered platform SQL token."
            }
        }

        val highlightedTokens = lexSignificantTokens("SELECT f(a.b, 1) FROM t WHERE a > 0 LIMIT 1;")
        listOf("SELECT", "FROM", "WHERE", "LIMIT").forEach { keyword ->
            check(SqlTokenRegistry.getType(keyword) in highlightedTokens) {
                "StarRocks lexer should emit registered platform keyword token $keyword for SQL highlighter."
            }
        }
        listOf(SQL_LEFT_PAREN, SQL_RIGHT_PAREN, SQL_PERIOD, SQL_COMMA, SQL_SEMICOLON).forEach { token ->
            check(token in highlightedTokens) {
                "StarRocks lexer should emit $token for SQL highlighter."
            }
        }

    }

    private fun validateSyntaxHighlighterArchitecture() {
        check(
            StarRocksSyntaxHighlighterFactory::class.java.superclass.name ==
                "com.intellij.sql.dialects.base.SqlSyntaxHighlighterFactory\$Base"
        ) {
            "StarRocks syntax highlighting must use the platform SqlSyntaxHighlighterFactory.Base."
        }
    }

    private fun lexSignificantTokens(sql: String): List<IElementType> {
        val lexer = StarRocksParserLexer()
        lexer.start(sql, 0, sql.length, 0)
        val tokens = mutableListOf<IElementType>()
        while (lexer.tokenType != null) {
            val token = lexer.tokenType
            if (token != null && token != WHITE_SPACE) {
                tokens += token
            }
            lexer.advance()
        }
        return tokens
    }

    private fun validateDialectTokens() {
        val classes = TokenClasses(StarRocksTokens::class.java)
        SqlTokenRegistry.initTypeMap(classes.all, null)
        SqlTokenRegistry.initTypeMap(classes.reserved, null)
        val keywords = SqlTokenRegistry.getTokens(classes.all)
        val reservedKeywords = SqlTokenRegistry.getTokens(classes.reserved)
        check("SELECT" in keywords && "QUALIFY" in keywords && "EXCEPT" in keywords && "INTERSECT" in keywords && "WINDOW" in keywords) {
            "StarRocks token class did not expose core StarRocks keywords."
        }
        check("SELECT" in reservedKeywords && "QUALIFY" in reservedKeywords && "EXCEPT" in reservedKeywords &&
            "INTERSECT" in reservedKeywords && "WINDOW" in reservedKeywords) {
            "StarRocks token class did not expose reserved StarRocks keywords."
        }
        check(SqlTokenRegistry.getTokenProvider(classes.reserved).`fun`(SqlTokenRegistry.getType("SELECT").toString()) != null) {
            "StarRocks reserved token provider should resolve SELECT."
        }
        check(StarRocksTokens::class.java.isAssignableFrom(StarRocksElementFactory::class.java)) {
            "StarRocks element factory must implement StarRocksTokens so keyword interfaces are initialized like mature SQL dialects."
        }
        check(SqlCodeBlockProviderUtils.STARTERS.contains(SqlTokenRegistry.getType("IF"))) {
            "Platform SQL block highlighter must load after StarRocks keyword token initialization."
        }
    }

    private fun validateGeneratedGrammarSkeleton(projectDir: File) {
        val builderClass = com.intellij.lang.PsiBuilder::class.java
        val levelClass = Int::class.javaPrimitiveType!!
        listOf(
            StarRocksGeneratedParser::class.java.getDeclaredMethod("script", builderClass, levelClass),
            StarRocksGeneratedParser::class.java.getDeclaredMethod("statement", builderClass, levelClass),
            StarRocksGeneratedParser::class.java.getDeclaredMethod("query_expression", builderClass, levelClass),
            StarRocksGeneratedParser::class.java.getDeclaredMethod("value_expression", builderClass, levelClass),
            StarRocksGeneratedParser::class.java.getDeclaredMethod("type_element", builderClass, levelClass),
            StarRocksGeneratedParser::class.java.getDeclaredMethod("cast_type", builderClass, levelClass),
            StarRocksGeneratedParser::class.java.getDeclaredMethod("table_column_list", builderClass, levelClass),
            StarRocksGeneratedParser::class.java.getDeclaredMethod("analytic_clause", builderClass, levelClass)
        ).forEach { method ->
            check(method.returnType == Boolean::class.javaPrimitiveType) {
                "Generated grammar entry method ${method.name} must return boolean like JetBrains generated parsers."
            }
        }
        val extendsSetsMethod = StarRocksParser::class.java.getDeclaredMethod("getExtendsTokenSets")
        extendsSetsMethod.isAccessible = true
        val extendsSets = extendsSetsMethod.invoke(StarRocksParser()) as Array<*>
        check(extendsSets.isEmpty()) {
            "StarRocksParser should expose no custom extends token sets for the Grammar-Kit generated parser."
        }
        val generatedRoot = projectDir
            .resolve("build/generated/src/main/java/com/jc/starrocks/datagrip/lang")
        val generatedParserSource = generatedRoot
            .resolve("StarRocksGeneratedParser.java")
            .readText()
        val generatedElementTypesSource = generatedRoot
            .resolve("StarRocksElementTypes.java")
            .readText()
        val generatedLexerSource = generatedRoot
            .resolve("_StarRocksParserLexer.java")
            .readText()
        val parserSource = projectDir
            .resolve("src/main/kotlin/com/jc/starrocks/datagrip/lang/StarRocksParser.kt")
            .readText()
        val parserDefinitionSource = projectDir
            .resolve("src/main/kotlin/com/jc/starrocks/datagrip/lang/StarRocksParserDefinition.kt")
            .readText()
        val parserLexerWrapperSource = projectDir
            .resolve("src/main/kotlin/com/jc/starrocks/datagrip/lang/StarRocksParserLexer.kt")
            .readText()
        check("return script(builder_, level_ + 1)" in generatedParserSource) {
            "StarRocksGeneratedParser root must be generated from the Grammar-Kit script rule."
        }
        check("StarRocksDmlParsing" !in generatedParserSource &&
            "StarRocksDdlParsing" !in generatedParserSource &&
            "StarRocksExpressionParsing" !in generatedParserSource &&
            "StarRocksOtherParsing" !in generatedParserSource) {
            "StarRocksGeneratedParser must not delegate to old hand-written parser objects."
        }
        check("StarRocksDmlParsing" !in parserSource &&
            "StarRocksDdlParsing" !in parserSource &&
            "StarRocksExpressionParsing" !in parserSource &&
            "StarRocksOtherParsing" !in parserSource) {
            "StarRocksParser must expose only DataGrip SQL adapter hooks and delegate grammar work to StarRocksGeneratedParser."
        }
        check("StarRocksParserLexer()" in parserDefinitionSource && "StarRocksLexer()" !in parserDefinitionSource) {
            "StarRocksParserDefinition must create the parser lexer facade, not the general lexer implementation."
        }
        check("_StarRocksParserLexer(null)" in parserLexerWrapperSource && "StarRocksLexer()" !in parserLexerWrapperSource) {
            "StarRocks parser lexer facade must wrap the generated JFlex lexer."
        }
        check("StarRocksLexer" !in generatedLexerSource) {
            "StarRocks parser lexer must be generated separately from the highlighting lexer."
        }
        check("StarRocksElementFactory.elementType" in generatedElementTypesSource &&
            "StarRocksElementFactory.token" in generatedElementTypesSource) {
            "Generated StarRocksElementTypes.java must route element and token creation through StarRocksElementFactory."
        }
        check(!Regex("""new\s+(IElementType|SqlTokens)""").containsMatchIn(generatedElementTypesSource)) {
            "Generated StarRocksElementTypes.java must not create a separate type universe."
        }
        check("recoverUntilStatementBoundary" !in generatedParserSource && "STATEMENT_SEGMENT" !in generatedParserSource) {
            "StarRocksGeneratedParser must not keep the old fallback statement-boundary scanner."
        }
        check("consumeBalancedTail" !in generatedParserSource && "while (!builder.eof())" !in generatedParserSource) {
            "StarRocksGeneratedParser must not use old broad PsiBuilder scanners."
        }
        val mainSourceMentionsFallbackSegment = projectDir
            .resolve("src/main")
            .walkTopDown()
            .filter { it.isFile }
            .any { "STATEMENT_SEGMENT" in it.readText() || "STARROCKS_STATEMENT_SEGMENT" in it.readText() }
        check(!mainSourceMentionsFallbackSegment) {
            "StarRocks main sources must not define or reference fallback STATEMENT_SEGMENT nodes."
        }
        listOf(
            "StarRocksDdlParsing.kt",
            "StarRocksDmlParsing.kt",
            "StarRocksExpressionParsing.kt",
            "StarRocksOtherParsing.kt",
            "StarRocksGeneratedParser.kt",
            "StarRocksElementTypes.kt",
            "StarRocksParsingUtil.kt"
        ).forEach { fileName ->
            check(!projectDir.resolve("src/main/kotlin/com/jc/starrocks/datagrip/lang/$fileName").exists()) {
                "Old parser implementation or conflicting type holder must not live under src/main: $fileName"
            }
        }
        val oldGeneratedKotlinRoot = projectDir.resolve("generated/src/main/kotlin/com/jc/starrocks/datagrip/lang")
        listOf(
            "StarRocksGeneratedParser.kt",
            "StarRocksGeneratedDdlRules.kt",
            "StarRocksGeneratedDmlRules.kt",
            "StarRocksGeneratedExpressionRules.kt",
            "StarRocksGeneratedOtherRules.kt",
            "StarRocksElementTypes.kt"
        ).forEach { fileName ->
            check(!oldGeneratedKotlinRoot.resolve(fileName).exists()) {
                "Old generated Kotlin parser artifacts must not remain: $fileName"
            }
        }
        val flexSource = projectDir.resolve("grammar/starrocks.flex").readText()
        val bnfSource = projectDir.resolve("grammar/starrocks.bnf").readText()
        check("%class _StarRocksParserLexer" in flexSource && "StarRocksLexer" !in flexSource) {
            "JFlex parser lexer grammar must be the parser lexer source and must not wrap the highlighting lexer."
        }
        listOf(
            "script",
            "statement",
            "query_expression",
            "value_expression",
            "type_element",
            "cast_type",
            "table_column_list",
            "analytic_clause"
        ).forEach { rule ->
            check(Regex("""(?m)^(?:private\s+)?$rule\s*::=""").containsMatchIn(bnfSource)) {
                "Grammar-Kit grammar must define required entry rule $rule."
            }
        }
        check("pin=" in bnfSource && "recoverWhile=" in bnfSource) {
            "Grammar-Kit grammar must model pin and recoverWhile explicitly."
        }
        check("STATEMENT_SEGMENT" !in bnfSource && "statement_tail" !in bnfSource) {
            "Grammar-Kit grammar must not define fallback statement segments."
        }
        val buildSource = projectDir.resolve("build.gradle.kts").readText()
        check("generated/src/main/java" in buildSource && "generated/src/main/kotlin" !in buildSource) {
            "Build must include Grammar-Kit/JFlex generated Java sources without the old generated Kotlin source root."
        }
        check("GenerateLexerTask" in buildSource &&
            "GenerateParserTask" in buildSource &&
            "register(\"validateGrammarSources\")" in buildSource) {
            "Build must expose the StarRocks grammar generation and validation chain."
        }
    }
    private fun validateFormattingProfile() {
        check(StarRocksFormattingProfile.USE_PLATFORM_SQL_FORMATTER) {
            "StarRocks formatter must stay wired to the platform SQL formatter."
        }
        check(!StarRocksFormattingProfile.USE_GENERIC_SQL_FORMATTER_BRIDGE) {
            "StarRocks formatter must use StarRocks PSI directly instead of reparsing through GenericSQL."
        }
        check(!StarRocksFormattingProfile.USE_SAFE_DDL_FORMATTER) {
            "StarRocks formatter must not use DDL no-op safe formatting once DDL PSI has generated formatter blocks."
        }
        check(!StarRocksFormattingProfile.USE_WHOLE_FILE_STRING_REWRITE) {
            "StarRocks formatter must not rely on whole-file string rewrites."
        }
        check(StarRocksFormattingProfile.QUERY_CLAUSE_ORDER.indexOf("HAVING") <
            StarRocksFormattingProfile.QUERY_CLAUSE_ORDER.indexOf("QUALIFY")) {
            "QUALIFY must format after HAVING, matching StarRocks query grammar."
        }
        check(SqlCompositeElementTypes.SQL_QUALIFY_CLAUSE in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Generated StarRocks QUALIFY must use the platform SQL_QUALIFY_CLAUSE formatter block."
        }
        check(SqlCompositeElementTypes.SQL_FUNCTION_CALL in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Generated StarRocks UNNEST must use the platform SQL_FUNCTION_CALL formatter block."
        }
        listOf(
            SqlCompositeElementTypes.SQL_TABLE_ELEMENT_LIST,
            SqlCompositeElementTypes.SQL_COLUMN_DEFINITION,
            SqlCompositeElementTypes.SQL_COLUMN_GENERATED_CLAUSE,
            SqlCompositeElementTypes.SQL_INDEX_DEFINITION,
            SqlCompositeElementTypes.SQL_TYPE_ELEMENT,
            SqlCompositeElementTypes.SQL_TYPE_PARAMETER_LIST
        ).forEach { type ->
            check(type !in StarRocksFormatterHelper().basicBlockCreation.keys) {
                "StarRocks DDL element $type must use the platform default alignment block."
            }
        }
        check(StarRocksElementTypes.CAST_TYPE in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Generated StarRocks CAST types must have a formatter block."
        }
        check(SqlCompositeElementTypes.SQL_CREATE_TABLE_STATEMENT in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Generated StarRocks CREATE TABLE must use the platform SQL_CREATE_TABLE_STATEMENT formatter block."
        }
        check(SqlCompositeElementTypes.SQL_CREATE_VIEW_STATEMENT in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Generated StarRocks CREATE VIEW must use the platform SQL_CREATE_VIEW_STATEMENT formatter block."
        }
        check(SqlCompositeElementTypes.SQL_CREATE_MATERIALIZED_VIEW_STATEMENT in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Generated StarRocks materialized view must use the platform SQL_CREATE_MATERIALIZED_VIEW_STATEMENT formatter block."
        }
        check(
            StarRocksFormatterHelper().basicBlockCreation[SqlCompositeElementTypes.SQL_CREATE_VIEW_STATEMENT]
                ?.call() is StarRocksViewBlock
        ) {
            "StarRocks ordinary views must use the dialect-aware view formatter block."
        }
        check(
            StarRocksFormatterHelper().basicBlockCreation[SqlCompositeElementTypes.SQL_CREATE_MATERIALIZED_VIEW_STATEMENT]
                ?.call() is StarRocksViewBlock
        ) {
            "StarRocks materialized views must use the dialect-aware view formatter block."
        }
        check(SqlCompositeElementTypes.SQL_SELECT_STATEMENT in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Ordinary StarRocks SELECT must use the platform SQL_SELECT_STATEMENT formatter block."
        }
        check(SqlCompositeElementTypes.SQL_INSERT_STATEMENT in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Ordinary StarRocks INSERT must use the platform SQL_INSERT_STATEMENT formatter block."
        }
        listOf(
            SqlCompositeElementTypes.SQL_CALL_STATEMENT,
            SqlCompositeElementTypes.SQL_CREATE_CATALOG_STATEMENT,
            SqlCompositeElementTypes.SQL_CREATE_SCHEMA_STATEMENT,
            SqlCompositeElementTypes.SQL_CREATE_INDEX_STATEMENT,
            SqlCompositeElementTypes.SQL_ALTER_SCHEMA_STATEMENT,
            SqlCompositeElementTypes.SQL_ALTER_TABLE_STATEMENT,
            SqlCompositeElementTypes.SQL_ALTER_VIEW_STATEMENT,
            SqlCompositeElementTypes.SQL_ALTER_CATALOG_STATEMENT,
            SqlCompositeElementTypes.SQL_START_TRANSACTION_STATEMENT,
            SqlCompositeElementTypes.SQL_COMMIT_STATEMENT,
            SqlCompositeElementTypes.SQL_ROLLBACK_STATEMENT,
            SqlCompositeElementTypes.SQL_TRUNCATE_TABLE_STATEMENT,
            StarRocksElementTypes.CREATE_USER_STATEMENT,
            StarRocksElementTypes.ALTER_USER_STATEMENT,
            StarRocksElementTypes.DROP_USER_STATEMENT,
            StarRocksElementTypes.CREATE_ROLE_STATEMENT,
            StarRocksElementTypes.ALTER_ROLE_STATEMENT,
            StarRocksElementTypes.DROP_ROLE_STATEMENT,
            StarRocksElementTypes.SET_PASSWORD_STATEMENT,
            StarRocksElementTypes.GRANT_STATEMENT,
            StarRocksElementTypes.REVOKE_STATEMENT,
            StarRocksElementTypes.DROP_SCHEMA_STATEMENT,
            StarRocksElementTypes.DROP_INDEX_STATEMENT,
            StarRocksElementTypes.ANALYZE_STATEMENT,
            StarRocksElementTypes.DESCRIBE_STATEMENT
        ).forEach { type ->
            check(type in StarRocksFormatterHelper().basicBlockCreation.keys) {
                "StarRocks common statement $type must have a formatter block."
            }
        }
        check(StarRocksElementTypes.SQL_TABLE_EXPRESSION in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Generated StarRocks table expressions must use a platform formatter block."
        }
        check(StarRocksElementTypes.SQL_JOIN_EXPRESSION in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Generated StarRocks JOIN expressions must use a platform formatter block."
        }
        check(StarRocksElementTypes.SQL_JOIN_CONDITION_CLAUSE in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Generated StarRocks JOIN conditions must use a platform formatter block."
        }
        listOf(
            StarRocksElementTypes.SQL_UPDATE_DML_INSTRUCTION,
            StarRocksElementTypes.SQL_DELETE_DML_INSTRUCTION,
            StarRocksElementTypes.SQL_SET_CLAUSE,
            StarRocksElementTypes.SQL_SET_ASSIGNMENT,
            StarRocksElementTypes.MERGE_USING_CLAUSE,
            StarRocksElementTypes.MERGE_ON_CLAUSE,
            StarRocksElementTypes.MERGE_WHEN_CLAUSE
        ).forEach { type ->
            check(type in StarRocksFormatterHelper().basicBlockCreation.keys) {
                "Generated StarRocks DML clause $type must have a platform formatter block."
            }
        }
        listOf(
            StarRocksElementTypes.ENGINE_CLAUSE,
            StarRocksElementTypes.KEY_MODEL_CLAUSE,
            StarRocksElementTypes.COMMENT_CLAUSE,
            StarRocksElementTypes.PARTITION_CLAUSE,
            StarRocksElementTypes.DISTRIBUTION_CLAUSE,
            StarRocksElementTypes.BUCKETS_CLAUSE,
            StarRocksElementTypes.REFRESH_CLAUSE,
            StarRocksElementTypes.PROPERTIES_CLAUSE,
            StarRocksElementTypes.ROLLUP_CLAUSE,
            StarRocksElementTypes.TABLE_ORDER_BY_CLAUSE,
            StarRocksElementTypes.MATERIALIZED_VIEW_ORDER_BY_CLAUSE
        ).forEach { type ->
            check(type in StarRocksFormatterHelper().basicBlockCreation.keys) {
                "Generated StarRocks DDL clause $type must have a platform formatter block."
            }
        }
        listOf(
            StarRocksElementTypes.ANALYZE_TARGET,
            StarRocksElementTypes.ANALYZE_HISTOGRAM_CLAUSE
        ).forEach { type ->
            check(type in StarRocksFormatterHelper().basicBlockCreation.keys) {
                "Generated StarRocks ANALYZE clause $type must have a platform formatter block."
            }
        }
        check(StarRocksElementTypes.SQL_ON_TARGET_CLAUSE in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Platform-backed StarRocks target clauses must have a formatter block."
        }
        check(StarRocksElementTypes.USE_TARGET in StarRocksFormatterHelper().basicBlockCreation.keys) {
            "Generated StarRocks USE target must have a platform formatter block."
        }
        check("DISTRIBUTED BY" in StarRocksFormattingProfile.TABLE_DDL_CLAUSE_ORDER) {
            "DDL formatting profile must include StarRocks distribution clauses."
        }
    }

    private fun validateElementFactory() {
        val factory = StarRocksElementFactory()
        listOf(
            StarRocksElementTypes.SQL_SELECT_CLAUSE,
            StarRocksElementTypes.SQL_WITH_CLAUSE,
            StarRocksElementTypes.SQL_NAMED_QUERY_DEFINITION,
            StarRocksElementTypes.SQL_COLUMN_ALIAS_LIST,
            StarRocksElementTypes.SQL_PARENTHESIZED_QUERY_EXPRESSION,
            StarRocksElementTypes.SQL_WHERE_CLAUSE,
            StarRocksElementTypes.SQL_GROUP_BY_CLAUSE,
            StarRocksElementTypes.SQL_HAVING_CLAUSE,
            StarRocksElementTypes.SQL_ORDER_BY_CLAUSE,
            StarRocksElementTypes.ORDERING_ITEM,
            StarRocksElementTypes.SQL_LIMIT_CLAUSE,
            StarRocksElementTypes.LIMIT_EXPRESSION,
            StarRocksElementTypes.SQL_VALUES_EXPRESSION,
            StarRocksElementTypes.VALUES_ROW,
            StarRocksElementTypes.SQL_UPDATE_DML_INSTRUCTION,
            StarRocksElementTypes.SQL_DELETE_DML_INSTRUCTION,
            StarRocksElementTypes.SQL_SET_CLAUSE,
            StarRocksElementTypes.SQL_SET_ASSIGNMENT,
            StarRocksElementTypes.MERGE_USING_CLAUSE,
            StarRocksElementTypes.MERGE_ON_CLAUSE,
            StarRocksElementTypes.MERGE_WHEN_CLAUSE,
            StarRocksElementTypes.ANALYZE_TARGET,
            StarRocksElementTypes.ANALYZE_HISTOGRAM_CLAUSE,
            StarRocksElementTypes.SQL_ON_TARGET_CLAUSE,
            StarRocksElementTypes.USE_TARGET,
            StarRocksElementTypes.SQL_TABLE_EXPRESSION,
            StarRocksElementTypes.SQL_TABLE_PROCEDURE_CALL_EXPRESSION,
            SqlCompositeElementTypes.SQL_TABLE_REFERENCE,
            StarRocksElementTypes.SQL_UNION_EXPRESSION,
            StarRocksElementTypes.SQL_JOIN_EXPRESSION,
            StarRocksElementTypes.SQL_PARENTHESIZED_JOIN_EXPRESSION,
            StarRocksElementTypes.SQL_JOIN_CONDITION_CLAUSE,
            StarRocksElementTypes.SQL_USING_CLAUSE,
            StarRocksElementTypes.SQL_WINDOW_CLAUSE,
            StarRocksElementTypes.SQL_GENERIC_DEFINITION,
            StarRocksElementTypes.SQL_WINDOW_REFERENCE,
            StarRocksElementTypes.SQL_AS_EXPRESSION,
            StarRocksElementTypes.STARROCKS_COLUMN_ALIAS_DEFINITION,
            StarRocksElementTypes.SQL_IDENTIFIER,
            StarRocksElementTypes.SQL_COLUMN_DEFINITION,
            SqlCompositeElementTypes.SQL_COLUMN_GENERATED_CLAUSE,
            SqlCompositeElementTypes.SQL_INDEX_DEFINITION,
            SqlCompositeElementTypes.SQL_LIKE_TABLE_CLAUSE,
            SqlCompositeElementTypes.SQL_TYPE_PARAMETER_LIST,
            StarRocksElementTypes.ENGINE_CLAUSE,
            StarRocksElementTypes.KEY_MODEL_CLAUSE,
            StarRocksElementTypes.COMMENT_CLAUSE,
            StarRocksElementTypes.PARTITION_CLAUSE,
            StarRocksElementTypes.PARTITION_EXPRESSION,
            StarRocksElementTypes.DISTRIBUTION_CLAUSE,
            StarRocksElementTypes.DISTRIBUTION_EXPRESSION,
            StarRocksElementTypes.BUCKETS_CLAUSE,
            StarRocksElementTypes.REFRESH_CLAUSE,
            StarRocksElementTypes.MATERIALIZED_VIEW_ORDER_BY_CLAUSE,
            StarRocksElementTypes.RESOURCE_REFERENCE,
            StarRocksElementTypes.SECURITY_PRINCIPAL,
            StarRocksElementTypes.PRIVILEGE_LIST,
            StarRocksElementTypes.PRIVILEGE_TARGET
        ).forEach { type ->
            val node = factory.createElementNode(type)
            check(node.elementType == type) { "Element factory created the wrong node type for $type." }
        }
        listOf(
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
        ).forEach { type ->
            val node = factory.createElementNode(type)
            check(node is SqlStatement) {
                "StarRocks top-level statement node $type must implement SqlStatement so IDE run actions can find it."
            }
        }
        listOf(
            SqlCompositeElementTypes.SQL_USE_SCHEMA_STATEMENT,
            SqlCompositeElementTypes.SQL_USE_CATALOG_STATEMENT,
            SqlCompositeElementTypes.SQL_USE_NAMESPACE_STATEMENT,
            SqlCompositeElementTypes.SQL_INSERT_STATEMENT,
            SqlCompositeElementTypes.SQL_UPDATE_STATEMENT,
            SqlCompositeElementTypes.SQL_DELETE_STATEMENT,
            SqlCompositeElementTypes.SQL_MERGE_STATEMENT,
            SqlCompositeElementTypes.SQL_CREATE_VIEW_STATEMENT,
            SqlCompositeElementTypes.SQL_CREATE_CATALOG_STATEMENT,
            SqlCompositeElementTypes.SQL_CREATE_SCHEMA_STATEMENT,
            SqlCompositeElementTypes.SQL_CREATE_INDEX_STATEMENT,
            SqlCompositeElementTypes.SQL_ALTER_SCHEMA_STATEMENT,
            SqlCompositeElementTypes.SQL_ALTER_TABLE_STATEMENT,
            SqlCompositeElementTypes.SQL_ALTER_VIEW_STATEMENT,
            SqlCompositeElementTypes.SQL_ALTER_CATALOG_STATEMENT,
            SqlCompositeElementTypes.SQL_CALL_STATEMENT,
            SqlCompositeElementTypes.SQL_GRANT_STATEMENT,
            SqlCompositeElementTypes.SQL_REVOKE_STATEMENT,
            SqlCompositeElementTypes.SQL_START_TRANSACTION_STATEMENT,
            SqlCompositeElementTypes.SQL_COMMIT_STATEMENT,
            SqlCompositeElementTypes.SQL_ROLLBACK_STATEMENT,
            SqlCompositeElementTypes.SQL_TRUNCATE_TABLE_STATEMENT
        ).forEach { type ->
            val node = factory.createElementNode(type)
            val psi = factory.createCompositeElement(node)
            check(psi is SqlStatement) {
                "Platform statement node $type must stay runnable under the StarRocks element factory."
            }
        }
        listOf(
            StarRocksElementTypes.SQL_FROM_CLAUSE to SqlFromClause::class.java,
            StarRocksElementTypes.SQL_TABLE_EXPRESSION to SqlTableExpression::class.java,
            StarRocksElementTypes.SQL_PARENTHESIZED_JOIN_EXPRESSION to SqlTableExpression::class.java,
            StarRocksElementTypes.SQL_UNION_EXPRESSION to SqlUnionExpression::class.java,
            StarRocksElementTypes.SQL_JOIN_EXPRESSION to SqlJoinExpression::class.java,
            StarRocksElementTypes.SQL_JOIN_CONDITION_CLAUSE to SqlJoinConditionClause::class.java,
            StarRocksElementTypes.SQL_USING_CLAUSE to SqlUsingClause::class.java
        ).forEach { (type, psiClass) ->
            val node = factory.createElementNode(type)
            check(psiClass.isInstance(node)) {
                "StarRocks node $type must implement ${psiClass.simpleName}, matching mature SQL dialect PSI mappings."
            }
        }
        check(SqlCompositeElementTypes.SQL_SELECT_STATEMENT in StarRocksStatementElementSets.STATEMENT_TYPES) {
            "Statement scope detection must use platform SELECT statements, not legacy StarRocks query nodes."
        }
        check(SqlCompositeElementTypes.SQL_INSERT_STATEMENT in StarRocksStatementElementSets.STATEMENT_TYPES) {
            "Statement scope detection must use platform INSERT statements, not legacy StarRocks DML nodes."
        }
        val legacyStatementNames = setOf(
            "STARROCKS_QUERY_STATEMENT",
            "STARROCKS_DML_STATEMENT",
            "STARROCKS_UNKNOWN_STATEMENT"
        )
        check(StarRocksStatementElementSets.STATEMENT_TYPES.none { it.toString() in legacyStatementNames }) {
            "Statement scope detection must not depend on legacy lightweight parser statement nodes."
        }
        check(StarRocksDialect.INSTANCE.isOperatorSupported(null)) {
            "StarRocks dialect must tolerate the nullable operator token passed by the platform SqlParser."
        }
    }

    private fun validateDatabaseIntegration(projectDir: File) {
        val typeSystem = StarRocksTypeSystem()
        check(typeSystem.getNormalizedTypeName("integer") == "INT") {
            "StarRocks type system should normalize INTEGER to INT."
        }
        check(typeSystem.getNormalizedTypeName("decimal(10, 2)") == "DECIMAL128") {
            "StarRocks type system should normalize DECIMAL to DECIMAL128."
        }
        check(typeSystem.getNormalizedTypeName("array<bigint>") == "ARRAY") {
            "StarRocks type system should recognize ARRAY complex types."
        }
        check(typeSystem.getNormalizedTypeName("binary(128)") == "VARBINARY") {
            "StarRocks type system should normalize BINARY to VARBINARY."
        }
        check(typeSystem.getDefaultTypeName(DasTypeCategory.INTEGER) == "BIGINT") {
            "StarRocks integer default type should be BIGINT."
        }
        check(
            setOf("JSON", "VARIANT", "VARBINARY", "PERCENTILE").all(StarRocksTypeSystem.SCALAR_TYPES::contains) &&
                "STRUCT" in StarRocksTypeSystem.COMPLEX_TYPES
        ) {
            "StarRocks type catalog should include current scalar and complex DDL types."
        }

        val quoted = StarRocksDefinitionProvider.quoteIdentifier("dws`schema")
        check(quoted == "`dws``schema`") { "StarRocks identifier quoting should escape backticks." }
        check(StarRocksDdlStatements.showCreateStatement(ObjectKind.TABLE, "`db`.`tbl`") == "SHOW CREATE TABLE `db`.`tbl`") {
            "StarRocks table DDL provider should use SHOW CREATE TABLE."
        }
        check(StarRocksDdlStatements.showCreateStatement(ObjectKind.MAT_VIEW, "`db`.`mv`") ==
            "SHOW CREATE MATERIALIZED VIEW `db`.`mv`") {
            "StarRocks materialized view DDL provider should use SHOW CREATE MATERIALIZED VIEW."
        }

        val pluginXml = projectDir.resolve("src/main/resources/META-INF/plugin.xml").readText()
        listOf(
            "<typeSystem dbms=\"STARROCKS\"",
            "<definitionProvider dbms=\"STARROCKS\"",
            "<introspector dbms=\"STARROCKS\"",
            "<jdbcSourceLoader dbms=\"STARROCKS\"",
            "<jdbcMetadataWrapper dbms=\"STARROCKS\"",
            "<sql.dialectCodeStyleProvider implementation=\"com.jc.starrocks.datagrip.format.StarRocksCodeStyleProvider\"",
            "<sql.formatterHelper language=\"StarRocks\" implementationClass=\"com.jc.starrocks.datagrip.format.StarRocksFormatterHelper\""
        ).forEach { marker ->
            check(marker in pluginXml) { "plugin.xml is missing database integration marker $marker." }
        }
        check("<lang.formatter language=\"StarRocks\"" !in pluginXml) {
            "StarRocks must use the platform SQL formatting model instead of a private formatter builder."
        }
        check("<stubElementTypeHolder" !in pluginXml && "<stubIndex" !in pluginXml) {
            "StarRocks must not register private named-stub infrastructure or an unused global SQL name index."
        }
    }

    private fun validateEditorArchitecture(projectDir: File) {
        val elementRegistry = projectDir.resolve(
            "src/main/kotlin/com/jc/starrocks/datagrip/lang/StarRocksElementTypeRegistry.kt"
        ).readText()
        check("getField(" !in elementRegistry && "SqlCompositeElementTypes.SQL_COLUMN_REFERENCE" in elementRegistry) {
            "StarRocks platform PSI mappings must be explicit and must not use reflective fallback."
        }

        val grammar = projectDir.resolve("grammar/starrocks.bnf").readText()
        check("elementType=\"SQL_COLUMN_REFERENCE\"" in grammar && "elementType=\"SQL_WINDOW_REFERENCE\"" in grammar) {
            "Column and window references must use platform SQL PSI reference element types."
        }
        check(Regex("column_definition\\s*::=.*elementType=\\\"SQL_COLUMN_DEFINITION\\\"", RegexOption.DOT_MATCHES_ALL).containsMatchIn(grammar)) {
            "Column definitions must use platform SQL_COLUMN_DEFINITION PSI directly."
        }

        val productionSources = projectDir.resolve("src/main").walkTopDown()
            .filter { it.isFile && it.extension in setOf("java", "kt", "xml") }
            .joinToString("\n") { it.readText() }
        check("StarRocksNamedStub" !in productionSources && "StarRocksStubElementTypes" !in productionSources) {
            "Private StarRocks named-stub infrastructure must be removed from production sources."
        }
        val parserDefinition = projectDir.resolve(
            "src/main/kotlin/com/jc/starrocks/datagrip/lang/StarRocksParserDefinition.kt"
        ).readText()
        check("SqlFileElementType" in parserDefinition) {
            "The parser definition must use SqlFileElementType so the platform creates SqlFileBlock for scripts."
        }

        val contributor = projectDir.resolve(
            "src/main/kotlin/com/jc/starrocks/datagrip/completion/StarRocksCompletionContributor.kt"
        ).readText()
        listOf("addTableCompletions", "addColumnCompletions", "addKeywordCompletions", "addTypeCompletions", "addFunctionCompletions")
            .forEach { legacyPath ->
                check(legacyPath !in contributor) {
                    "Platform SQL completion must own $legacyPath instead of the supplementary contributor."
                }
            }

        val functionsXml = projectDir.resolve(
            "src/main/resources/com/jc/starrocks/datagrip/dialect/functions.xml"
        ).readText()
        check(Regex("<function(?:\\s[^>]*)?>").findAll(functionsXml).count() >= 525) {
            "The platform function catalog must contain the StarRocks server function set."
        }
        listOf(
            "REGEXP_COUNT",
            "REGEXP_EXTRACT_ALL",
            "REGEXP_SPLIT",
            "ARRAY_REPEAT",
            "CHARACTER_LENGTH",
            "URL_ENCODE"
        ).forEach { function ->
            check("<name>$function</name>" in functionsXml) {
                "The platform function catalog must contain current public StarRocks function $function."
            }
        }
        check(
            Regex(
                "<name>UNNEST</name>\\s*<prototype>\\(a:array\\.\\.\\.\\):table\\(unnest:ANY\\)</prototype>"
            ).containsMatchIn(functionsXml)
        ) {
            "UNNEST must remain a table-valued builtin so connected data-source resolution keeps its output column."
        }
        check(
            Regex(
                "<macro name=\\\"agg_order_by\\\">\\s*" +
                    Regex.escape("ORDER BY (sort_key:ANY [{ASC | DESC}] [NULLS {FIRST | LAST}])...") +
                    "\\s*</macro>"
            ).containsMatchIn(functionsXml)
        ) {
            "Aggregate ORDER BY prototypes require direction and null-ordering support in the agg_order_by macro."
        }
        mapOf(
            "AVG" to "([set_quantifier] expr:ANY):ANY",
            "COUNT" to "([set_quantifier] expr:ANY...):N",
            "MAX" to "([set_quantifier] expr:ANY):ANY",
            "MIN" to "([set_quantifier] expr:ANY):ANY",
            "SUM" to "([set_quantifier] expr:X):P0",
            "ARRAY_AGG" to "([set_quantifier] expr:ANY [agg_order_by]):ANY",
            "ARRAY_AGG_DISTINCT" to "(expr:ANY [agg_order_by]):ANY",
            "GROUP_CONCAT" to "([set_quantifier] expr:str... [agg_order_by] [SEPARATOR expr:str]):str",
            "STRING_AGG" to "([set_quantifier] expr:str, delimiter:str [agg_order_by]):str"
        ).forEach { (function, prototype) ->
            check(
                Regex(
                    "<function aggregate=\\\"true\\\">\\s*<name>$function</name>\\s*" +
                        "<prototype>${Regex.escape(prototype)}</prototype>"
                ).containsMatchIn(functionsXml)
            ) {
                "$function must keep its aggregate modifiers in the platform function prototype."
            }
        }
        mapOf(
            "TIMESTAMPADD" to listOf("(timestamp_unit, interval:ANY, datetime_expr:ANY):ANY"),
            "TIMESTAMPDIFF" to listOf("(timestamp_unit, datetime_expr1:ANY, datetime_expr2:ANY):N"),
            "TRIM" to listOf(
                "(str:str):str",
                "(str:str, characters:str):str",
                "([{BOTH | LEADING | TRAILING}] [characters:str] FROM str:str):str"
            )
        ).forEach { (function, prototypes) ->
            check(
                Regex(
                    "<function>\\s*<name>$function</name>\\s*" +
                        prototypes.joinToString("\\s*") { "<prototype>${Regex.escape(it)}</prototype>" } +
                        "\\s*</function>"
                ).containsMatchIn(functionsXml)
            ) {
                "$function must keep keyword-bearing arguments in its platform function prototypes."
            }
        }
        mapOf(
            "timestamp_unit" to "{MICROSECOND | MILLISECOND | SECOND | MINUTE | HOUR | DAY | WEEK | MONTH | QUARTER | YEAR}"
        ).forEach { (macro, body) ->
            check(
                Regex(
                    "<macro name=\\\"$macro\\\">\\s*${Regex.escape(body)}\\s*</macro>"
                ).containsMatchIn(functionsXml)
            ) {
                "$macro must keep supported bare date/time units out of column resolution."
            }
        }
        listOf(
            "CUME_DIST",
            "DENSE_RANK",
            "FIRST_VALUE",
            "LAG",
            "LAST_VALUE",
            "LEAD",
            "NTILE",
            "PERCENT_RANK",
            "RANK",
            "ROW_NUMBER"
        ).forEach { function ->
            check(
                Regex("<function analytic=\"true\">\\s*<name>$function</name>")
                    .containsMatchIn(functionsXml)
            ) {
                "$function must remain an analytic function for inspection and completion."
            }
        }
        mapOf(
            "FIRST_VALUE" to listOf("(expr:ANY [IGNORE NULLS]):ANY"),
            "LAST_VALUE" to listOf("(expr:ANY [IGNORE NULLS]):ANY"),
            "LAG" to listOf(
                "(expr:ANY [IGNORE NULLS]):ANY",
                "(expr:ANY [IGNORE NULLS], offset:N):ANY",
                "(expr:ANY [IGNORE NULLS], offset:N, default:ANY):ANY"
            ),
            "LEAD" to listOf(
                "(expr:ANY [IGNORE NULLS]):ANY",
                "(expr:ANY [IGNORE NULLS], offset:N):ANY",
                "(expr:ANY [IGNORE NULLS], offset:N, default:ANY):ANY"
            )
        ).forEach { (function, prototypes) ->
            check(
                Regex(
                    "<function analytic=\\\"true\\\">\\s*<name>$function</name>\\s*" +
                        prototypes.joinToString("\\s*") { "<prototype>${Regex.escape(it)}</prototype>" } +
                        "\\s*</function>"
                ).containsMatchIn(functionsXml)
            ) {
                "$function must keep IGNORE NULLS in every supported analytic-function prototype."
            }
        }
        listOf("CUME_DIST", "DENSE_RANK", "PERCENT_RANK", "RANK", "ROW_NUMBER").forEach { function ->
            check(
                Regex("<name>$function</name>\\s*<prototype>\\(\\):N</prototype>")
                    .containsMatchIn(functionsXml)
            ) {
                "$function must remain a zero-argument numeric function."
            }
        }
        mapOf(
            "UUID" to "str",
            "UUID_NUMERIC" to "N",
            "UUID_V7" to "str",
            "UUID_V7_NUMERIC" to "N"
        ).forEach { (function, returnType) ->
            check(
                Regex(
                    "<function parens=\\\"required\\\">\\s*<name>$function</name>\\s*" +
                        "<prototype>\\(\\):$returnType</prototype>\\s*</function>"
                ).containsMatchIn(functionsXml)
            ) {
                "$function must remain a required-parentheses zero-argument function."
            }
        }

        val keywordCatalog = projectDir.resolve("grammar/starrocks-keywords.txt").readText()
        check("[reserved]" in keywordCatalog && "[optional]" in keywordCatalog) {
            "StarRocks keywords must come from the standalone canonical keyword catalog."
        }
        check(setOf("ELASTICSEARCH", "HIVE", "HUDI", "ICEBERG", "JDBC", "MYSQL").all { it in keywordCatalog }) {
            "StarRocks external table engines must be registered SQL keywords."
        }
        val buildScript = projectDir.resolve("build.gradle.kts").readText()
        check("grammar/starrocks-keywords.txt" in buildScript && "StarRocksKeywordCatalog.kt" !in buildScript) {
            "Gradle must generate keyword registries from the standalone catalog, not parse Kotlin source text."
        }
    }

    private fun validateLegacyTextAnalyzersAreTestOnly(projectDir: File) {
        listOf(
            "src/main/kotlin/com/jc/starrocks/datagrip/resolve/StarRocksLocalSqlContext.kt",
            "src/main/kotlin/com/jc/starrocks/datagrip/lang/StarRocksStatementWordsClassifier.kt",
            "src/main/kotlin/com/jc/starrocks/datagrip/lang/StarRocksStatementFamily.kt",
            "src/main/kotlin/com/jc/starrocks/datagrip/resolve/StarRocksPsiScope.kt",
            "src/main/kotlin/com/jc/starrocks/datagrip/resolve/StarRocksColumnReference.kt",
            "src/main/kotlin/com/jc/starrocks/datagrip/resolve/StarRocksTableAliasReference.kt",
            "src/main/kotlin/com/jc/starrocks/datagrip/resolve/StarRocksWindowReference.kt"
        ).forEach { path ->
            check(!projectDir.resolve(path).exists()) {
                "Legacy text analyzer/classifier must not stay in production sources: $path"
            }
        }
        listOf(
            "src/test/kotlin/com/jc/starrocks/datagrip/resolve/StarRocksLocalSqlContext.kt",
            "src/test/kotlin/com/jc/starrocks/datagrip/lang/StarRocksStatementWordsClassifier.kt",
            "src/test/kotlin/com/jc/starrocks/datagrip/lang/StarRocksStatementFamily.kt"
        ).forEach { path ->
            check(projectDir.resolve(path).isFile) {
                "Legacy text analyzer/classifier should be isolated to test validation sources: $path"
            }
        }
    }

    private fun validateDmlMutationFixtureCoverage(sql: String) {
        val statements = splitStatements(sql)
        check(statements.size == 4) {
            "DML mutations fixture should contain 4 standalone statements, found ${statements.size}."
        }
        listOf("INSERT OVERWRITE", "UPDATE", "DELETE", "MERGE").forEach { keyword ->
            check(keyword in sql) { "DML mutations fixture must cover $keyword." }
        }
    }

    private fun validateSecurityAndTransactionFixtureCoverage(sql: String) {
        val statements = splitStatements(sql)
        check(statements.size == 14) {
            "Security/transaction fixture should contain 14 standalone statements, found ${statements.size}."
        }
        listOf("SET PASSWORD", "GRANT", "REVOKE", "CALL", "BEGIN", "START TRANSACTION", "COMMIT", "ROLLBACK").forEach { keyword ->
            check(keyword in sql) { "Security/transaction fixture must cover $keyword." }
        }
    }

    private fun validateSchemaAndIndexFixtureCoverage(sql: String) {
        val statements = splitStatements(sql)
        check(statements.size == 9) {
            "Schema/index fixture should contain 9 standalone statements, found ${statements.size}."
        }
        listOf("CREATE DATABASE", "CREATE SCHEMA", "ALTER DATABASE", "ALTER SCHEMA", "CREATE INDEX", "DROP INDEX").forEach { keyword ->
            check(keyword in sql) { "Schema/index fixture must cover $keyword." }
        }
    }

    private fun splitStatements(sql: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var index = 0
        var parenDepth = 0
        var quote: Char? = null
        while (index < sql.length) {
            val char = sql[index]
            if (quote != null) {
                if (char == quote) {
                    if (index + 1 < sql.length && sql[index + 1] == quote) {
                        index += 2
                        continue
                    }
                    quote = null
                } else if (char == '\\') {
                    index++
                }
                index++
                continue
            }
            when (char) {
                '\'', '"' -> quote = char
                '(' -> parenDepth++
                ')' -> if (parenDepth > 0) parenDepth--
                ';' -> if (parenDepth == 0) {
                    sql.substring(start, index).trim().takeIf { it.isNotBlank() }?.let(result::add)
                    start = index + 1
                }
            }
            index++
        }
        sql.substring(start).trim().takeIf { it.isNotBlank() }?.let(result::add)
        return result
    }

}

private data class ScenarioMetadata(
    val milestone: StarRocksGrammarMilestone,
    val features: Set<StarRocksFeature>
)
