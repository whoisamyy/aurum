package lang.aurum.ir

import lang.aurum.model.Attribute

data class CodeAttribute(
    val code: List<Instruction>
) : Attribute {
    override fun name(): String = "code"
    override fun values(): Map<String, Any> = mapOf("code" to code)
}
