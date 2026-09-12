package com.jc.starrocks.datagrip.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Pure-logic tests for [StarRocksMatViewDdl] (no IDE fixture needed): name parsing out of
 * CREATE statements, name rewriting, temp-name generation and the swap statement sequence.
 */
class StarRocksMatViewDdlTest {

    // --- parseName ---

    @Test
    fun parseNamePlain() {
        val parsed = StarRocksMatViewDdl.parseName("CREATE MATERIALIZED VIEW mv_test AS SELECT 1")
        assertEquals(StarRocksMatViewDdl.ParsedName(null, "mv_test"), parsed)
    }

    @Test
    fun parseNameBackticked() {
        val parsed = StarRocksMatViewDdl.parseName("CREATE MATERIALIZED VIEW `mv_test` AS SELECT 1")
        assertEquals(StarRocksMatViewDdl.ParsedName(null, "mv_test"), parsed)
    }

    @Test
    fun parseNameQualified() {
        val parsed = StarRocksMatViewDdl.parseName("CREATE MATERIALIZED VIEW `dwd`.`mv_sales` AS SELECT 1")
        assertEquals(StarRocksMatViewDdl.ParsedName("dwd", "mv_sales"), parsed)
    }

    @Test
    fun parseNameUnqualifiedQualifiedMix() {
        val parsed = StarRocksMatViewDdl.parseName("CREATE MATERIALIZED VIEW dwd.mv_sales AS SELECT 1")
        assertEquals(StarRocksMatViewDdl.ParsedName("dwd", "mv_sales"), parsed)
    }

    @Test
    fun parseNameIfNotExists() {
        val parsed = StarRocksMatViewDdl.parseName("CREATE MATERIALIZED VIEW IF NOT EXISTS mv_test AS SELECT 1")
        assertEquals(StarRocksMatViewDdl.ParsedName(null, "mv_test"), parsed)
    }

    @Test
    fun parseNameOrReplace() {
        val parsed = StarRocksMatViewDdl.parseName("CREATE OR REPLACE MATERIALIZED VIEW mv_test AS SELECT 1")
        assertEquals(StarRocksMatViewDdl.ParsedName(null, "mv_test"), parsed)
    }

    @Test
    fun parseNameEscapedBacktickInsideName() {
        val parsed = StarRocksMatViewDdl.parseName("CREATE MATERIALIZED VIEW `my``mv` AS SELECT 1")
        assertEquals(StarRocksMatViewDdl.ParsedName(null, "my`mv"), parsed)
    }

    @Test
    fun parseNameStarRocksShowCreateShape() {
        // Typical `SHOW CREATE MATERIALIZED VIEW` output shape, including a leading dot in the
        // qualified name and REFRESH/COMMENT clauses between the name and the query.
        val ddl = "CREATE MATERIALIZED VIEW `dwd`.`mv_sales`\n" +
            "REFRESH ASYNC COMMENT 'sales mv'\n" +
            "AS SELECT shop_id, SUM(amount) FROM dwd_orders GROUP BY shop_id"
        assertEquals(StarRocksMatViewDdl.ParsedName("dwd", "mv_sales"), StarRocksMatViewDdl.parseName(ddl))
    }

    @Test
    fun parseNameCaseInsensitive() {
        val parsed = StarRocksMatViewDdl.parseName("create materialized view mv_test as select 1")
        assertEquals(StarRocksMatViewDdl.ParsedName(null, "mv_test"), parsed)
    }

    @Test
    fun parseNameRejectsNonMaterializedView() {
        assertNull(StarRocksMatViewDdl.parseName("CREATE VIEW v_test AS SELECT 1"))
        assertNull(StarRocksMatViewDdl.parseName("ALTER MATERIALIZED VIEW mv_test SWAP WITH mv_old"))
        assertNull(StarRocksMatViewDdl.parseName("DROP MATERIALIZED VIEW mv_test"))
    }

    @Test
    fun parseNameRejectsGarbage() {
        assertNull(StarRocksMatViewDdl.parseName("not sql at all"))
        assertNull(StarRocksMatViewDdl.parseName(""))
    }

    // --- rewriteName ---

    @Test
    fun rewriteNamePlainToQualifiedTemp() {
        val ddl = "CREATE MATERIALIZED VIEW mv_test AS SELECT 1"
        val rewritten = StarRocksMatViewDdl.rewriteName(ddl, "`dwd`.`mv_test_20260715123045`")
        assertEquals("CREATE MATERIALIZED VIEW `dwd`.`mv_test_20260715123045` AS SELECT 1", rewritten)
        // Reparses to the new (schema-qualified) name.
        assertEquals(
            StarRocksMatViewDdl.ParsedName("dwd", "mv_test_20260715123045"),
            rewritten?.let(StarRocksMatViewDdl::parseName)
        )
    }

