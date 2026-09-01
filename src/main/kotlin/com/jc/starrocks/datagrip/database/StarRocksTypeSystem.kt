package com.jc.starrocks.datagrip.database

import com.intellij.database.Dbms
import com.intellij.database.dialects.base.types.DasTypeSystemImpl
import com.intellij.database.types.DasArrayType
import com.intellij.database.types.DasType
import com.intellij.database.types.DasTypeCategory
import com.intellij.database.types.DasTypeSystemBase
import com.intellij.psi.PsiElement
import com.intellij.sql.dialects.BuiltinFunction
import com.intellij.sql.dialects.SqlDialectImplUtilCore
import com.intellij.sql.psi.SqlExpressionList
import com.intellij.sql.psi.SqlReferenceExpression
import com.intellij.sql.psi.impl.SqlImplUtil

class StarRocksTypeSystem(dbms: Dbms = StarRocksDbms.INSTANCE) : DasTypeSystemImpl(dbms) {
    override fun getNormalizedTypeName(name: String): String {
        val baseName = name
            .substringBefore("(")
            .substringBefore("<")
            .trim()
            .uppercase()
        return NORMALIZED_TYPE_NAMES[baseName] ?: baseName
    }

    override fun getDefaultTypeName(category: DasTypeCategory): String {
        return DEFAULT_TYPE_NAMES[category] ?: super.getDefaultTypeName(category) ?: "STRING"
    }

    override fun getBuiltinFunctionReturnType(
        prototype: BuiltinFunction.Prototype,
        reference: SqlReferenceExpression?,
        argumentList: PsiElement?,
        routineElement: PsiElement
    ): DasType? {
        if (!reference?.name.equals("UNNEST", ignoreCase = true)) {
            return super.getBuiltinFunctionReturnType(prototype, reference, argumentList, routineElement)
        }

        val componentType = ((argumentList as? SqlExpressionList)
            ?.expressionList
            ?.firstOrNull()
            ?.dasType as? DasArrayType)
            ?.componentType
            ?: DasTypeSystemBase.UNKNOWN
        val columnElement = reference ?: routineElement
        return SqlImplUtil.createType(
            listOf(SqlDialectImplUtilCore.col(columnElement, columnElement, "unnest", componentType)),
            routineElement
        )
    }

    override fun getTableTypeSpecification(type: String): String {
        return when (type.uppercase()) {
            "OLAP" -> "OLAP"
            "MYSQL" -> "MYSQL"
            "ELASTICSEARCH" -> "ELASTICSEARCH"
            "HIVE" -> "HIVE"
            "ICEBERG" -> "ICEBERG"
            "HUDI" -> "HUDI"
            "JDBC" -> "JDBC"
            else -> super.getTableTypeSpecification(type)
        }
    }

    companion object {
        val SCALAR_TYPES: Set<String> = setOf(
            "BOOLEAN",
            "TINYINT",
            "SMALLINT",
            "INT",
            "BIGINT",
            "LARGEINT",
            "FLOAT",
            "DOUBLE",
            "DECIMAL32",
            "DECIMAL64",
            "DECIMAL128",
            "DATE",
            "DATETIME",
            "CHAR",
            "VARCHAR",
            "STRING",
            "BINARY",
            "VARBINARY",
            "JSON",
            "VARIANT",
            "BITMAP",
            "HLL",
            "PERCENTILE"
        )

        val COMPLEX_TYPES: Set<String> = setOf(
            "ARRAY",
            "MAP",
            "STRUCT"
        )

        private val NORMALIZED_TYPE_NAMES: Map<String, String> = mapOf(
            "BOOL" to "BOOLEAN",
            "INTEGER" to "INT",
            "DECIMAL" to "DECIMAL128",
            "DECIMALV2" to "DECIMAL128",
            "NUMERIC" to "DECIMAL128",
            "VARCHAR2" to "VARCHAR",
            "TEXT" to "STRING",
            "BINARY" to "VARBINARY",
            "TIMESTAMP" to "DATETIME"
        )

        private val DEFAULT_TYPE_NAMES: Map<DasTypeCategory, String> = mapOf(
            DasTypeCategory.INTEGER to "BIGINT",
            DasTypeCategory.REAL to "DOUBLE",
            DasTypeCategory.STRING to "STRING",
            DasTypeCategory.BOOLEAN to "BOOLEAN",
            DasTypeCategory.DATE to "DATE",
            DasTypeCategory.DATE_TIME to "DATETIME",
            DasTypeCategory.TIMESTAMP to "DATETIME",
            DasTypeCategory.BYTES to "VARBINARY",
            DasTypeCategory.RECORD to "STRUCT",
            DasTypeCategory.UNKNOWN to "STRING"
        )
    }
}
