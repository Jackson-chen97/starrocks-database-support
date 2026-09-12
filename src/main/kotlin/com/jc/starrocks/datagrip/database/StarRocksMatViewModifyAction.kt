package com.jc.starrocks.datagrip.database

import com.intellij.database.model.DasObject
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.remote.jdbc.helpers.JdbcNativeUtil
import com.intellij.database.vfs.DatabaseElementVirtualFileImpl
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.database.dataSource.DatabaseConnectionPoint

/**
 * "Modify Materialized View" — right-click action that opens the view's DDL in the native
 * DataGrip "Go to DDL" editor tab (the same tab Ctrl+Click opens, prefilled by
 * [StarRocksDefinitionProvider] with `SHOW CREATE MATERIALIZED VIEW` output and reformatted by
 * [StarRocksDdlFormatListener]). An editor notification bar ([StarRocksMatViewApplyBarProvider])
 * offers a one-click Apply that runs the swap sequence on the edited text:
 *
 * 1. `CREATE MATERIALIZED VIEW <temp> AS ...` — the edited DDL, its name rewritten to [temp];
 * 2. `ALTER MATERIALIZED VIEW <temp> SWAP WITH <original>`;
 * 3. `DROP MATERIALIZED VIEW <temp>` (the leftover holding the old definition).
 *
 * [temp] is `<name>_<yyyyMMddHHmmss>` unless the user already renamed the view in the edited
 * DDL, in which case that name is respected. Multi-selection is not supported (update() gates
 * on exactly one view).
 */
