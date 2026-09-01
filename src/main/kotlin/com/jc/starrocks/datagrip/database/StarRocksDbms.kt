package com.jc.starrocks.datagrip.database

import com.jc.starrocks.datagrip.StarRocksIcons
import com.intellij.database.Dbms

class StarRocksDbms private constructor() {
    companion object {
        @JvmField
        val INSTANCE: Dbms = Dbms.create("STARROCKS", "StarRocks", { StarRocksIcons.DataSource })
    }
}
