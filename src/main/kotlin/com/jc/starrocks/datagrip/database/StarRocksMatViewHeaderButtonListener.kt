package com.jc.starrocks.datagrip.database

import com.intellij.database.model.ObjectKind
import com.intellij.database.vfs.DatabaseElementVirtualFileImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Action
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Installs the "Apply" button into the RIGHT side (BorderLayout.EAST) of the DDL editor's
 * header strip. The platform builds that strip in DatabaseEditorHelper.configureToolbar as an
 * [com.intellij.openapi.editor.impl.EditorHeaderComponent] and adds its action toolbar at
 * "West" only — group membership can never reach the right side, so the button is injected
 * into the header panel directly, keyed on the panel to survive editor re-opens.
 *
 * The button is enabled while the document differs from the baseline DDL seeded by
 * [StarRocksMatViewApplySupport.seedBaseline]; clicking runs the swap pipeline
 * (create-as-temp → `SWAP WITH` → drop-temp) via [StarRocksMatViewApplySupport.apply].
 */
class StarRocksMatViewHeaderButtonListener : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file !is DatabaseElementVirtualFileImpl) return
        if (file.objectKind != ObjectKind.MAT_VIEW) return
        // The permanent header strip is set up by the platform asynchronously after the editor
        // opens; retry until it exists (bounded), skipping if the button is already installed.
        installWithRetry(source.project, file, source, attempt = 0)
    }

    private fun installWithRetry(
        project: Project,
        file: DatabaseElementVirtualFileImpl,
        source: FileEditorManager,
        attempt: Int,
    ) {
        if (!file.isValid) return
        val editors = source.getEditors(file)
        // An empty list right after fileOpened is common (the editor is still being composed)
        // and must keep the retry loop alive just like a missing header would.
        var pending = editors.isEmpty()
        for (fileEditor in editors) {
            val editorEx = EditorUtil.getEditorEx(fileEditor) as? EditorEx ?: continue
            if (install(editorEx, file, project)) continue
            pending = true
        }
        if (pending && attempt < MAX_ATTEMPTS) {
            ApplicationManager.getApplication().invokeLater {
                installWithRetry(project, file, source, attempt + 1)
            }
        }
    }

    /** Returns true when the header exists and the button is (already) installed. */
    private fun install(
        editorEx: EditorEx,
        file: DatabaseElementVirtualFileImpl,
        project: Project,
    ): Boolean {
        val header = editorEx.permanentHeaderComponent as? JPanel ?: return false
        if (header.getClientProperty(BUTTON_KEY) != null) return true
        val button = ApplyButton(project, file)
        // BorderLayout.EAST would stretch the wrapper to the strip's full height; the inner
        // FlowLayout keeps the button at its compact preferred height, vertically centered.
        val wrapper = JPanel(FlowLayout(FlowLayout.TRAILING, 0, 3)).apply { isOpaque = false }
        wrapper.add(button)
        header.add(wrapper, BorderLayout.EAST)
        header.putClientProperty(BUTTON_KEY, button)
        header.revalidate()
        header.repaint()
        return true
    }

    companion object {
        private val BUTTON_KEY: Key<ApplyButton> = Key.create("starrocks.apply.headerButton")

        /** The platform header setup is fast; 100ms x 30 covers slow machine corners. */
        private const val MAX_ATTEMPTS = 30
    }
}

private class ApplyButton(
    private val project: Project,
    private val file: VirtualFile,
) : JPanel(BorderLayout()) {

    private val document: Document? = ReadAction.compute<Document?, RuntimeException> {
        FileDocumentManager.getInstance().getDocument(file)
    }
    private val button: JButton

    init {
        isOpaque = false
        val action = object : AbstractAction("Apply") {
            override fun actionPerformed(e: ActionEvent) {
                // Document/model reads need read access — resolve everything on the EDT, then
                // hand only plain data to the pooled thread that runs the JDBC pipeline.
                val prepared = StarRocksMatViewApplySupport.prepare(project, file) ?: return
                ApplicationManager.getApplication().executeOnPooledThread {
                    val success = StarRocksMatViewApplySupport.apply(project, file, prepared)
                    if (success) {
                        ApplicationManager.getApplication().invokeLater { refresh() }
                    }
                }
            }
        }
        button = RoundedGreenButton(action)
        button.toolTipText = "Apply the edited materialized view DDL (create-temp, swap, drop-temp)"
        button.isFocusable = false
        button.foreground = java.awt.Color.WHITE
        button.preferredSize = java.awt.Dimension(
            button.getFontMetrics(button.font).stringWidth("Apply") + 16,
            22,
        )
        button.isEnabled = false
        add(button, BorderLayout.EAST)
        document?.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = refresh()
        })
        refresh()
    }

    private fun refresh() {
        StarRocksMatViewApplySupport.seedBaseline(file, document)
        val changed = StarRocksMatViewApplySupport.hasChanges(file)
        if (button.isEnabled != changed) {
            button.isEnabled = changed
        }
    }
}


/**
 * OK-style button: rounded, filled green when enabled, grey when disabled — painted manually so
 * the look is identical across IDE themes and button-UI variants.
 */
private class RoundedGreenButton(action: Action) : JButton(action) {

    init {
        isContentAreaFilled = false
        isFocusPainted = false
        isBorderPainted = false
        isOpaque = false
        border = JBUI.Borders.empty(1, 10)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = when {
            !isEnabled -> JBColor(0x3D4A44, 0x4A5450)
            model.isPressed || model.isRollover -> JBColor(0x1F6F33, 0x63B877)
            else -> JBColor(0x2E7D32, 0x57965C)
        }
        g2.fillRoundRect(0, 0, width, height, 12, 12)
        // Text drawn manually in pure white — theme foreground colors don't apply.
        g2.font = font.deriveFont(font.size2D - 1f)
        val fm = g2.fontMetrics
        g2.color = java.awt.Color.WHITE
        g2.drawString(text, (width - fm.stringWidth(text)) / 2, (height - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }
}
