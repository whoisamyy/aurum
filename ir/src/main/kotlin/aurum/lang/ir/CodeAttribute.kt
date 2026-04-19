package aurum.lang.ir

import aurum.lang.model.Attribute

data class CodeAttribute(
    val code: MutableList<Instruction>
) : Attribute {
    override fun name(): String = "code"
    override fun values(): Map<String, Any> = mapOf("code" to code)
}
