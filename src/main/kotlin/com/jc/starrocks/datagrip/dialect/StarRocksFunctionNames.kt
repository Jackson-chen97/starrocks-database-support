package com.jc.starrocks.datagrip.dialect

import javax.xml.parsers.DocumentBuilderFactory

internal object StarRocksFunctionNames {
    val NAMES: Set<String> by lazy {
        val resource = StarRocksDialect::class.java.getResourceAsStream("functions.xml")
            ?: error("Missing StarRocks platform function catalog: functions.xml")
        resource.use { input ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
            val names = document.getElementsByTagName("name")
            buildSet {
                for (index in 0 until names.length) {
                    names.item(index).textContent.trim().takeIf(String::isNotEmpty)?.let(::add)
                }
            }
        }
    }
}
