package com.jc.starrocks.datagrip.lang;

import com.intellij.lang.PsiBuilder;
import com.intellij.sql.dialects.base.SqlGeneratedParserUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.sql.psi.SqlTokens;

import java.util.regex.Pattern;
import java.util.Set;

public class StarRocksParserUtil extends SqlGeneratedParserUtil {
    private static final Pattern FUNCTION_WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");
    private static final Set<String> NON_FUNCTION_KEYWORDS = Set.of(
        "AS", "CASE", "ELSE", "END", "EXCEPT", "FROM", "GROUP", "HAVING", "IN",
        "INTERSECT", "JOIN", "LIMIT", "ON", "ORDER", "OVER", "QUALIFY", "SELECT",
        "THEN", "UNION", "WHEN", "WHERE", "WINDOW", "WITH"
    );

    public static boolean parseFunctionCallTail(PsiBuilder builder, int level) {
        return PARSE_FUNCTION_CALL_TAIL.parse(builder, level);
    }

    public static boolean parseFunctionKeyword(PsiBuilder builder, int level) {
        if (builder.getTokenType() == null || builder.getTokenType() == SqlTokens.SQL_IDENT ||
            builder.getTokenType() == SqlTokens.SQL_IDENT_DELIMITED) {
            return false;
        }
        if (builder.lookAhead(1) != StarRocksElementTypes.SQL_LEFT_PAREN &&
            builder.lookAhead(1) != StarRocksElementTypes.SQL_PERIOD) {
            return false;
        }
        String text = builder.getTokenText();
        if (text == null || NON_FUNCTION_KEYWORDS.contains(text.toUpperCase()) ||
            !FUNCTION_WORD.matcher(text).matches()) {
            return false;
        }
        builder.advanceLexer();
        return true;
    }

    public static boolean parseOptionalKeyword(PsiBuilder builder, int level) {
        String text = builder.getTokenText();
        if (text == null || !StarRocksKeywordCatalog.isOptionalKeyword(text)) {
            return false;
        }
        builder.advanceLexer();
        return true;
    }

    public static boolean parseMaterializedViewReference(PsiBuilder builder, int level) {
        return parseReference(builder, level, SqlCompositeElementTypes.SQL_MATERIALIZED_VIEW_REFERENCE);
    }

    public static boolean parseViewReference(PsiBuilder builder, int level) {
        return parseReference(builder, level, SqlCompositeElementTypes.SQL_VIEW_REFERENCE);
    }

    public static boolean parseSchemaReference(PsiBuilder builder, int level) {
        return parseReference(builder, level, SqlCompositeElementTypes.SQL_SCHEMA_REFERENCE);
    }

    public static boolean parseCatalogReference(PsiBuilder builder, int level) {
        return parseReference(builder, level, SqlCompositeElementTypes.SQL_CATALOG_REFERENCE);
    }

    public static boolean parseIndexReference(PsiBuilder builder, int level) {
        return parseReference(builder, level, SqlCompositeElementTypes.SQL_INDEX_REFERENCE);
    }

    public static boolean parseParameter(PsiBuilder builder, int level) {
        String text = builder.getTokenText();
        // SqlParser's adapted builder may split an otherwise single JFlex template token
        // into an opening marker, its body, and a closing marker.
        if (text != null && (text.equals("${") || text.equals("$["))) {
            String closing = text.equals("${") ? "}" : "]";
            builder.advanceLexer();
            while (builder.getTokenType() != null && !closing.equals(builder.getTokenText())) {
                builder.advanceLexer();
            }
            if (closing.equals(builder.getTokenText())) {
                builder.advanceLexer();
            }
            return true;
        }
        if (text == null || !(text.equals("?") || text.startsWith(":") ||
            (text.startsWith("${") && text.endsWith("}")) ||
            (text.startsWith("$[") && text.endsWith("]")))) {
            return false;
        }
        builder.advanceLexer();
        return true;
    }

    public static boolean parseVariable(PsiBuilder builder, int level) {
        String text = builder.getTokenText();
        if (text == null || !text.startsWith("@")) {
            return false;
        }
        builder.advanceLexer();
        if ((text.equals("@") || text.equals("@@")) && builder.getTokenType() == SqlTokens.SQL_IDENT) {
            builder.advanceLexer();
        }
        return true;
    }

    public static boolean isWithUpdate(PsiBuilder builder, int level) {
        PsiBuilder.Marker marker = builder.mark();
        boolean result = StarRocksGeneratedParser.with_clause(builder, level + 1) &&
            StarRocksGeneratedParser.update_statement(builder, level + 1);
        marker.rollbackTo();
        return result;
    }
}
