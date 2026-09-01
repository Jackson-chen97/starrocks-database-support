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

/**
 * Reformats StarRocks materialized-view DDL declaration files ("Go to DDL" / Ctrl+click) after
 * they open.
 *
 * `SHOW CREATE ...` text arrives in the virtual file asynchronously (the definition provider
 * consumes sources in the background), so reformat retries briefly until the PSI file has
 * content. Only `MATERIALIZED VIEW` definitions are touched; ordinary tables/views keep the
 * platform's existing rendering untouched.
 */
class StarRocksDdlFormatListener(private val project: Project) : FileEditorManagerListener {

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file.fileType !is DbStorageFileType) return
        scheduleFormat(file, MAX_ATTEMPTS)
    }

    private fun scheduleFormat(file: VirtualFile, attemptsLeft: Int) {
        val delay = if (attemptsLeft == MAX_ATTEMPTS) 0L else RETRY_DELAY_MS
        alarm.addRequest({
            if (!file.isValid || project.isDisposed) return@addRequest
            val psiFile = PsiManager.getInstance(project).findFile(file)
            val text = psiFile?.text ?: ""
            when {
                text.isBlank() && attemptsLeft > 1 -> scheduleFormat(file, attemptsLeft - 1)
                text.uppercase().contains("MATERIALIZED VIEW") && psiFile != null ->
                    ApplicationManager.getApplication().invokeLater {
                        if (psiFile.isValid) {
                            WriteCommandAction.runWriteCommandAction(project, "Reformat", null, {
                                CodeStyleManager.getInstance(project).reformat(psiFile, false)
                            })
                        }
                    }
            }
        }, delay)
    }

    private companion object {
        const val MAX_ATTEMPTS = 6
        const val RETRY_DELAY_MS = 250L
    }
}
