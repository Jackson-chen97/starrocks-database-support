package com.jc.starrocks.datagrip.lang

import com.jc.starrocks.datagrip.dialect.StarRocksDialect
import com.intellij.sql.psi.SqlTokenType

object StarRocksCustomTokenTypes {
    @JvmField
    val PARAMETER = SqlTokenType("STARROCKS_PARAMETER", StarRocksDialect.INSTANCE)
}
