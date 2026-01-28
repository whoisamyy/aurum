package lang.aurum.attribute

import lang.aurum.model.Attribute

object LambdaMethodAttribute : Attribute {
    override fun name(): String = "LambdaMethod"
    override fun values(): Map<String, Any?> = mutableMapOf()
}