package com.jc.starrocks.datagrip.database

import com.intellij.database.dataSource.DatabaseConnectionCore
import com.intellij.database.dialects.base.introspector.jdbc.wrappers.DatabaseMetaDataWrapper
import com.intellij.database.dialects.base.introspector.jdbc.wrappers.ResultSetWrapper
import com.intellij.database.dialects.base.introspector.jdbc.wrappers.TableIt
import com.intellij.database.dialects.generic.introspector.jdbc.GenericMetadataWrapper
import com.intellij.database.remote.jdbc.RemoteDatabaseMetaData
import com.intellij.database.remote.jdbc.helpers.JdbcNativeUtil
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Marks StarRocks materialized views with the JDBC table type "MATERIALIZED VIEW".
 *
 * The StarRocks FE reports async materialized views through JDBC metadata as plain `VIEW` rows,
 * so the stock introspector files them under Views. Rewriting the row type inside the platform's
 * own [TableIt.fillValue] hook makes `JdbcTableType.MATERIALIZED_VIEW -> ObjectKind.MAT_VIEW`
 * kick in, routing the object into the reused PostgreSQL model's materialized-view family (own
 * tree group + MV icon + Refresh MV action) without touching the introspector itself.
 *
 * Deliberately NOT a custom ClosableIt wrapper: this JBIterator build passes `nextImpl()` results
 * straight into the base `tables()` TEMPORARY filter lambda without a null check, and does not
 * catch NoSuchElementException either — both custom terminations abort the whole tables pass.
 * Subclassing TableIt keeps every iteration/termination semantics platform-owned.
 *
 * Membership is decided by `information_schema.materialized_views` (async MVs only; sync MVs are
 * base tables to JDBC and stay where they are). Any query failure degrades to the stock behaviour
 * instead of breaking introspection.
 */
class StarRocksMetadataWrapper(
    connection: DatabaseConnectionCore,
    metaData: RemoteDatabaseMetaData,
) : GenericMetadataWrapper(connection, metaData) {

    /** dbName (lowercased) -> MV names; null = query failed, don't retry within this wrapper. */
    private val mvNameCache = ConcurrentHashMap<String, Set<String>?>()

    /**
     * JdbcIntrospector derives its introspected table-kind set from these types; StarRocks'
     * getTableTypes() lacks "MATERIALIZED VIEW", which would drop the MAT_VIEW family from
     * column introspection entirely. Adding it here lights up MV columns.
     */
    override fun getAllTableTypes(): Array<String> {
        val base = super.getAllTableTypes()
        if (base.any { it.equals(MATERIALIZED_VIEW_TYPE, ignoreCase = true) }) return base
        return base + MATERIALIZED_VIEW_TYPE
    }

    override fun createTableIt(
        schema: DatabaseMetaDataWrapper.Schema,
        resultSet: ResultSetWrapper,
        databaseIsSchema: Boolean,
    ): TableIt {
        val mvNames = materializedViewNames(schema)
        if (mvNames.isEmpty()) return super.createTableIt(schema, resultSet, databaseIsSchema)
        return object : TableIt(resultSet, databaseIsSchema, schema) {
            override fun fillValue(table: DatabaseMetaDataWrapper.Table): Boolean {
                val hasValue = super.fillValue(table)
                if (hasValue &&
                    table.type != null && table.type.equals("VIEW", ignoreCase = true) &&
                    table.name?.lowercase() in mvNames
                ) {
                    table.type = MATERIALIZED_VIEW_TYPE
                }
                return hasValue
            }
        }
    }

    private fun materializedViewNames(schema: DatabaseMetaDataWrapper.Schema): Set<String> {
        val dbName = schema.schema ?: return emptySet()
        val key = dbName.lowercase()
        mvNameCache[key]?.let { return it }
        val safeName = dbName.replace("'", "''")
        val names: Set<String> = try {
            JdbcNativeUtil.computeRemote {
                val found = HashSet<String>()
                val statement = myConnection.remoteConnection.createStatement()
                try {
                    val rs = statement.executeQuery(
                        "SELECT TABLE_NAME FROM information_schema.materialized_views" +
                            " WHERE TABLE_SCHEMA = '$safeName'"
                    )
                    try {
                        while (rs.next()) {
                            rs.getString(1)?.let { found.add(it.lowercase()) }
                        }
                    } finally {
                        rs.close()
                    }
                } finally {
                    JdbcNativeUtil.closeRemoteStatementSafe(statement)
                }
                found
            } ?: emptySet()
        } catch (t: Throwable) {
            LOG.info("StarRocks MV membership query failed for '$dbName': ${t.message}")
            emptySet()
        }
        mvNameCache[key] = names
        return names
    }

    companion object {
        /** Matches JdbcTableType.MATERIALIZED_VIEW title. */
        const val MATERIALIZED_VIEW_TYPE = "MATERIALIZED VIEW"
        private val LOG = logger<StarRocksMetadataWrapper>()
    }
}

/** `<jdbcMetadataWrapper dbms="STARROCKS">` factory. */
class StarRocksMetadataWrapperFactory : DatabaseMetaDataWrapper.MDFactory() {
    override fun create(
        connection: DatabaseConnectionCore,
        metaData: RemoteDatabaseMetaData,
    ): DatabaseMetaDataWrapper = StarRocksMetadataWrapper(connection, metaData)
}
