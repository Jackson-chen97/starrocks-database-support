package com.jc.starrocks.datagrip.database

import com.intellij.database.dialects.postgres.model.PgMetaModel
import com.intellij.database.model.DescriptionService
import com.intellij.database.model.ModelFacade
import com.intellij.database.model.ModelHelper
import com.intellij.database.model.meta.BasicMetaModel

/**
 * Model facade that gives StarRocks schemas a materialized-view family.
 *
 * The generic meta-model only knows {ROUTINE, SCHEMA, TABLE, VIEW}, so JDBC table type
 * "MATERIALIZED VIEW" cannot be represented and MV introspection is degraded to plain tables.
 * PostgreSQL's public meta-model already carries ObjectKind.MAT_VIEW under a SCHEMA that sits
 * under a DATABASE - matching StarRocks catalog/database object semantics - so we reuse it
 * wholesale (same route the Apache Doris plugin takes with MsMetaModel).
 *
 * Like DorisModelFacade, this overrides ONLY getMetaModel/getModelHelper/getDescriptionService:
 * the base createModel builds the live model through the meta-model's own factory. Hand-delegating
 * to another facade's createModel routes BasicMetaModel.newModel through a mismatched factory and
 * trips the dbms consistency assert, blanking the whole database tree.
 */
class StarRocksModelFacade : ModelFacade(StarRocksDbms.INSTANCE) {
    override fun getMetaModel(): BasicMetaModel<*> = PgMetaModel.MODEL

    override fun getModelHelper(): ModelHelper = HELPER

    override fun getDescriptionService(): DescriptionService = StarRocksDescriptionService.INSTANCE

    private companion object {
        val HELPER: ModelHelper = object : ModelHelper() {}
    }
}
