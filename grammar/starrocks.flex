package com.jc.starrocks.datagrip.lang;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.sql.psi.SqlTokens;
import java.util.Locale;

%%

%public
%class _StarRocksParserLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode
%ignorecase

%{
  private IElementType keyword() {
    return StarRocksElementFactory.token(yytext().toString().toUpperCase(Locale.ROOT));
  }
%}

WHITE_SPACE=[ \t\r\n\f]+
LINE_COMMENT="--"[^\r\n]*
BLOCK_COMMENT="/*"([^*]|\*+[^*/])*\*+"/"
IDENT=[A-Za-z_\p{L}][A-Za-z0-9_$\p{L}\p{N}\uFF08\uFF09]*
DIGIT_IDENT=[0-9]+[A-Za-z_\p{L}][A-Za-z0-9_$\p{L}\p{N}\uFF08\uFF09]*
SIZE_VALUE=[0-9]+[KMGTP]
TEMPLATED_IDENT=[A-Za-z_\p{L}][A-Za-z0-9_$\p{L}\p{N}]*(\$\[[^\]]*\]|\$\{[^}]*\})
SYSTEM_VARIABLE="@"{1,2}[A-Za-z_][A-Za-z0-9_.$]*
DELIMITED_IDENT=`([^`]|``)*`
NAMED_PARAMETER=":"[A-Za-z_][A-Za-z0-9_$]*
BRACED_PARAMETER="$""{"[^}]*"}"
BRACKETED_PARAMETER=\$\[[^\]]*\]
INTEGER=[0-9]+
FLOAT=[0-9]+"."[0-9]*([eE][+-]?[0-9]+)?
STRING='([^'\\]|\\.|'')*'|\"([^\"\\]|\\.|\"\")*\"

%%

{WHITE_SPACE}        { return TokenType.WHITE_SPACE; }
{LINE_COMMENT}       { return SqlTokens.SQL_LINE_COMMENT; }
{BLOCK_COMMENT}      { return SqlTokens.SQL_BLOCK_COMMENT; }
{STRING}             { return SqlTokens.SQL_STRING_TOKEN; }
{DELIMITED_IDENT}    { return SqlTokens.SQL_IDENT_DELIMITED; }
{SYSTEM_VARIABLE}    { return SqlTokens.SQL_IDENT; }
{NAMED_PARAMETER}    { return StarRocksCustomTokenTypes.PARAMETER; }
{BRACED_PARAMETER}   { return StarRocksCustomTokenTypes.PARAMETER; }
{BRACKETED_PARAMETER} { return StarRocksCustomTokenTypes.PARAMETER; }
"?"                  { return StarRocksCustomTokenTypes.PARAMETER; }
{TEMPLATED_IDENT}     { return SqlTokens.SQL_IDENT; }
{SIZE_VALUE}          { yypushback(1); return SqlTokens.SQL_INTEGER_TOKEN; }
{DIGIT_IDENT}         { return SqlTokens.SQL_IDENT; }
{FLOAT}              { return SqlTokens.SQL_FLOAT_TOKEN; }
{INTEGER}            { return SqlTokens.SQL_INTEGER_TOKEN; }

"<=>"                { return StarRocksElementTypes.STARROCKS_OP_NULL_SAFE_EQ; }
"<="                 { return SqlTokens.SQL_OP_LE; }
">="                 { return SqlTokens.SQL_OP_GE; }
"<>"                 { return SqlTokens.SQL_OP_NEQ; }
"!="                 { return SqlTokens.SQL_OP_NEQ2; }
"<<"                 { return SqlTokens.SQL_OP_LEFT_SHIFT; }
">>"                 { return SqlTokens.SQL_OP_RIGHT_SHIFT; }
"||"                 { return SqlTokens.SQL_OP_CONCAT; }
"("                  { return SqlTokens.SQL_LEFT_PAREN; }
")"                  { return SqlTokens.SQL_RIGHT_PAREN; }
"["                  { return SqlTokens.SQL_LEFT_BRACKET; }
"]"                  { return SqlTokens.SQL_RIGHT_BRACKET; }
"{"                  { return SqlTokens.SQL_LEFT_BRACE; }
"}"                  { return SqlTokens.SQL_RIGHT_BRACE; }
","                  { return SqlTokens.SQL_COMMA; }
";"                  { return SqlTokens.SQL_SEMICOLON; }
"."                  { return SqlTokens.SQL_PERIOD; }
"+"                  { return SqlTokens.SQL_OP_PLUS; }
"-"                  { return SqlTokens.SQL_OP_MINUS; }
"*"                  { return SqlTokens.SQL_ASTERISK; }
"/"                  { return SqlTokens.SQL_OP_DIV; }
"%"                  { return SqlTokens.SQL_OP_MODULO; }
"="                  { return SqlTokens.SQL_OP_EQ; }
"<"                  { return SqlTokens.SQL_OP_LT; }
">"                  { return SqlTokens.SQL_OP_GT; }
"!"                  { return SqlTokens.SQL_OP_NOT2; }
"|"                  { return SqlTokens.SQL_OP_BITWISE_OR; }
"&"                  { return SqlTokens.SQL_OP_BITWISE_AND; }
"~"                  { return StarRocksElementTypes.STARROCKS_OP_BITWISE_NOT; }
":"                  { return SqlTokens.SQL_COLON; }

{IDENT}              {
                       String word = yytext().toString().toUpperCase(Locale.ROOT);
                       return StarRocksKeywordCatalog.isKeyword(word)
                         ? keyword()
                         : SqlTokens.SQL_IDENT;
                     }

[^]                  { return TokenType.BAD_CHARACTER; }
