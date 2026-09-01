package com.jc.starrocks.datagrip.lang

import com.jc.starrocks.datagrip.dialect.StarRocksDialect
import com.intellij.psi.tree.IElementType

class StarRocksElementType(debugName: String) : IElementType(debugName, StarRocksDialect.INSTANCE)

