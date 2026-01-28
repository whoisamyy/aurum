package lang.aurum.parsing.attribute

import lang.aurum.model.Attribute

object FileClassAttribute : Attribute {
    override fun name(): String = "FileClass"

    override fun values(): Map<String, Any?> = mapOf()

    override fun isVisible(): Boolean = true
}
