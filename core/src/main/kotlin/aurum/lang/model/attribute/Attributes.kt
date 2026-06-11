package aurum.lang.model.attribute

import aurum.lang.model.Attribute
import aurum.lang.model.Type

object PrimaryConstructorAttribute : Attribute {
    override fun name(): String = "PrimaryConstructor"
    override fun values(): Map<String, Any?> = mapOf()
}

data class LambdaMethodAttribute (
    val functionalInterface: Type
) : Attribute {
    override fun name(): String = "LambdaMethod"
    override fun values(): Map<String, Any?> = mutableMapOf()
}

object FileClassAttribute : Attribute {
    override fun name(): String = "FileClass"

    override fun values(): Map<String, Any?> = mapOf()

    override fun isVisible(): Boolean = true
}

abstract class ExtensionAttribute : Attribute {
    lateinit var type: Type
    override fun name(): String = "Extension"
}