class ModifyStarRocksMatViewAction : AnAction("Modify Materialized View") {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = StarRocksMatViewSupport.selectedMatViews(e).size == 1
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val obj = StarRocksMatViewSupport.selectedMatViews(e).singleOrNull() ?: return
        // Resolve the data source on the EDT: the event's data context is not valid on pooled threads.
        val point = StarRocksMatViewSupport.connectionPoint(project, e, obj)
        if (point == null) {
            StarRocksMatViewSupport.notify(project, "Could not resolve the StarRocks data source of the selection", NotificationType.WARNING)
            return
        }
        val dbElement = DbPsiFacade.getInstance(project).findElement(obj)
        if (dbElement == null) {
            StarRocksMatViewSupport.notify(project, "Could not locate ${obj.name} in the database tree; try refreshing the tree first", NotificationType.WARNING)
            return
        }
        val file = DatabaseElementVirtualFileImpl.findFile(dbElement, true)
            ?: run {
                StarRocksMatViewSupport.notify(project, "Could not open the DDL editor for ${obj.name}", NotificationType.WARNING)
                return
            }
        file.putUserData(CONTEXT_KEY, ModifyContext(obj, point))
        FileEditorManager.getInstance(project).openFile(file, true)
        StarRocksMatViewSupport.notify(
            project,
            "Edit the DDL, then click Apply Changes in the editor bar to swap the new definition in.",
            NotificationType.INFORMATION,
        )
    }

    companion object {
        /** Context stashed on the DDL virtual file by [ModifyStarRocksMatViewAction]; absent for Go-to-DDL-opened files. */
        val CONTEXT_KEY: Key<ModifyContext> = Key.create("starrocks.modify.matview.context")

        /** The editor document text at the time of the last successful load/apply (baseline for change detection). */
        val ORIGINAL_DDL_KEY: Key<String> = Key.create("starrocks.modify.matview.originalDdl")

        class ModifyContext(val obj: DasObject, val point: DatabaseConnectionPoint)

        /**
         * Applies [edited] as the new definition of [obj] via the swap sequence, on a pooled
         * thread. Used by both the Modify action path and the editor Apply bar; returns true
         * when all steps executed.
         */
        fun runModify(project: Project, point: DatabaseConnectionPoint, obj: DasObject, edited: String): Boolean {
            val target = StarRocksMatViewSupport.qualified(obj)
            val schema = StarRocksMatViewSupport.schema(obj)
            val originalQuoted = StarRocksMatViewSupport.quoteParts(schema, obj.name)
            val parsed = StarRocksMatViewDdl.parseName(edited)
            if (parsed == null) {
                StarRocksMatViewSupport.notify(
                    project,
                    "Modify Materialized View: $target — could not find a view name in the edited DDL; nothing was executed",
                    NotificationType.ERROR
                )
                return false
            }
            // Respect a user-renamed name in the edited DDL; otherwise generate the temp name.
            val userRenamed = !parsed.name.equals(obj.name, ignoreCase = true)
            val tempBare = if (userRenamed) parsed.name else StarRocksMatViewDdl.generateTempName(obj.name)
            val tempQuoted = StarRocksMatViewSupport.quoteParts(schema, tempBare)
            val createStmt = StarRocksMatViewDdl.rewriteName(edited, tempQuoted)
            if (createStmt == null) {
                StarRocksMatViewSupport.notify(
                    project,
                    "Modify Materialized View: $target — could not rewrite the view name in the DDL; nothing was executed",
                    NotificationType.ERROR
                )
                return false
            }
            val swap = StarRocksMatViewDdl.buildSwapStatements(originalQuoted, tempQuoted)
            val steps = listOf(
                "CREATE" to createStmt,
                "SWAP" to swap[0],
                "DROP" to swap[1],
            )
            val success = StarRocksMatViewSupport.withConnection(project, point) { connection ->
                // SWAP takes bare names, which resolve against the session database; select the
                // view's schema first (the console equivalent of the schema dropdown) and restore
                // the previous database afterwards — the connection may be shared with the user's
                // own console session.
                fun exec(sqlText: String): Unit {
                    JdbcNativeUtil.performRemote {
                        val statement = connection.remoteConnection.createStatement()
                        try {
                            statement.execute(sqlText)
                        } finally {
                            JdbcNativeUtil.closeRemoteStatementSafe(statement)
                        }
                        Unit
                    }
                }
                val previousDatabase: String? = try {
                    JdbcNativeUtil.computeRemote {
                        val statement = connection.remoteConnection.createStatement()
                        try {
                            val rs = statement.executeQuery("SELECT DATABASE()")
                            try {
                                if (rs.next()) rs.getString(1) else null
                            } finally {
                                rs.close()
                            }
                        } finally {
                            JdbcNativeUtil.closeRemoteStatementSafe(statement)
                        }
                    }
                } catch (t: Throwable) {
                    null
                }
                val useSchema = schema.takeIf { it.isNotBlank() }
                    ?.let { "USE ${StarRocksDefinitionProvider.quoteIdentifier(it)}" }
                try {
                    if (useSchema != null) {
                        try {
                            exec(useSchema)
                        } catch (t: Throwable) {
                            StarRocksMatViewSupport.notify(
                                project,
                                "Modify Materialized View: $target — failed to select schema: ${t.message}",
                                NotificationType.ERROR
                            )
                            return@withConnection false
                        }
                    }
                    for ((label, sqlText) in steps) {
                        try {
                            exec(sqlText)
                        } catch (t: Throwable) {
                            val hint = if (label == "SWAP" || label == "DROP") {
                                " The temp view `$tempBare` may still exist — drop it manually if so."
                            } else ""
                            StarRocksMatViewSupport.notify(
                                project,
                                "Modify Materialized View: $target — failed at $label: ${t.message}.$hint",
                                NotificationType.ERROR
                            )
                            return@withConnection false
                        }
                    }
                } finally {
                    val restore = previousDatabase
                    if (restore != null && !restore.equals(schema, ignoreCase = true)) {
                        try {
                            exec("USE ${StarRocksDefinitionProvider.quoteIdentifier(restore)}")
                        } catch (t: Throwable) {
                            // Best-effort restore only; the apply result is already decided.
                        }
                    }
                }
                StarRocksMatViewStatus.remove(schema, obj.name)
                StarRocksMatViewSupport.notify(project, "Modify Materialized View: $target — done", NotificationType.INFORMATION)
                ApplicationManager.getApplication().invokeLater {
                    StarRocksMatViewSupport.refreshParents(project, listOf(obj))
                }
                true
            }
            return success ?: false
        }
    }
}
