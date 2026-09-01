package com.jc.starrocks.datagrip.lang

import com.jc.starrocks.datagrip.dialect.StarRocksDialect
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IFileElementType
import com.intellij.sql.dialects.base.SqlElementFactoryBase
import com.intellij.sql.dialects.base.SqlParserDefinitionBase
import com.intellij.sql.psi.stubs.elementTypes.SqlFileElementType

class StarRocksParserDefinition : SqlParserDefinitionBase() {
    override fun createElementFactory(): SqlElementFactoryBase = StarRocksElementFactory()

    override fun createLexer(project: Project?): Lexer {
        StarRocksTokenInitializer.ensureInitialized()
        return StarRocksParserLexer()
    }

    override fun createParser(project: Project?): PsiParser = StarRocksParser()

    override fun getFileNodeType(): IFileElementType = STARROCKS_SQL_FILE

    companion object {
        val STARROCKS_SQL_FILE: SqlFileElementType =
            SqlFileElementType("STARROCKS_SQL_FILE", StarRocksDialect.INSTANCE)
    }
}
