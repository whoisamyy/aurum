package lang.aurum.parsing.attribute

import lang.aurum.attribute.ExtensionAttribute
import lang.aurum.parsing.antlr.AurumParser

class ExtensionAttributeImpl(
    val typeCtx: AurumParser.TypeExprContext
) : ExtensionAttribute {
    override fun values(): Map<String?, Any?>? =
        mapOf(
            "typeCtx" to typeCtx
        )
}