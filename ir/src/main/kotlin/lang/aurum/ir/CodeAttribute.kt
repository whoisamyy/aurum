package lang.aurum.ir

import lang.aurum.model.Attribute

data class CodeAttribute(
    val code: MutableList<Instruction>
) : Attribute {
    override fun name(): String = "code"
    override fun values(): Map<String, Any> = mapOf("code" to code)
}
