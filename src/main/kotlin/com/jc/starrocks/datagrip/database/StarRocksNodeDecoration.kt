package com.jc.starrocks.datagrip.database

import com.intellij.database.explorer.structure.DbTreeNodeDecoration
import com.intellij.database.explorer.structure.DvDecorationExtension
import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.model.basic.BasicNode
import com.intellij.database.model.basic.BasicRoot

/**
 * Explorer-node decorations for the 2026.1 database tree:
 * - inactive materialized views get a " [inactive]" suffix;
 * - columns get their comment appended in a dimmed "-- comment" form.
 *
 * The new model tree does not render through DescriptionService.options (only the legacy dump
 * dialog and MCP tools call it), so presentation extras live on the explorer decoration seam.
 * The seam is global, so everything is gated to roots whose dbms is StarRocks.
 */
class StarRocksNodeDecoration : DvDecorationExtension {
    override fun decorate(node: BasicNode, decoration: DbTreeNodeDecoration): DbTreeNodeDecoration {
        val obj = node as? DasObject ?: return decoration
        if (obj.kind != ObjectKind.MAT_VIEW && obj.kind != ObjectKind.COLUMN) return decoration
        if (!isStarRocks(obj)) return decoration
        when (obj.kind) {
            ObjectKind.MAT_VIEW -> {
                val status = StarRocksMatViewStatus.get(obj) ?: return decoration
                if (status.isActive) return decoration
                if (hasParticle(node, decoration, INACTIVE_SUFFIX)) return decoration
                return decoration.withSimpleParticle(INACTIVE_SUFFIX)
            }
            ObjectKind.COLUMN -> {
                val comment = obj.comment?.trim()?.replace(Regex("\\s*\\R\\s*"), " ")?.takeIf { it.isNotEmpty() }
                    ?: return decoration
                val shown = if (comment.length > MAX_COMMENT) comment.take(MAX_COMMENT) + "…" else comment
                val suffix = "  -- $shown"
                if (hasParticle(node, decoration, suffix)) return decoration
                return decoration.withSimpleParticle(suffix)
            }
            else -> return decoration
        }
    }

    private fun isStarRocks(obj: DasObject): Boolean {
        var node: DasObject? = obj
        while (node != null) {
            if (node is BasicRoot) {
                val dbms = runCatching { node.dbms }.getOrNull() ?: return false
                return dbms.name == StarRocksDbms.INSTANCE.name
            }
            node = node.dasParent
        }
        return false
    }

    private fun hasParticle(node: BasicNode, decoration: DbTreeNodeDecoration, text: String): Boolean =
        decoration.particles.any { it.getParticleText(node) == text }

    private companion object {
        const val INACTIVE_SUFFIX = " [inactive]"
        const val MAX_COMMENT = 80
    }
}
