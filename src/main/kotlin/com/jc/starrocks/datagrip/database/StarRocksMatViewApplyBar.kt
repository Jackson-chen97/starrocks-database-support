package com.jc.starrocks.datagrip.database

import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.vfs.DatabaseElementVirtualFileImpl
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

/**
 * Editor notification bar shown on native "Go to DDL" editor tabs of StarRocks materialized
 * views. Purely informational — the Apply action itself lives in the editor header strip
 * ([StarRocksMatViewHeaderButtonListener], right side).
 *
 * The bar also seeds the DDL baseline ([ModifyStarRocksMatViewAction.ORIGINAL_DDL_KEY]) once the
 * asynchronously loaded definition lands — the platform's DdlEditorContentLoader fills the
 * document after the editor opens, so the first non-blank snapshot becomes the baseline the
 * Apply button compares against.
 */
class StarRocksMatViewApplyBarProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent>? {
        val vf = file as? DatabaseElementVirtualFileImpl ?: return null
        if (vf.objectKind != ObjectKind.MAT_VIEW) return null
        return Function { fileEditor ->
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
                text("Edit the materialized view DDL, then click the Apply button above — the new definition replaces it under the same name.")
                val document = ReadAction.compute<Document?, RuntimeException> {
                    FileDocumentManager.getInstance().getDocument(file)
                }
                document?.addDocumentListener(object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        StarRocksMatViewApplySupport.seedBaseline(file, event.document)
                    }
                })
                StarRocksMatViewApplySupport.seedBaseline(file, document ?: return@apply)
            }
        }
    }
}

/** Shared apply/baseline plumbing for the editor bar and the header-strip Apply button. */
object StarRocksMatViewApplySupport {

    fun seedBaseline(file: VirtualFile, document: Document?) {
        if (document == null) return
        if (file.getUserData(ModifyStarRocksMatViewAction.ORIGINAL_DDL_KEY) != null) return
        val text = document.text
        if (text.isNotBlank()) {
            file.putUserData(ModifyStarRocksMatViewAction.ORIGINAL_DDL_KEY, text)
        }
    }

    /** True when the document differs from the seeded baseline — i.e. there is something to apply. */
    fun hasChanges(file: VirtualFile): Boolean = ReadAction.compute<Boolean, RuntimeException> {
        val baseline = file.getUserData(ModifyStarRocksMatViewAction.ORIGINAL_DDL_KEY) ?: return@compute false
        val text = FileDocumentManager.getInstance().getDocument(file)?.text ?: return@compute false
        text.isNotBlank() && text != baseline
    }

    /**
     * Resolves everything [apply] needs — the document and the database model require read
     * access, so the whole resolution runs inside a ReadAction. Returns null (with a
     * notification) when the view or its data source cannot be resolved.
     */
    fun prepare(project: Project, file: VirtualFile): Prepared? = ReadAction.compute<Prepared?, RuntimeException> {
        val edited = FileDocumentManager.getInstance().getDocument(file)?.text
        if (edited.isNullOrBlank()) {
            StarRocksMatViewSupport.notify(project, "The DDL editor is still loading — try again in a moment", NotificationType.WARNING)
            return@compute null
        }
        val baseline = file.getUserData(ModifyStarRocksMatViewAction.ORIGINAL_DDL_KEY)
        val context = file.getUserData(ModifyStarRocksMatViewAction.CONTEXT_KEY)
        val obj = context?.obj ?: (file as? DatabaseElementVirtualFileImpl)?.findElement(project)
        if (obj == null) {
            StarRocksMatViewSupport.notify(project, "Could not resolve the materialized view for this editor", NotificationType.WARNING)
            return@compute null
        }
        val point = context?.point ?: StarRocksMatViewSupport.connectionPointFor(project, obj)
        if (point == null) {
            StarRocksMatViewSupport.notify(project, "Could not resolve the StarRocks data source of ${obj.name}", NotificationType.WARNING)
            return@compute null
        }
        if (edited == baseline) {
            StarRocksMatViewSupport.notify(project, "No changes to apply for ${obj.name}", NotificationType.INFORMATION)
            return@compute null
        }
        Prepared(obj, point, edited)
    }

    /**
     * Runs the swap pipeline for [prepared] on a pooled thread; refreshes the baseline on
     * success only, so a failed apply stays hot for a retry.
     */
    fun apply(project: Project, file: VirtualFile, prepared: Prepared): Boolean {
        val success = ModifyStarRocksMatViewAction.runModify(project, prepared.point, prepared.obj, prepared.edited)
        if (success) {
            file.putUserData(ModifyStarRocksMatViewAction.ORIGINAL_DDL_KEY, prepared.edited)
        }
        return success
    }

    class Prepared(val obj: DasObject, val point: com.intellij.database.dataSource.DatabaseConnectionPoint, val edited: String)
}
