package com.jc.starrocks.datagrip.database

import com.intellij.database.model.ObjectKind
import com.intellij.database.util.DbImplUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StarRocksDatabaseDialectTest : BasePlatformTestCase() {
    fun testStarRocksDatabaseDialectIsRegistered() {
        assertTrue(
            DbImplUtilCore.getDatabaseDialect(StarRocksDbms.INSTANCE) is StarRocksDatabaseDialect
        )
    }

    fun testConnectionNamespaceLoadsWithoutSelectedEditorSchema() {
        val queries = mutableListOf<String>()
        val searchPath = StarRocksDatabaseDialect.loadSearchPath { sql ->
            queries += sql
            when (sql) {
                StarRocksDatabaseDialect.CURRENT_CATALOG_QUERY -> "default_catalog"
                StarRocksDatabaseDialect.CURRENT_DATABASE_QUERY -> "dwd"
                else -> error("Unexpected query: $sql")
            }
        }

        assertEquals(
            listOf(
                StarRocksDatabaseDialect.CURRENT_CATALOG_QUERY,
                StarRocksDatabaseDialect.CURRENT_DATABASE_QUERY,
            ),
            queries,
        )
        assertNotNull(searchPath)
        assertEquals(ObjectKind.SCHEMA, searchPath!!.current.kind)
        assertEquals("dwd", searchPath.current.name)
        assertEquals(ObjectKind.DATABASE, searchPath.current.parent.kind)
        assertEquals("default_catalog", searchPath.current.parent.name)
    }

    fun testConnectionNamespaceKeepsCatalogWhenDatabaseIsUnset() {
        val searchPath = StarRocksDatabaseDialect.loadSearchPath { sql ->
            when (sql) {
                StarRocksDatabaseDialect.CURRENT_CATALOG_QUERY -> "default_catalog"
                StarRocksDatabaseDialect.CURRENT_DATABASE_QUERY -> null
                else -> error("Unexpected query: $sql")
            }
        }

        assertNotNull(searchPath)
        assertEquals(ObjectKind.DATABASE, searchPath!!.current.kind)
        assertEquals("default_catalog", searchPath.current.name)
    }

    fun testUnrelatedGenericDialectBehaviorIsPreserved() {
        val dialect = StarRocksDatabaseDialect()

        assertEquals("Generic SQL", dialect.displayName)
        assertNull(dialect.searchPathObjectKind)
        assertFalse(dialect.supportsCommonTableExpression())
        assertTrue(dialect.shouldSwitchThroughJdbc(ObjectKind.SCHEMA))
    }

    fun testMissingCatalogDoesNotInventAPath() {
        val queries = mutableListOf<String>()
        val searchPath = StarRocksDatabaseDialect.loadSearchPath { sql ->
            queries += sql
            null
        }

        assertNull(searchPath)
        assertEquals(listOf(StarRocksDatabaseDialect.CURRENT_CATALOG_QUERY), queries)
    }
}