    @Test
    fun rewriteNameQualifiedReplacesBothSegments() {
        val ddl = "CREATE MATERIALIZED VIEW `dwd`.`mv_sales` REFRESH ASYNC AS SELECT 1"
        val rewritten = StarRocksMatViewDdl.rewriteName(ddl, "`dwd`.`mv_sales_20260715123045`")
        assertEquals("CREATE MATERIALIZED VIEW `dwd`.`mv_sales_20260715123045` REFRESH ASYNC AS SELECT 1", rewritten)
    }

    @Test
    fun rewriteNameKeepsPrefixAndBody() {
        val ddl = "CREATE MATERIALIZED VIEW `dwd`.`mv_sales` REFRESH ASYNC COMMENT 'x' AS SELECT a FROM t"
        val rewritten = StarRocksMatViewDdl.rewriteName(ddl, "`dwd`.`mv_new`")
            ?: error("expected a rewrite")
        assertTrue(rewritten.startsWith("CREATE MATERIALIZED VIEW `dwd`.`mv_new` REFRESH"))
        assertTrue(rewritten.endsWith("AS SELECT a FROM t"))
    }

    @Test
    fun rewriteNameReturnsNullWithoutMatch() {
        assertNull(StarRocksMatViewDdl.rewriteName("ALTER MATERIALIZED VIEW a SWAP WITH b", "`x`.`y`"))
    }

    // --- generateTempName ---

    @Test
    fun generateTempNameFixedClock() {
        val now = LocalDateTime.of(2026, 7, 15, 12, 30, 45)
        assertEquals("mv_test_20260715123045", StarRocksMatViewDdl.generateTempName("mv_test", now))
    }

    @Test
    fun generateTempNameAppendsSecondsPrecisionSuffix() {
        val now = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
        val temp = StarRocksMatViewDdl.generateTempName("mv", now)
        assertEquals("mv_20260102030405", temp)
    }

    @Test
    fun generateTempNameQualifiedAppliesToLastSegment() {
        val now = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
        assertEquals("db.mv_20260102030405", StarRocksMatViewDdl.generateTempName("db.mv", now))
    }

    // --- buildSwapStatements ---

    @Test
    fun buildSwapStatementsQuotedSequence() {
        val statements = StarRocksMatViewDdl.buildSwapStatements("`dwd`.`mv_old`", "`dwd`.`mv_old_20260715123045`")
        assertEquals(
            listOf(
                // SWAP takes bare names only (same database); DROP keeps the qualified name.
                "ALTER MATERIALIZED VIEW `mv_old_20260715123045` SWAP WITH `mv_old`",
                "DROP MATERIALIZED VIEW `dwd`.`mv_old_20260715123045`",
            ),
            statements
        )
    }

    // --- end-to-end shape ---

    @Test
    fun fullFlowRewritesToTempAndReparses() {
        val ddl = "CREATE MATERIALIZED VIEW `dwd`.`mv_sales`\n" +
            "REFRESH ASYNC COMMENT 'sales mv'\n" +
            "AS SELECT shop_id, SUM(amount) FROM dwd_orders GROUP BY shop_id"
        val parsed = StarRocksMatViewDdl.parseName(ddl) ?: error("expected a parse")
        val now = LocalDateTime.of(2026, 7, 15, 12, 30, 45)
        val temp = StarRocksMatViewDdl.generateTempName(parsed.name, now)
        val createStmt = StarRocksMatViewDdl.rewriteName(ddl, "`dwd`.`$temp`") ?: error("expected a rewrite")
        // The rewritten statement is a valid CREATE for the temp view.
        assertEquals(
            StarRocksMatViewDdl.ParsedName("dwd", "mv_sales_20260715123045"),
            StarRocksMatViewDdl.parseName(createStmt)
        )
        // Swap sequence references both the new (temp) and the original name, drops the temp.
        val swap = StarRocksMatViewDdl.buildSwapStatements("`dwd`.`mv_sales`", "`dwd`.`$temp`")
        assertEquals(2, swap.size)
        assertTrue(swap[0].contains("SWAP WITH `mv_sales`"))
        assertEquals("DROP MATERIALIZED VIEW `dwd`.`mv_sales_20260715123045`", swap[1])
    }
}
