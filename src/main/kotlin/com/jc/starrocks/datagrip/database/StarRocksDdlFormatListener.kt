package com.jc.starrocks.datagrip.database

import com.intellij.database.vfs.DbStorageFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.Alarm
import java.util.Collections
import java.util.WeakHashMap

/**
 * Reformats StarRocks materialized-view DDL declaration files ("Go to DDL" / Ctrl+click) after
 * they open.
 *
 * `SHOW CREATE ...` text arrives in the virtual file asynchronously (the definition provider
 * consumes sources in the background, sometimes after a cold connection round-trip), so the
 * listener keeps polling while the document is blank and, once a `MATERIALIZED VIEW` definition
 * has been reformatted, keeps a short tail window in which a later model refresh that overwrites
 * the document with the raw single-line text is detected and reformatted again.
 *
 * Only `MATERIALIZED VIEW` definitions are touched; ordinary tables/views keep the platform's
 * existing rendering untouched.
 */
class StarRocksDdlFormatListener(private val project: Project) : FileEditorManagerListener {

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
    private val lastFormatted: MutableMap<VirtualFile, String> =
        Collections.synchronizedMap(WeakHashMap())

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file.fileType !is DbStorageFileType) return
        scheduleFormat(file, WAIT_ATTEMPTS, tail = false)
    }

    private fun scheduleFormat(file: VirtualFile, attemptsLeft: Int, tail: Boolean) {
        if (attemptsLeft <= 0 || project.isDisposed || !file.isValid) return
        val budget = if (tail) TAIL_ATTEMPTS else WAIT_ATTEMPTS
        val delay = if (attemptsLeft == budget) 0L else RETRY_DELAY_MS
        alarm.addRequest({
            if (project.isDisposed || !file.isValid) return@addRequest
            if (!FileEditorManager.getInstance(project).isFileOpen(file)) return@addRequest
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@addRequest
            val text = psiFile.text
            if (text.isBlank()) {
                if (attemptsLeft > 1) scheduleFormat(file, attemptsLeft - 1, tail)
                return@addRequest
            }
            if (!text.uppercase().contains("MATERIALIZED VIEW")) return@addRequest
            if (lastFormatted[file] != text) {
                lastFormatted[file] = text
                ApplicationManager.getApplication().invokeLater {
                    if (psiFile.isValid) {
                        WriteCommandAction.runWriteCommandAction(project, "Reformat", null, {
                            CodeStyleManager.getInstance(project).reformat(psiFile, false)
                        })
                    }
                }
            }
            if (attemptsLeft > 1) {
                // Once the definition has been seen, switch to the shorter tail budget and
                // keep watching in case the model replaces the document content later.
                scheduleFormat(file, if (tail) attemptsLeft - 1 else TAIL_ATTEMPTS, true)
            }
        }, delay)
    }

    private companion object {
        const val WAIT_ATTEMPTS = 120
        const val TAIL_ATTEMPTS = 12
        const val RETRY_DELAY_MS = 250L
    }
}
