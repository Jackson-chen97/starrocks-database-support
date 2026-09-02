package com.jc.starrocks.datagrip.database

import com.intellij.database.vfs.DatabaseElementFileType
import com.intellij.database.vfs.DbStorageFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
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
        LOG.info("fileOpened path=${file.path} type=${file.fileType}")
        // "Go to DDL" opens das:// element files whose type is DatabaseElementFileType
        // (the .sql "source" variant); DbStorageFileType is kept for older platform builds.
        if (file.fileType !is DatabaseElementFileType && file.fileType !is DbStorageFileType) return
        scheduleFormat(file, WAIT_ATTEMPTS, tail = false)
    }

    private fun scheduleFormat(file: VirtualFile, attemptsLeft: Int, tail: Boolean) {
        if (attemptsLeft <= 0) {
            LOG.info("poll budget exhausted: ${file.name}")
            return
        }
        if (project.isDisposed || !file.isValid) return
        val budget = if (tail) TAIL_ATTEMPTS else WAIT_ATTEMPTS
        val delay = if (attemptsLeft == budget) 0L else RETRY_DELAY_MS
        alarm.addRequest({
            if (project.isDisposed || !file.isValid) return@addRequest
            if (!FileEditorManager.getInstance(project).isFileOpen(file)) {
                LOG.info("tab closed, stopping: ${file.name}")
                return@addRequest
            }
            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile == null) {
                LOG.info("psiFile null for ${file.name}, attempt=$attemptsLeft")
                if (attemptsLeft > 1) scheduleFormat(file, attemptsLeft - 1, tail)
                return@addRequest
            }
            val text = psiFile.text
            val isMv = text.uppercase().contains("MATERIALIZED VIEW")
            LOG.info(
                "poll ${file.name} attempt=$attemptsLeft tail=$tail lang=${psiFile.language} " +
                    "len=${text.length} blank=${text.isBlank()} mv=$isMv sameAsFormatted=${lastFormatted[file] == text}"
            )
            if (text.isBlank()) {
                if (attemptsLeft > 1) scheduleFormat(file, attemptsLeft - 1, tail)
                return@addRequest
            }
            if (!isMv) {
                LOG.info("not an MV definition, stopping: ${file.name}")
                return@addRequest
            }
            if (lastFormatted[file] != text) {
                lastFormatted[file] = text
                val preview = text.take(200).replace('\n', ' ')
                LOG.info("reformatting ${file.name}: $preview")
                ApplicationManager.getApplication().invokeLater {
                    if (psiFile.isValid) {
                        val before = psiFile.text
                        WriteCommandAction.runWriteCommandAction(project, "Reformat", null, {
                            CodeStyleManager.getInstance(project).reformat(psiFile, false)
                        })
                        val after = psiFile.text
                        LOG.info(
                            "reformatted ${file.name}: beforeLen=${before.length} afterLen=${after.length} " +
                                "changed=${before != after} afterPreview=${after.take(200).replace('\n', ' ')}"
                        )
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
        private val LOG = Logger.getInstance(StarRocksDdlFormatListener::class.java)
        const val WAIT_ATTEMPTS = 120
        const val TAIL_ATTEMPTS = 12
        const val RETRY_DELAY_MS = 250L
    }
}
