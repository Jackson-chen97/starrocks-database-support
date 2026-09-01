package com.jc.starrocks.datagrip.format

import com.jc.starrocks.datagrip.dialect.StarRocksDialect
import com.intellij.sql.dialects.base.BaseSqlDialectCodeStyleProvider

class StarRocksCodeStyleProvider : BaseSqlDialectCodeStyleProvider<StarRocksCodeStyleSettings>(
    StarRocksDialect.INSTANCE,
    StarRocksCodeStyleSettings::class.java,
    "StarRocks"
)
