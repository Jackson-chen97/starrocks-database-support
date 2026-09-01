package com.jc.starrocks.datagrip.lang

import com.intellij.database.types.DasType
import com.intellij.database.types.DasTypeSystemBase
import com.intellij.database.model.ObjectKind
import com.intellij.psi.tree.IElementType
import com.intellij.sql.psi.SqlColumnAliasDefinition
import com.intellij.sql.psi.SqlIdentifier
import com.intellij.sql.psi.SqlNameElement
import com.intellij.sql.psi.SqlTypeElement
import com.intellij.sql.psi.impl.SqlAsExpressionImpl
import com.intellij.sql.psi.impl.SqlColumnAliasListImpl
import com.intellij.sql.psi.impl.SqlDefinitionImpl

internal class StarRocksAsExpression(type: IElementType) : SqlAsExpressionImpl(type)

/**
 * Column aliases in a table alias list are ordinary AST PSI in this dialect.
 * The platform implementation is stub-backed and cannot be constructed for a
 * Grammar-Kit element type, so expose the same SQL PSI contract without a stub.
 */
internal class StarRocksColumnAliasDefinition(type: IElementType) :
    SqlDefinitionImpl(type),
    SqlColumnAliasDefinition {

    override fun getNameElement(): SqlIdentifier =
        findChildByClass(SqlIdentifier::class.java)
            ?: error("A column alias definition must contain an SQL identifier")

    override fun isPlainIdentifier(): Boolean = nameElement.isPlainIdentifier

    override fun isQuotedIdentifier(): Boolean = nameElement.isQuotedIdentifier

    override fun setName(name: String): SqlNameElement = nameElement.setName(name)

    override fun getTypeElement(): SqlTypeElement? = null

    override fun getKind(): ObjectKind = ObjectKind.COLUMN

    override fun isNotNull(): Boolean = false

    override fun getDefault(): String? = null

    override fun getDasType(): DasType =
        (parent as? SqlColumnAliasListImpl)?.getAliasedDasType(this)
            ?: DasTypeSystemBase.UNKNOWN
}
