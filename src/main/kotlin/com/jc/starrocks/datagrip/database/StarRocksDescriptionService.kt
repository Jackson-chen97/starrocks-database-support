package com.jc.starrocks.datagrip.database

import com.intellij.database.model.DescriptionService
import com.intellij.database.model.ObjectKind
import com.intellij.database.model.basic.BasicElement

/**
 * Suffixes inactive materialized views with a status marker in the database tree.
 *
 * The reused PostgreSQL meta-graph has no StarRocks-specific "is active" property, and live
 * PgImplModel nodes cannot be extended with new BasicMetaPropertyIds from a plugin; the platform
 * also has no per-status icon seam (kind -> DatabaseIcons.MaterializedView is fixed). So status is
 * kept in an in-memory cache filled by StarRocksMatViewStatus during introspection and rendered
 * here as a name suffix - the same presentation pattern Oracle uses for [invalid] objects.
 */
class StarRocksDescriptionService : DescriptionService() {
    companion object {
        @JvmField
        val INSTANCE: StarRocksDescriptionService = StarRocksDescriptionService()
    }

    override fun options(element: BasicElement, context: Context): String {
        if (element.kind == ObjectKind.MAT_VIEW) {
            val status = StarRocksMatViewStatus.get(element)
            if (status != null && !status.isActive) {
                return " [inactive]"
            }
            return ""
        }
        return super.options(element, context)
    }
}
