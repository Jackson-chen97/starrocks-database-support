package com.jc.starrocks.datagrip.database

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.model.DasObject
import com.intellij.database.remote.jdbc.helpers.JdbcNativeUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Right-click actions on StarRocks materialized views in the database tree:
 * Refresh / Activate / Deactivate / Drop.
 *
 * Reuses an already-active connection of the SELECTED object's own data source only while its
 * remote stub is alive (DataGrip's "cook" process can be recycled, leaving a dead RMI port —
 * see [StarRocksMatViewSupport.isRemoteAlive]); otherwise transparently establishes one
 * (anonymous requestor so stored credentials apply without a dialog). State cache is updated
 * in-place after Activate/Deactivate so the tree suffix renders without a re-introspect.
 */
sealed class StarRocksMatViewAction(
    title: String,
    private val afterState: Boolean?,
    private val sql: (qualified: String) -> String,
) : AnAction(title) {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = StarRocksMatViewSupport.selectedMatViews(e).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val objects = StarRocksMatViewSupport.selectedMatViews(e)
        if (objects.isEmpty()) return
        if (!confirmExecution(project, objects, e)) return
        // Resolve data sources on the EDT: the event's data context is not valid on pooled threads.
        val targets = objects.mapNotNull { obj ->
            StarRocksMatViewSupport.connectionPoint(project, e, obj)?.let { obj to it }
        }
        if (targets.isEmpty()) {
            StarRocksMatViewSupport.notify(project, "Could not resolve the StarRocks data source of the selection", NotificationType.WARNING)
            return
        }
        if (targets.size < objects.size) {
            StarRocksMatViewSupport.notify(project, "Skipped ${objects.size - targets.size} object(s): data source not resolved", NotificationType.WARNING)
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            targets.groupBy { (_, point) -> point.dataSource.uniqueId }.forEach { (_, group) ->
                val succeeded = mutableListOf<DasObject>()
                try {
                    StarRocksMatViewSupport.withConnection(project, group.first().second) { connection ->
                        group.forEach { (obj, _) ->
                            if (runStatement(project, connection, obj, e)) succeeded += obj
                        }
                    }
                } catch (t: Throwable) {
                    StarRocksMatViewSupport.notify(project, "Connect failed: ${t.message}", NotificationType.ERROR)
                }
                if (succeeded.isNotEmpty()) {
                    onSuccess(project, succeeded)
                }
            }
        }
    }

    private fun runStatement(
        project: Project,
        connection: DatabaseConnection,
        obj: DasObject,
        e: AnActionEvent
    ): Boolean {
        val target = StarRocksMatViewSupport.qualified(obj)
        return try {
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
                StarRocksMatViewStatus.put(
                    StarRocksMatViewSupport.schema(obj), obj.name,
                    MatViewState(afterState, if (afterState) null else "altered")
                )
            }
            StarRocksMatViewSupport.notify(project, "${e.presentation.text}: $target — done", NotificationType.INFORMATION)
            true
        } catch (t: Throwable) {
            StarRocksMatViewSupport.notify(project, "${e.presentation.text}: $target — failed: ${t.message}", NotificationType.ERROR)
            false
        }
    }

    /**
     * Optional confirmation gate shown before anything is executed; returning false aborts the
     * action. [objects] is the non-empty selection.
     */
    protected open fun confirmExecution(project: Project, objects: List<DasObject>, e: AnActionEvent): Boolean = true

    /**
     * Called on the pooled thread after at least one statement of this action succeeded
     * (once per data source). Used for side effects such as state cleanup and targeted tree
     * refresh; must stay fast — heavy work belongs on a separate thread.
     */
    protected open fun onSuccess(project: Project, succeeded: List<DasObject>) {}
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

class DropStarRocksMatViewAction : StarRocksMatViewAction(
    "Drop Materialized View",
    afterState = null,
    sql = { "DROP MATERIALIZED VIEW $it" },
) {
    override fun confirmExecution(project: Project, objects: List<DasObject>, e: AnActionEvent): Boolean {
        val title = "Drop Materialized View"
        return if (objects.size == 1) {
            val target = StarRocksMatViewSupport.qualified(objects.first())
            Messages.showYesNoDialog(
                project,
                "Drop materialized view `$target`?\n\nIt will be dropped permanently.",
                title,
                "Yes",
                "No",
                Messages.getWarningIcon(),
            ) == Messages.YES
        } else {
            Messages.showYesNoDialog(
                project,
                "Drop ${objects.size} materialized views?\n\nThey will be dropped permanently.",
                title,
                "Yes",
                "No",
                Messages.getWarningIcon(),
            ) == Messages.YES
        }
    }

    override fun onSuccess(project: Project, succeeded: List<DasObject>) {
        succeeded.forEach {
            StarRocksMatViewStatus.remove(StarRocksMatViewSupport.schema(it), it.name)
        }
        StarRocksMatViewSupport.refreshParents(project, succeeded)
    }
}
