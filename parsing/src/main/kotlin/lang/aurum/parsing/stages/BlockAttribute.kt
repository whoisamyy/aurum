package lang.aurum.parsing.stages

import lang.aurum.model.Attribute
import lang.aurum.parsing.antlr.AurumParser

data class BlockAttribute(val block: AurumParser.BlockContext) : Attribute {
    override fun name(): String = "Block"

    override fun values(): Map<String, Any?> = mapOf("block" to block)

    override fun isVisible(): Boolean = false
}