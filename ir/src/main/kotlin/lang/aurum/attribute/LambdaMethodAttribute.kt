package lang.aurum.attribute

import lang.aurum.model.Attribute
import lang.aurum.model.Type

data class LambdaMethodAttribute (
    val functionalInterface: Type
) : Attribute {
    override fun name(): String = "LambdaMethod"
    override fun values(): Map<String, Any?> = mutableMapOf()
}