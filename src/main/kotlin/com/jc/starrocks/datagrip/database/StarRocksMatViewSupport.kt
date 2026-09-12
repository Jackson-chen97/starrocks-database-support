package com.jc.starrocks.datagrip.database

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.model.DasObject
import com.intellij.database.model.RawDataSource
import com.intellij.database.model.ObjectKind
import com.intellij.database.model.basic.BasicElement
import com.intellij.database.model.basic.BasicRoot
import com.intellij.database.psi.DbElement
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.remote.jdbc.helpers.JdbcNativeUtil
import com.intellij.database.util.DasUtil
import com.intellij.database.view.DatabaseView
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Shared plumbing for the StarRocks materialized-view actions: tree selection, data-source
 * resolution, cook-safe connection acquisition, DDL loading, targeted tree refresh and
 * notifications.
 *
 * Lives here (instead of in [StarRocksMatViewAction]) because the Modify and Drop actions are
 * independent [com.intellij.openapi.actionSystem.AnAction]s that reuse the same helpers rather
 * than subclassing the sealed base.
 */
object StarRocksMatViewSupport {

    const val GROUP = "StarRocks Support"
    private val LOG = Logger.getInstance("starrocks-materialized-view")

    fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP)
            .createNotification(message, type)
            .notify(project)
    }

    fun selectedMatViews(e: AnActionEvent): List<DasObject> =
        e.getData(DatabaseView.DATABASE_ELEMENTS)
            ?.filter { it.kind == ObjectKind.MAT_VIEW }
            ?.filterIsInstance<DasObject>()
            ?: emptyList()

    fun qualified(obj: DasObject): String = quoteParts(schema(obj), obj.name)

    fun schema(obj: DasObject): String = DasUtil.getSchema(obj) ?: ""

    /** Quotes [schema].[name] for DDL use; a blank [schema] yields an unqualified name. */
    fun quoteParts(schema: String?, name: String): String =
        listOfNotNull(schema?.takeIf { it.isNotBlank() }, name)
            .joinToString(".") { StarRocksDefinitionProvider.quoteIdentifier(it) }

    /**
     * Tree elements come from either the PSI tree (DbElement) or the new model tree, where the
     * dasParent chain tops out at a BasicRoot that is NOT a data source. Strategy order:
     * 1. DbElement (PSI) direct accessor;
     * 2. match the BasicRoot against DatabaseView.DATABASE_RELATED_DATA_SOURCES nodes — their
     *    LocalDataSource implements DatabaseConnectionPoint directly;
     * 3. sole StarRocks data source registered in the project.
     */
    fun connectionPoint(project: Project, e: AnActionEvent, obj: DasObject): DatabaseConnectionPoint? {
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

    /**
     * Resolves the connection point for a [DasObject] without an action event context (e.g. an
     * Apply triggered from an editor notification bar): uses the element's own data source when
     * available, then falls back to the project's single StarRocks data source.
     */
    fun connectionPointFor(project: Project, obj: DasObject): DatabaseConnectionPoint? {
        (obj as? DbElement)?.dataSource?.let { ds ->
            (ds.connectionConfig as? DatabaseConnectionPoint)?.let { return it }
            (ds.delegateDataSource as? DatabaseConnectionPoint)?.let { return it }
        }
        return DbPsiFacade.getInstance(project).dataSources
            .filter { it.dbms == StarRocksDbms.INSTANCE }
            .singleOrNull()?.connectionConfig as? DatabaseConnectionPoint
    }

    /**
     * DataGrip executes JDBC in a separate "cook" process; the IDE-side [DatabaseConnection]
     * holds an RMI stub bound to that process's localhost port. Cooks are recycled (project
     * close/reopen, idle shutdown), which leaves cached connections whose stub points at a
     * dead port — using one then throws java.rmi.ConnectException (Connection refused).
     * Probe the stub before trusting it; any failure (dead port, closed server connection)
     * falls through to establishing a fresh connection below.
     */
    fun isRemoteAlive(connection: DatabaseConnection): Boolean =
        try {
            connection.remoteConnection.isValid(1000)
        } catch (t: Throwable) {
            false
        }

    /**
     * Runs [block] with a connection to [point]'s data source, reusing an active connection only
     * while its remote stub is alive (see [isRemoteAlive]); otherwise transparently establishes
     * one (anonymous requestor so stored credentials apply without a dialog).
     * Returns null — with an error notification — when no connection could be obtained.
     * Must be called off the EDT.
     */
    fun <T> withConnection(project: Project, point: DatabaseConnectionPoint, block: (DatabaseConnection) -> T): T? {
        val manager = DatabaseConnectionManager.getInstance()
        val existing = manager.activeConnections.firstOrNull {
            it.connectionPoint?.dataSource?.uniqueId == point.dataSource.uniqueId
        }
        if (existing != null && isRemoteAlive(existing)) {
            return block(existing)
        }
        val ref = manager
            .build(project, point)
            .setRequestor(ConnectionRequestor.Anonymous())
            .createBlockingNonCancellable()
        if (ref == null) {
            notify(project, "Connect failed: no connection available", NotificationType.ERROR)
            return null
        }
        return ref.use { block(it.get()) }
    }

    /**
     * Executes one DDL statement on the data source behind [point]. Returns true on success
     * (connect failures yield false — the failure is already notified by [withConnection]).
     */
    fun executeSql(project: Project, point: DatabaseConnectionPoint, sql: String): Boolean {
        var executed = false
        withConnection(project, point) { connection ->
            JdbcNativeUtil.performRemote {
                val statement = connection.remoteConnection.createStatement()
                try {
                    statement.execute(sql)
                } finally {
                    JdbcNativeUtil.closeRemoteStatementSafe(statement)
                }
                Unit
            }
            executed = true
        }
        return executed
    }

    /**
     * Loads the `SHOW CREATE MATERIALIZED VIEW` DDL of [obj] on its own data source.
     * Returns null when the DDL cannot be obtained (connect failure or SQL error — the provider
     * consumes Throwables for failed statements).
     */
    fun fetchMatViewDdl(project: Project, point: DatabaseConnectionPoint, obj: DasObject): String? =
        withConnection(project, point) { connection ->
            val definition = StarRocksDefinitionProvider().fetchDdl(obj, connection)
            (definition as? String)?.takeIf { it.isNotBlank() }
        }

    /**
     * Best-effort tree refresh after DDL changes. Reflects into `RefreshActionsLogic` (see the
     * class comment on why): `runRegularRefresh(project, dataSource, basicElements)` is the same
     * entry the IDE's Refresh action uses — targeted when the elements are basic-model objects,
     * falling back to `runDataSourceGeneralRefresh` (whole data source) when they are not, e.g.
     * PSI-backed objects coming from a Go-to-DDL editor. Failures are logged only — the user's
     * data is already committed by the time this runs.
     */
    fun refreshParents(project: Project, elements: List<DasObject>) {
        try {
            val logic = Class.forName("com.intellij.database.actions.RefreshActionsLogic")
            val dataSource = elements.firstOrNull()?.let { connectionPointFor(project, it) }?.dataSource
                ?: return
            val basicParents = elements.mapNotNull { it.dasParent }
                .filterIsInstance<BasicElement>()
                .distinct()
            if (basicParents.isNotEmpty()) {
                try {
                    logic.getMethod(
                        "runRegularRefresh", Project::class.java, RawDataSource::class.java, Collection::class.java,
                    ).invoke(null, project, dataSource, basicParents)
                    return
                } catch (t: Throwable) {
                    LOG.warn("Targeted refresh fell back to general refresh", t)
                }
            }
            logic.getMethod("runDataSourceGeneralRefresh", Project::class.java, RawDataSource::class.java)
                .invoke(null, project, dataSource)
        } catch (t: Throwable) {
            LOG.warn("Targeted tree refresh failed", t)
        }
    }
}
