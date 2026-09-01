package com.jc.starrocks.datagrip.database

import com.intellij.database.dataSource.DataSourceModelStorage
import com.intellij.database.dataSource.DataSourceStorage
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.DasModel
import com.intellij.database.model.serialization.ModelImporter
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Clears persisted model files whose shape predates the PostgreSQL-family meta-model.
 *
 * Models cached by earlier releases use the flat `ROOT -> SCHEMA` shape (generic family); the
 * reused Pg meta-model is `ROOT -> DATABASE -> SCHEMA -> {tables, views, matViews}` and cannot
 * cross-load them (ModelImporter drops orphaned families / throws). This application listener
 * fires synchronously before any model file is read, so sniffing the first root child is enough:
 * a `<schema parent="1">` head means a stale flat file, which is deleted for a silent rebuild on
 * the next connect/refresh. Only StarRocks data sources are touched.
 */
class StarRocksModelMigrationListener : DataSourceModelStorage.Listener {

    override fun started(project: Project?) {
        project ?: return
        try {
            val storageDir = DataSourceStorage.getStorageDir(project) ?: return
            for (dataSource in DataSourceStorage.getProjectStorage(project).dataSources) {
                if (dataSource.dbms != StarRocksDbms.INSTANCE) continue
                val uniqueId = dataSource.uniqueId ?: continue
                val modelXml = Paths.get(storageDir, "$uniqueId.xml")
                val head = readHead(modelXml) ?: continue
                val shape = StarRocksModelShape.sniff(head) ?: continue
                if (shape == StarRocksModelShape.Shape.PG_MULTI_LEVEL) continue
                Files.deleteIfExists(modelXml)
                deleteRecursively(Paths.get(storageDir, uniqueId, "entities"))
                LOG.info("stale flat model shape cleared for silent rebuild (data source '${dataSource.name}')")
            }
        } catch (t: Throwable) {
            LOG.warn("model-shape migration check failed: ${t.message}")
        }
    }

    private fun readHead(path: Path): String? = try {
        if (!Files.isRegularFile(path)) null
        else Files.newInputStream(path).use { String(it.readNBytes(StarRocksModelShape.SNIFF_LIMIT_BYTES), Charsets.UTF_8) }
    } catch (_: Throwable) {
        null
    }

    private fun deleteRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { p ->
                try {
                    Files.deleteIfExists(p)
                } catch (_: Throwable) {
                }
            }
        }
    }

    override fun finished(project: Project?) = Unit
    override fun started(project: Project?, dataSource: LocalDataSource) = Unit
    override fun finished(
        project: Project?,
        dataSource: LocalDataSource,
        model: DasModel,
        importer: ModelImporter,
    ) = Unit

    override fun failed(project: Project?, dataSource: LocalDataSource, error: Throwable?) = Unit

    private companion object {
        val LOG = logger<StarRocksModelMigrationListener>()
    }
}

object StarRocksModelShape {
    const val SNIFF_LIMIT_BYTES: Int = 8192

    enum class Shape { PG_MULTI_LEVEL, FLAT }

    private val FIRST_ROOT_CHILD = Regex("""<(database|schema)\b[^>]*\bparent="1"""")

    fun sniff(head: String?): Shape? {
        if (head.isNullOrBlank()) return null
        val match = FIRST_ROOT_CHILD.find(head) ?: return null
        return if (match.groupValues[1] == "database") Shape.PG_MULTI_LEVEL else Shape.FLAT
    }
}
