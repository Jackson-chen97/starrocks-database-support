package com.jc.starrocks.datagrip.database

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.model.basic.BasicRoot
import com.intellij.database.psi.DbElement
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.remote.jdbc.helpers.JdbcNativeUtil
import com.intellij.database.util.DasUtil
import com.intellij.database.view.DatabaseView
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

/**
 * Right-click actions on StarRocks materialized views in the database tree:
 * Refresh / Activate / Deactivate.
 *
 * Uses an already-active connection of the SELECTED object's own data source when present;
 * otherwise transparently establishes one (anonymous requestor so stored credentials apply
 * without a dialog). State cache is updated in-place after Activate/Deactivate so the tree
 * suffix renders without a re-introspect.
 */
sealed class StarRocksMatViewAction(
    title: String,
    private val afterState: Boolean?,
    private val sql: (qualified: String) -> String,
) : AnAction(title) {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = selectedMatViews(e).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val objects = selectedMatViews(e)
        if (objects.isEmpty()) return
        // Resolve data sources on the EDT: the event's data context is not valid on pooled threads.
        val targets = objects.mapNotNull { obj -> connectionPoint(project, e, obj)?.let { obj to it } }
        if (targets.isEmpty()) {
            notify(project, "Could not resolve the StarRocks data source of the selection", NotificationType.WARNING)
            return
        }
        if (targets.size < objects.size) {
            notify(project, "Skipped ${objects.size - targets.size} object(s): data source not resolved", NotificationType.WARNING)
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            targets.groupBy { (_, point) -> point.dataSource.uniqueId }.forEach { (_, group) ->
                try {
                    withConnection(project, group.first().second) { connection ->
                        group.forEach { (obj, _) -> runStatement(project, connection, obj, e) }
                    }
                } catch (t: Throwable) {
                    notify(project, "Connect failed: ${t.message}", NotificationType.ERROR)
                }
            }
        }
    }

    private fun runStatement(project: Project, connection: DatabaseConnection, obj: DasObject, e: AnActionEvent) {
        val target = qualified(obj)
        try {
            JdbcNativeUtil.performRemote {
                val statement = connection.remoteConnection.createStatement()
                try {
                    statement.execute(sql(target))
                } finally {
                    JdbcNativeUtil.closeRemoteStatementSafe(statement)
                }
                Unit
            }
            if (afterState != null) {
                StarRocksMatViewStatus.put(schema(obj), obj.name, MatViewState(afterState, if (afterState) null else "altered"))
            }
            notify(project, "${e.presentation.text}: $target — done", NotificationType.INFORMATION)
        } catch (t: Throwable) {
            notify(project, "${e.presentation.text}: $target — failed: ${t.message}", NotificationType.ERROR)
        }
    }

    private fun withConnection(project: Project, point: DatabaseConnectionPoint, block: (DatabaseConnection) -> Unit) {
        val existing = DatabaseConnectionManager.getInstance().activeConnections.firstOrNull {
            it.connectionPoint?.dataSource?.uniqueId == point.dataSource.uniqueId
        }
        if (existing != null) {
            block(existing)
            return
        }
        val ref = DatabaseConnectionManager.getInstance()
            .build(project, point)
            .setRequestor(ConnectionRequestor.Anonymous())
            .createBlockingNonCancellable()
        if (ref == null) {
            notify(project, "Connect failed: no connection available", NotificationType.ERROR)
            return
        }
        ref.use { block(it.get()) }
    }

    /**
     * Tree elements come from either the PSI tree (DbElement) or the new model tree, where the
     * dasParent chain tops out at a BasicRoot that is NOT a data source. Strategy order:
     * 1. DbElement (PSI) direct accessor;
     * 2. match the BasicRoot against DatabaseView.DATABASE_RELATED_DATA_SOURCES nodes — their
     *    LocalDataSource implements DatabaseConnectionPoint directly;
     * 3. sole StarRocks data source registered in the project.
     */
    private fun connectionPoint(project: Project, e: AnActionEvent, obj: DasObject): DatabaseConnectionPoint? {
        (obj as? DbElement)?.dataSource?.let { ds ->
            (ds.connectionConfig as? DatabaseConnectionPoint)?.let { return it }
            // RawDataSource behind the PSI wrapper. getDelegate() is @ApiStatus.Internal;
            // getDelegateDataSource() is the public accessor for the same object.
            (ds.delegateDataSource as? DatabaseConnectionPoint)?.let { return it }
        }
        var root: BasicRoot? = null
        var node: DasObject? = obj
        while (node != null && root == null) {
            if (node is BasicRoot) root = node else node = node.dasParent
        }
        val nodes = e.getData(DatabaseView.DATABASE_RELATED_DATA_SOURCES)?.toList()
            ?: e.getData(DatabaseView.DATABASE_RELATED_SINGLE_DATA_SOURCE)?.let { listOf(it) }
            ?: emptyList()
        nodes.firstOrNull { runCatching { it.modelRoot === root }.getOrDefault(false) }?.let {
            (it.localDataSource as? DatabaseConnectionPoint)?.let { p -> return p }
        }
        nodes.singleOrNull()?.let {
            (it.localDataSource as? DatabaseConnectionPoint)?.let { p -> return p }
        }
        val starRocksSources = DbPsiFacade.getInstance(project).dataSources.filter { it.dbms == StarRocksDbms.INSTANCE }
        return starRocksSources.singleOrNull()?.connectionConfig as? DatabaseConnectionPoint
    }

    private fun selectedMatViews(e: AnActionEvent): List<DasObject> =
        e.getData(DatabaseView.DATABASE_ELEMENTS)
            ?.filter { it.kind == ObjectKind.MAT_VIEW }
            ?.filterIsInstance<DasObject>()
            ?: emptyList()

    private fun qualified(obj: DasObject): String {
        val schema = schema(obj)
        return listOfNotNull(schema.takeIf { it.isNotBlank() }, obj.name)
            .joinToString(".") { StarRocksDefinitionProvider.quoteIdentifier(it) }
    }

    private fun schema(obj: DasObject): String = DasUtil.getSchema(obj) ?: ""

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP)
            .createNotification(message, type)
            .notify(project)
    }

    companion object {
        const val GROUP = "StarRocks Support"
    }
}

class RefreshStarRocksMatViewAction : StarRocksMatViewAction(
    "Refresh Materialized View",
    afterState = null,
    sql = { "REFRESH MATERIALIZED VIEW $it" },
)

class ActivateStarRocksMatViewAction : StarRocksMatViewAction(
    "Activate Materialized View",
    afterState = true,
    sql = { "ALTER MATERIALIZED VIEW $it ACTIVE" },
)

class DeactivateStarRocksMatViewAction : StarRocksMatViewAction(
    "Deactivate Materialized View",
    afterState = false,
    sql = { "ALTER MATERIALIZED VIEW $it INACTIVE" },
)
