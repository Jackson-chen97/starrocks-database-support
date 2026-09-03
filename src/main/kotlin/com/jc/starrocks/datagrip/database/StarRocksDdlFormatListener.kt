package com.jc.starrocks.datagrip.database

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.sql.dialects.SqlDialectMappings
import com.intellij.util.Alarm
import com.intellij.util.FileContentUtil
import com.jc.starrocks.datagrip.dialect.StarRocksDialect
import java.util.Collections
import java.util.WeakHashMap

/**
 * Reformats StarRocks materialized-view DDL declaration files ("Go to DDL" / Ctrl+click)
 * after they open.
 *
 * Platform note (DataGrip 2026.1): the DDL tab content is loaded into the editor's
 * [com.intellij.openapi.editor.Document] while the underlying [VirtualFile.text] stays
 * empty, and the file type is a plain [com.intellij.sql.SqlFileType] for storage-FS
 * `.sql` files (older builds use `DatabaseElementFileType`). So the listener reads the
 * text from the editor document and triggers the exact same reformat the user would get
 * from Ctrl+Alt+L: [CodeStyleManager.reformat] on that document, once per unique raw
 * text. A short tail window catches a later model refresh that overwrites the document
 * with the raw single-line text.
 *
 * Only `MATERIALIZED VIEW` definitions are touched; ordinary tables/views keep the
 * platform's existing rendering untouched.
 */
class StarRocksDdlFormatListener(private val project: Project) : FileEditorManagerListener {

    // EDT alarm: every poll reads the editor Document and the file's PSI, both of which require
    // read access. On the EDT that access is implicit, so no (deprecated, and previously
    // thread-violating) ReadAction wrapper is needed; a tick costs one regex plus one PSI lookup.
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)
    private val lastFormatted: MutableMap<VirtualFile, String> =
        Collections.synchronizedMap(WeakHashMap())

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (!file.name.endsWith(".sql")) return
        LOG.info("fileOpened path=${file.path} type=${file.fileType}")
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
            val document = FileEditorManager.getInstance(project).getEditorList(file)
                .firstNotNullOfOrNull { (it as? TextEditor)?.editor?.document }
            if (document == null) {
                if (attemptsLeft > 1) scheduleFormat(file, attemptsLeft - 1, tail)
                return@addRequest
            }
            // Runs on the EDT (SWING_THREAD alarm), so document and PSI reads have implicit
            // read access already; the actual reformat still happens inside a write command below.
            val text = document.text
            val isMv = MV_PATTERN.containsMatchIn(text)
            val psiLang = try {
                PsiManager.getInstance(project).findFile(file)?.language?.toString() ?: "?"
            } catch (t: Throwable) {
                "?"
            }
            LOG.info(
                "poll ${file.name} attempt=$attemptsLeft tail=$tail psiLang=$psiLang sqlLang=${probeSqlLanguage(file)} " +
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
                val preview = text.take(200).replace('\n', ' ')
                LOG.info("reformatting ${file.name} via=${probeSqlLanguage(file)}: $preview")
                ApplicationManager.getApplication().invokeLater {
                    if (!file.isValid) return@invokeLater
                    val before = document.text
                    // Pin this DDL file to the StarRocks dialect so the platform's native
                    // reformat (exactly what Ctrl+Alt+L runs) applies our formatter blocks;
                    // without the mapping the storage-FS .sql file would parse as generic SQL.
                    try {
                        SqlDialectMappings.getInstance(project).setMapping(file, StarRocksDialect.INSTANCE)
                        FileContentUtil.reparseFiles(project, listOf(file), true)
                    } catch (t: Throwable) {
                        LOG.warn("dialect mapping failed for ${file.name}", t)
                    }
                    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@invokeLater
                    WriteCommandAction.runWriteCommandAction(project, "Reformat MV DDL", null, {
                        CodeStyleManager.getInstance(project).reformat(psiFile)
                    })
                    val after = document.text
                    lastFormatted[file] = after
                    LOG.info(
                        "reformatted ${file.name}: beforeLen=${before.length} afterLen=${after.length} " +
                            "changed=${before != after} afterPreview=${after.take(200).replace('\n', ' ')}"
                    )
                }
            }
            if (attemptsLeft > 1) {
                // Once the definition has been seen, switch to the shorter tail budget and
                // keep watching in case the model replaces the document content later.
                scheduleFormat(file, if (tail) attemptsLeft - 1 else TAIL_ATTEMPTS, true)
            }
        }, delay)
    }

    /** Diagnostic: which dialect the platform maps this file to (Ctrl+Alt+L would use the same one). */
    private fun probeSqlLanguage(file: VirtualFile): String = try {
        val method = Class.forName("com.intellij.sql.psi.SqlLanguageUtils")
            .getMethod("getSqlLanguage", VirtualFile::class.java, Project::class.java)
        method.invoke(null, file, project)?.toString() ?: "null"
    } catch (t: Throwable) {
        "unavailable(${t.message?.take(40)})"
    }

    private companion object {
        private val LOG = Logger.getInstance(StarRocksDdlFormatListener::class.java)
        private val MV_PATTERN = Regex("(?s)CREATE\\s+MATERIALIZED\\s+VIEW")
        const val WAIT_ATTEMPTS = 120
        const val TAIL_ATTEMPTS = 12
        const val RETRY_DELAY_MS = 250L
    }
}
