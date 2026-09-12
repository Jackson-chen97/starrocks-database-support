package com.jc.starrocks.datagrip.database

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Pure (no IDE dependency) parsing and rewriting of a StarRocks materialized-view CREATE
 * statement. The Modify action uses this to locate the view name in user-edited DDL, to
 * generate the temporary target name for the swap flow, and to build the swap sequence
 * (create-as-temp → `ALTER ... SWAP WITH` → drop temp).
 *
 * The name regex accepts the two forms StarRocks emits from `SHOW CREATE MATERIALIZED VIEW`
 * and a user may write by hand:
 * - `CREATE MATERIALIZED VIEW [IF NOT EXISTS] name AS SELECT ...`
 * - `CREATE MATERIALIZED VIEW [IF NOT EXISTS] db.name AS SELECT ...`
 * with each segment either plain (`[A-Za-z0-9_$]`) or backtick-quoted; `OR REPLACE` is
 * tolerated but never emitted by StarRocks.
 */
object StarRocksMatViewDdl {

    /**
     * One identifier segment: backtick-quoted (doubled backticks are the escape, per MySQL/
     * StarRocks identifier rules) or plain.
     */
    private const val SEGMENT = "(?:`(?:[^`]|``)*`|[A-Za-z0-9_\\$]+)"

    private val NAME_REGEX = Regex(
        "(?is)\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?MATERIALIZED\\s+VIEW\\s+" +
            "(?:IF\\s+NOT\\s+EXISTS\\s+)?" +
            "($SEGMENT)(?:\\.\\s*($SEGMENT))?"
    )

    /** The view name in a CREATE statement, unquoted; [schema] is null when unqualified. */
    data class ParsedName(val schema: String?, val name: String)

    fun parseName(ddl: String): ParsedName? {
        val match = NAME_REGEX.find(ddl) ?: return null
        val first = unquote(match.groupValues[1])
        val second = match.groupValues[2]
        return if (second.isBlank()) {
            ParsedName(null, first)
        } else {
            ParsedName(first, unquote(second))
        }
    }

    /**
     * Replaces the view name in [ddl] with [newName] (already quoted, optionally
     * schema-qualified, e.g. `` `db`.`mv` ``). Returns null when no name is found.
     */
    fun rewriteName(ddl: String, newName: String): String? {
        val match = NAME_REGEX.find(ddl) ?: return null
        val first = match.groups[1] ?: return null
        val second = match.groups[2]
        // Replace only the name span (group 1 plus group 2 when qualified) — not the
        // `CREATE MATERIALIZED VIEW` prefix, which must be preserved.
        val nameRange = if (second != null) {
            IntRange(first.range.first, second.range.last)
        } else {
            first.range
        }
        return ddl.replaceRange(nameRange, newName)
    }

    /** Temporary target name for the swap flow: `<name>_<yyyyMMddHHmmss>` on the last segment only. */
    fun generateTempName(name: String, now: LocalDateTime = LocalDateTime.now()): String {
        val suffix = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val lastSegment = name.substringAfterLast('.')
        return if (lastSegment == name) {
            "${name}_${suffix}"
        } else {
            "${name.substringBeforeLast('.')}.${lastSegment}_$suffix"
        }
    }

    /**
     * Swap sequence for when the temp view has been created: step 1 swaps the new definition
     * in under the original name, step 2 drops the leftover temp. [oldName] and [newName] are
     * already quoted (optionally qualified).
     *
     * SWAP takes plain names only — StarRocks resolves both views within the statement's current
     * database and rejects qualified operands ("Unexpected input '.'"), so the qualification is
     * stripped from both sides. DROP accepts qualified names.
     */
    fun buildSwapStatements(oldName: String, newName: String): List<String> {
        return listOf(
            "ALTER MATERIALIZED VIEW ${bare(newName)} SWAP WITH ${bare(oldName)}",
            "DROP MATERIALIZED VIEW $newName",
        )
    }

    private fun bare(quoted: String): String = quoted.substringAfterLast('.')

    private fun unquote(segment: String): String {
        val trimmed = segment.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("`") && trimmed.endsWith("`")) {
            return trimmed.substring(1, trimmed.length - 1).replace("``", "`")
        }
        return trimmed
    }
}
