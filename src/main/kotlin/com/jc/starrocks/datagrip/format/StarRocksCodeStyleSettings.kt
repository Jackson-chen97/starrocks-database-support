package com.jc.starrocks.datagrip.format

import com.jc.starrocks.datagrip.dialect.StarRocksDialect
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.sql.formatter.settings.SqlCodeStyleSettings

class StarRocksCodeStyleSettings(container: CodeStyleSettings) : SqlCodeStyleSettings("StarRocksCodeStyleSettings", container) {
    override fun getCorrespondedDialect(): Language = StarRocksDialect.INSTANCE
}
