package com.jc.starrocks.datagrip.lang

import com.intellij.psi.tree.IElementType
import com.intellij.sql.psi.SqlCompositeElementTypes
import com.intellij.sql.psi.SqlKeywordTokenType
import com.intellij.sql.psi.SqlTokenType
import com.intellij.sql.psi.SqlTokens
import com.intellij.sql.util.SqlTokenRegistry
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal object StarRocksElementTypeRegistry {
    private val tokens = ConcurrentHashMap<String, SqlTokenType>()

    fun token(text: String): SqlTokenType {
        val normalized = text.uppercase(Locale.ROOT)
        return tokens.computeIfAbsent(normalized, ::createToken)
    }

    fun elementType(name: String): IElementType {
        platformCompositeElementType(name)?.let { return it }
        check(!name.startsWith("SQL_")) {
            "StarRocks grammar element $name must have an explicit platform element type mapping."
        }
        return StarRocksElementType("STARROCKS_$name")
    }

    private fun createToken(normalized: String): SqlTokenType {
        if (normalized == "STARROCKS_PARAMETER") {
            return StarRocksCustomTokenTypes.PARAMETER
        }
        punctuationToken(normalized)?.let { return it }
        return SqlTokenRegistry.getType(normalized, SqlKeywordTokenType.FACTORY)
    }

    private fun punctuationToken(text: String): SqlTokenType? = when (text) {
        "(" -> SqlTokens.SQL_LEFT_PAREN
        ")" -> SqlTokens.SQL_RIGHT_PAREN
        "[" -> SqlTokens.SQL_LEFT_BRACKET
        "]" -> SqlTokens.SQL_RIGHT_BRACKET
        "{" -> SqlTokens.SQL_LEFT_BRACE
        "}" -> SqlTokens.SQL_RIGHT_BRACE
        "," -> SqlTokens.SQL_COMMA
        ";" -> SqlTokens.SQL_SEMICOLON
        "." -> SqlTokens.SQL_PERIOD
        ":" -> SqlTokens.SQL_COLON
        "+" -> SqlTokens.SQL_OP_PLUS
        "-" -> SqlTokens.SQL_OP_MINUS
        "*" -> SqlTokens.SQL_ASTERISK
        "/" -> SqlTokens.SQL_OP_DIV
        "%" -> SqlTokens.SQL_OP_MODULO
        "=" -> SqlTokens.SQL_OP_EQ
        "<" -> SqlTokens.SQL_OP_LT
        ">" -> SqlTokens.SQL_OP_GT
        "<=" -> SqlTokens.SQL_OP_LE
        ">=" -> SqlTokens.SQL_OP_GE
        "<>" -> SqlTokens.SQL_OP_NEQ
        "!=" -> SqlTokens.SQL_OP_NEQ2
        "<<" -> SqlTokens.SQL_OP_LEFT_SHIFT
        ">>" -> SqlTokens.SQL_OP_RIGHT_SHIFT
        "||" -> SqlTokens.SQL_OP_CONCAT
        "!" -> SqlTokens.SQL_OP_NOT2
        "|" -> SqlTokens.SQL_OP_BITWISE_OR
        "&" -> SqlTokens.SQL_OP_BITWISE_AND
        "?" -> StarRocksCustomTokenTypes.PARAMETER
        else -> null
    }

    private fun platformCompositeElementType(name: String): IElementType? = when (name) {
        "SQL_ALTER_CATALOG_STATEMENT" -> SqlCompositeElementTypes.SQL_ALTER_CATALOG_STATEMENT
        "SQL_ALTER_SCHEMA_STATEMENT" -> SqlCompositeElementTypes.SQL_ALTER_SCHEMA_STATEMENT
        "SQL_ALTER_TABLE_STATEMENT" -> SqlCompositeElementTypes.SQL_ALTER_TABLE_STATEMENT
        "SQL_ALTER_VIEW_STATEMENT" -> SqlCompositeElementTypes.SQL_ALTER_VIEW_STATEMENT
        "SQL_ANALYTIC_CLAUSE" -> SqlCompositeElementTypes.SQL_ANALYTIC_CLAUSE
        "SQL_ARGUMENT_LIST" -> SqlCompositeElementTypes.SQL_ARGUMENT_LIST
        "SQL_AS_QUERY_CLAUSE" -> SqlCompositeElementTypes.SQL_AS_QUERY_CLAUSE
        "SQL_AS_EXPRESSION" -> SqlCompositeElementTypes.SQL_AS_EXPRESSION
        "SQL_ARRAY_LITERAL" -> SqlCompositeElementTypes.SQL_ARRAY_LITERAL
        "SQL_BETWEEN_EXPRESSION" -> SqlCompositeElementTypes.SQL_BETWEEN_EXPRESSION
        "SQL_BINARY_EXPRESSION" -> SqlCompositeElementTypes.SQL_BINARY_EXPRESSION
        "SQL_CALL_STATEMENT" -> SqlCompositeElementTypes.SQL_CALL_STATEMENT
        "SQL_CATALOG_REFERENCE" -> SqlCompositeElementTypes.SQL_CATALOG_REFERENCE
        "SQL_COLUMN_ALIAS_DEFINITION" -> SqlCompositeElementTypes.SQL_COLUMN_ALIAS_DEFINITION
        "SQL_COLUMN_ALIAS_LIST" -> SqlCompositeElementTypes.SQL_COLUMN_ALIAS_LIST
        "SQL_COLUMN_REFERENCE" -> SqlCompositeElementTypes.SQL_COLUMN_REFERENCE
        "SQL_COLUMN_SHORT_REFERENCE" -> SqlCompositeElementTypes.SQL_COLUMN_SHORT_REFERENCE
        "SQL_COLUMN_DEFINITION" -> SqlCompositeElementTypes.SQL_COLUMN_DEFINITION
        "SQL_COLUMN_GENERATED_CLAUSE" -> SqlCompositeElementTypes.SQL_COLUMN_GENERATED_CLAUSE
        "SQL_COMMIT_STATEMENT" -> SqlCompositeElementTypes.SQL_COMMIT_STATEMENT
        "SQL_CREATE_CATALOG_STATEMENT" -> SqlCompositeElementTypes.SQL_CREATE_CATALOG_STATEMENT
        "SQL_CREATE_INDEX_STATEMENT" -> SqlCompositeElementTypes.SQL_CREATE_INDEX_STATEMENT
        "SQL_CREATE_MATERIALIZED_VIEW_STATEMENT" -> SqlCompositeElementTypes.SQL_CREATE_MATERIALIZED_VIEW_STATEMENT
        "SQL_CREATE_SCHEMA_STATEMENT" -> SqlCompositeElementTypes.SQL_CREATE_SCHEMA_STATEMENT
        "SQL_CREATE_TABLE_STATEMENT" -> SqlCompositeElementTypes.SQL_CREATE_TABLE_STATEMENT
        "SQL_CREATE_VIEW_STATEMENT" -> SqlCompositeElementTypes.SQL_CREATE_VIEW_STATEMENT
        "SQL_DELETE_STATEMENT" -> SqlCompositeElementTypes.SQL_DELETE_STATEMENT
        "SQL_DELETE_DML_INSTRUCTION" -> SqlCompositeElementTypes.SQL_DELETE_DML_INSTRUCTION
        "SQL_EXPLAIN_STATEMENT" -> SqlCompositeElementTypes.SQL_EXPLAIN_STATEMENT
        "SQL_EXPLICIT_TABLE_EXPRESSION" -> SqlCompositeElementTypes.SQL_EXPLICIT_TABLE_EXPRESSION
        "SQL_EXPRESSION_LIST" -> SqlCompositeElementTypes.SQL_EXPRESSION_LIST
        "SQL_FROM_CLAUSE" -> SqlCompositeElementTypes.SQL_FROM_CLAUSE
        "SQL_FUNCTION_CALL" -> SqlCompositeElementTypes.SQL_FUNCTION_CALL
        "SQL_GENERIC_DEFINITION" -> SqlCompositeElementTypes.SQL_GENERIC_DEFINITION
        "SQL_GROUP_BY_CLAUSE" -> SqlCompositeElementTypes.SQL_GROUP_BY_CLAUSE
        "SQL_HAVING_CLAUSE" -> SqlCompositeElementTypes.SQL_HAVING_CLAUSE
        "SQL_INSERT_STATEMENT" -> SqlCompositeElementTypes.SQL_INSERT_STATEMENT
        "SQL_INSERT_DML_INSTRUCTION" -> SqlCompositeElementTypes.SQL_INSERT_DML_INSTRUCTION
        "SQL_IDENTIFIER" -> SqlCompositeElementTypes.SQL_IDENTIFIER
        "SQL_INDEX_REFERENCE" -> SqlCompositeElementTypes.SQL_INDEX_REFERENCE
        "SQL_INDEX_DEFINITION" -> SqlCompositeElementTypes.SQL_INDEX_DEFINITION
        "SQL_JOIN_CONDITION_CLAUSE" -> SqlCompositeElementTypes.SQL_JOIN_CONDITION_CLAUSE
        "SQL_JOIN_EXPRESSION" -> SqlCompositeElementTypes.SQL_JOIN_EXPRESSION
        "SQL_LIMIT_CLAUSE" -> SqlCompositeElementTypes.SQL_LIMIT_CLAUSE
        "SQL_LIKE_TABLE_CLAUSE" -> SqlCompositeElementTypes.SQL_LIKE_TABLE_CLAUSE
        "SQL_MERGE_STATEMENT" -> SqlCompositeElementTypes.SQL_MERGE_STATEMENT
        "SQL_MATERIALIZED_VIEW_REFERENCE" -> SqlCompositeElementTypes.SQL_MATERIALIZED_VIEW_REFERENCE
        "SQL_NAMED_QUERY_DEFINITION" -> SqlCompositeElementTypes.SQL_NAMED_QUERY_DEFINITION
        "SQL_NUMERIC_LITERAL" -> SqlCompositeElementTypes.SQL_NUMERIC_LITERAL
        "SQL_ORDER_BY_CLAUSE" -> SqlCompositeElementTypes.SQL_ORDER_BY_CLAUSE
        "SQL_PARENTHESIZED_EXPRESSION" -> SqlCompositeElementTypes.SQL_PARENTHESIZED_EXPRESSION
        "SQL_PARENTHESIZED_JOIN_EXPRESSION" -> SqlCompositeElementTypes.SQL_PARENTHESIZED_JOIN_EXPRESSION
        "SQL_PARENTHESIZED_QUERY_EXPRESSION" -> SqlCompositeElementTypes.SQL_PARENTHESIZED_QUERY_EXPRESSION
        "SQL_PARTITION_DEFINITION" -> SqlCompositeElementTypes.SQL_PARTITION_DEFINITION
        "SQL_PARTITION_REFERENCE" -> SqlCompositeElementTypes.SQL_PARTITION_REFERENCE
        "SQL_QUALIFY_CLAUSE" -> SqlCompositeElementTypes.SQL_QUALIFY_CLAUSE
        "SQL_QUERY_EXPRESSION" -> SqlCompositeElementTypes.SQL_QUERY_EXPRESSION
        "SQL_WITH_QUERY_EXPRESSION" -> SqlCompositeElementTypes.SQL_WITH_QUERY_EXPRESSION
        "SQL_ON_TARGET_CLAUSE" -> SqlCompositeElementTypes.SQL_ON_TARGET_CLAUSE
        "SQL_TABLE_COLUMNS_LIST" -> SqlCompositeElementTypes.SQL_TABLE_COLUMNS_LIST
        "SQL_REFERENCE_LIST" -> SqlCompositeElementTypes.SQL_REFERENCE_LIST
        "SQL_REFERENCE" -> SqlCompositeElementTypes.SQL_REFERENCE
        "SQL_ROLLBACK_STATEMENT" -> SqlCompositeElementTypes.SQL_ROLLBACK_STATEMENT
        "SQL_SCHEMA_REFERENCE" -> SqlCompositeElementTypes.SQL_SCHEMA_REFERENCE
        "SQL_SELECT_CLAUSE" -> SqlCompositeElementTypes.SQL_SELECT_CLAUSE
        "SQL_SELECT_STATEMENT" -> SqlCompositeElementTypes.SQL_SELECT_STATEMENT
        "SQL_SET_CLAUSE" -> SqlCompositeElementTypes.SQL_SET_CLAUSE
        "SQL_SET_ASSIGNMENT" -> SqlCompositeElementTypes.SQL_SET_ASSIGNMENT
        "SQL_SET_STATEMENT" -> SqlCompositeElementTypes.SQL_SET_STATEMENT
        "SQL_SPECIAL_LITERAL" -> SqlCompositeElementTypes.SQL_SPECIAL_LITERAL
        "SQL_START_TRANSACTION_STATEMENT" -> SqlCompositeElementTypes.SQL_START_TRANSACTION_STATEMENT
        "SQL_STRING_LITERAL" -> SqlCompositeElementTypes.SQL_STRING_LITERAL
        "SQL_TABLE_ELEMENT_LIST" -> SqlCompositeElementTypes.SQL_TABLE_ELEMENT_LIST
        "SQL_TABLE_EXPRESSION" -> SqlCompositeElementTypes.SQL_TABLE_EXPRESSION
        "SQL_TABLE_PROCEDURE_CALL_EXPRESSION" -> SqlCompositeElementTypes.SQL_TABLE_PROCEDURE_CALL_EXPRESSION
        "SQL_TABLE_REFERENCE" -> SqlCompositeElementTypes.SQL_TABLE_REFERENCE
        "SQL_TRUNCATE_TABLE_STATEMENT" -> SqlCompositeElementTypes.SQL_TRUNCATE_TABLE_STATEMENT
        "SQL_TYPE_ELEMENT" -> SqlCompositeElementTypes.SQL_TYPE_ELEMENT
        "SQL_TYPE_CAST_EXPRESSION" -> SqlCompositeElementTypes.SQL_TYPE_CAST_EXPRESSION
        "SQL_TYPE_PARAMETER_LIST" -> SqlCompositeElementTypes.SQL_TYPE_PARAMETER_LIST
        "SQL_UNION_EXPRESSION" -> SqlCompositeElementTypes.SQL_UNION_EXPRESSION
        "SQL_UPDATE_STATEMENT" -> SqlCompositeElementTypes.SQL_UPDATE_STATEMENT
        "SQL_UPDATE_DML_INSTRUCTION" -> SqlCompositeElementTypes.SQL_UPDATE_DML_INSTRUCTION
        "SQL_USE_SCHEMA_STATEMENT" -> SqlCompositeElementTypes.SQL_USE_SCHEMA_STATEMENT
        "SQL_USING_CLAUSE" -> SqlCompositeElementTypes.SQL_USING_CLAUSE
        "SQL_VALUES_EXPRESSION" -> SqlCompositeElementTypes.SQL_VALUES_EXPRESSION
        "SQL_VIEW_REFERENCE" -> SqlCompositeElementTypes.SQL_VIEW_REFERENCE
        "SQL_WHERE_CLAUSE" -> SqlCompositeElementTypes.SQL_WHERE_CLAUSE
        "SQL_WINDOW_CLAUSE" -> SqlCompositeElementTypes.SQL_WINDOW_CLAUSE
        "SQL_WINDOW_REFERENCE" -> SqlCompositeElementTypes.SQL_WINDOW_REFERENCE
        "SQL_WITH_CLAUSE" -> SqlCompositeElementTypes.SQL_WITH_CLAUSE
        else -> null
    }
}
