package com.jc.starrocks.datagrip.lang

import com.intellij.sql.util.SqlTokenRegistry

object StarRocksTokenInitializer {
    private val initialized = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SqlTokenRegistry.ensureInterfacesAreInitializedInOrder(StarRocksTokens::class.java)
    }

    fun ensureInitialized() {
        initialized.value
    }
}
