package com.jc.starrocks.datagrip.database

import com.intellij.database.dataSource.DatabaseConnectionCore
import com.intellij.database.dialects.AbstractDefinitionProvider
import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.DasUtil
import com.intellij.util.PairConsumer

class StarRocksDefinitionProvider : AbstractDefinitionProvider() {
    override fun isSupported(obj: DasObject): Boolean {
        return obj.kind in SUPPORTED_KINDS
    }

    override fun fetchSources(
        objects: Iterable<DasObject>,
        connection: DatabaseConnectionCore,
        consumer: PairConsumer<DasObject, Any>
    ) {
        val remoteConnection = connection.remoteConnection
        val statement = remoteConnection.createStatement()
        try {
            objects.filter(::isSupported).forEach { obj ->
                try {
                    val statementText = StarRocksDdlStatements.showCreateStatement(obj.kind, qualifiedName(obj))
                    val resultSet = statement.executeQuery(statementText)
                    try {
                        consumer.consume(obj, if (resultSet.next()) extractDefinition(resultSet) else "")
                    } finally {
                        resultSet.close()
                    }
                } catch (t: Throwable) {
                    consumer.consume(obj, t)
                }
            }
        } finally {
            statement.close()
        }
    }

    /**
     * SHOW CREATE result columns differ per object kind (`Create Table`, `Create View`,
     * `Create Materialized View`, ...): prefer a column whose label mentions "create" or a value
     * starting with CREATE, then fall back to the second / first non-blank column.
     */
    private fun extractDefinition(resultSet: com.intellij.database.remote.jdbc.RemoteResultSet): String {
        val meta = resultSet.metaData
        var secondColumn: String? = null
        var fallback: String? = null
        for (i in 1..meta.columnCount) {
            val value = resultSet.getString(i) ?: continue
            if (fallback == null) fallback = value
            if (i == 2 && secondColumn == null) secondColumn = value
            if (meta.getColumnLabel(i).contains("create", ignoreCase = true) || value.startsWith("CREATE")) {
                return value
            }
        }
        return secondColumn ?: fallback ?: ""
    }

    private fun qualifiedName(obj: DasObject): String {
        val schema = DasUtil.getSchema(obj).takeIf { it.isNotBlank() }
        val catalog = DasUtil.getCatalog(obj).takeIf { it.isNotBlank() && it != schema }
        return listOfNotNull(catalog, schema, obj.name)
            .joinToString(".") { quoteIdentifier(it) }
    }

    companion object {
        private val SUPPORTED_KINDS: Set<ObjectKind> = setOf(
            ObjectKind.TABLE,
            ObjectKind.VIEW,
            ObjectKind.MAT_VIEW
        )

        fun quoteIdentifier(identifier: String): String {
            if (identifier.startsWith("`") && identifier.endsWith("`")) {
                return identifier
            }
            return "`" + identifier.replace("`", "``") + "`"
        }
    }
}

object StarRocksDdlStatements {
    fun showCreateStatement(kind: ObjectKind, qualifiedName: String): String {
        return when (kind) {
            ObjectKind.MAT_VIEW -> "SHOW CREATE MATERIALIZED VIEW $qualifiedName"
            ObjectKind.VIEW -> "SHOW CREATE VIEW $qualifiedName"
            else -> "SHOW CREATE TABLE $qualifiedName"
        }
    }
}
