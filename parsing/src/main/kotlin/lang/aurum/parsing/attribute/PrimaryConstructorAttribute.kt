package lang.aurum.parsing.attribute

import lang.aurum.model.Attribute

object PrimaryConstructorAttribute : Attribute {
    override fun name(): String = "PrimaryConstructor"
    override fun values(): Map<String, Any?> = mapOf()
}