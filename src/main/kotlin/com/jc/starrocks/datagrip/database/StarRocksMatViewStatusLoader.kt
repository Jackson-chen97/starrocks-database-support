package com.jc.starrocks.datagrip.database

import com.intellij.database.dataSource.DatabaseConnectionCore
import com.intellij.database.dataSource.DatabaseConnectionInterceptor
import com.intellij.database.remote.jdbc.RemoteResultSet
import com.intellij.database.remote.jdbc.helpers.JdbcNativeUtil
import com.intellij.openapi.diagnostic.logger

/**
 * `<database.connectionInterceptor>` — refreshes the MV activity-state cache on every
 * StarRocks connection.
 *
 * `interceptConnection` gated to dbms=STARROCKS makes the platform call [handleConnected] after
 * the JDBC connection is established (same seam Doris uses for its trace-id binding). The state
 * of async materialized views is read from `information_schema.materialized_views` (one query
 * covers all databases); views missing from it (sync MVs, older servers) simply stay uncached
 * and render without a status suffix.
 */
class StarRocksMatViewStatusLoader : DatabaseConnectionInterceptor {

    override suspend fun interceptConnection(
        proto: DatabaseConnectionInterceptor.ProtoConnection,
        silent: Boolean,
    ): Boolean = proto.connectionPoint.dbms == StarRocksDbms.INSTANCE

    override suspend fun handleConnected(
        connection: DatabaseConnectionCore,
        proto: DatabaseConnectionInterceptor.ProtoConnection,
    ) {
        if (connection.dbms != StarRocksDbms.INSTANCE) return
        try {
            JdbcNativeUtil.performRemote {
                val statement = connection.remoteConnection.createStatement()
                try {
                    val rs: RemoteResultSet? = try {
                        statement.executeQuery(QUERY)
                    } catch (_: Exception) {
                        null
                    }
                    try {
                        if (rs != null) readStates(rs)
                    } finally {
                        rs?.close()
                    }
                } finally {
                    JdbcNativeUtil.closeRemoteStatementSafe(statement)
                }
                Unit
            }
        } catch (t: Throwable) {
            LOG.info("MV state refresh skipped: ${t.message}")
        }
    }

    private fun readStates(rs: RemoteResultSet) {
        val meta = rs.metaData
        val cols = (1..meta.columnCount).associateWith { meta.getColumnLabel(it).lowercase() }
        val schemaCol = cols.entries.firstOrNull { it.value.contains("schema") }?.key
        val nameCol = cols.entries.firstOrNull {
            it.value.contains("name") && !it.value.contains("refresh") && !it.value.contains("partition")
        }?.key
        val activeCol = cols.entries.firstOrNull { it.value == "is_active" || it.value == "isactive" }?.key
        if (schemaCol == null || nameCol == null || activeCol == null) return
        val states = HashMap<String, MatViewState>()
        while (rs.next()) {
            val schema = rs.getString(schemaCol) ?: continue
            val name = rs.getString(nameCol) ?: continue
            val raw = rs.getString(activeCol)
            val active = parseActive(raw)
            states[schema.lowercase() + "." + name.lowercase()] =
                MatViewState(active, if (active) null else raw)
        }
        StarRocksMatViewStatus.replaceAll(states)
    }

    private fun parseActive(raw: String?): Boolean {
        if (raw == null) return true
        val v = raw.trim().lowercase()
        return v != "false" && v != "0" && v != "inactive" && v != "invalid"
    }

    private companion object {
        val LOG = logger<StarRocksMatViewStatusLoader>()
        const val QUERY =
            "SELECT TABLE_SCHEMA, TABLE_NAME, IS_ACTIVE FROM information_schema.materialized_views"
    }
}
