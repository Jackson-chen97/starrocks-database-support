package com.jc.starrocks.datagrip.database

import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.view.DatabaseView
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup

/**
 * "Materialized View Actions" submenu mounted on DatabaseViewPopupMenu. Visible only when the
 * current tree selection contains a materialized view, so tables and plain views keep showing
 * just the stock "Object Actions" submenu.
 *
 * The alternative — overriding the platform's DatabaseView.ObjectContext.Alterations group by
 * redeclaring the same id — is silently ignored by ActionManager, hence this separate group.
 */
class StarRocksMatViewActionsGroup : DefaultActionGroup() {
    override fun update(e: AnActionEvent) {
        val hasMatView = e.getData(DatabaseView.DATABASE_ELEMENTS)
            ?.any { (it as? DasObject)?.kind == ObjectKind.MAT_VIEW } == true
        e.presentation.isEnabledAndVisible = hasMatView
    }
}
