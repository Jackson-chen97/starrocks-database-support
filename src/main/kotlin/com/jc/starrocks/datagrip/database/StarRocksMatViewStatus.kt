package com.jc.starrocks.datagrip.database

import com.intellij.database.model.DasObject
import com.intellij.database.util.DasUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-datasource cache of StarRocks materialized-view activity state.
 *
 * Filled by [StarRocksMatViewStatusLoader] after MV introspection from
 * `information_schema.materialized_views` / `SHOW MATERIALIZED VIEWS`, read by
 * [StarRocksDescriptionService.options] to suffix invalid MVs in the tree.
 */
data class MatViewState(val isActive: Boolean, val reason: String?)

object StarRocksMatViewStatus {
    // key: lowercase "db.mv_name" (StarRocks MV names are scoped by database within a connection)
    private val states = ConcurrentHashMap<String, MatViewState>()

    fun put(database: String, name: String, state: MatViewState) {
        states[key(database, name)] = state
    }

    /** Full refresh from a `schema -> (mv name -> state)` snapshot. */
    fun replaceAll(newStates: Map<String, MatViewState>) {
        states.clear()
        newStates.forEach { (name, state) -> states[name.lowercase()] = state }
    }

    fun get(element: DasObject): MatViewState? {
        val schema = DasUtil.getSchema(element) ?: return null
        return states[key(schema, element.name)]
            ?: DasUtil.getCatalog(element)?.takeIf { it.isNotBlank() }?.let { states[key(it, element.name)] }
    }

    fun clear() = states.clear()

    private fun key(scope: String, name: String): String = scope.lowercase() + "." + name.lowercase()
}
