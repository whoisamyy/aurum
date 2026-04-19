package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.model.Attribute

sealed class CompileOnlyAttribute : Attribute {
    final override fun isVisible(): Boolean = false
}

data class DefaultValueAttribute (
    val value: ASTNode.Expression
) : CompileOnlyAttribute() {
    override fun name(): String = "DefaultValue"

    override fun values(): Map<String, Any?> = mapOf("value" to value)
}

data class CodeBlockAttribute (
    val codeBlock: ASTNode.CodeBlock
) : CompileOnlyAttribute() {
    override fun name(): String = "CodeBlock"

    override fun values(): Map<String, Any?> = mapOf("codeBlock" to codeBlock)
}

object OperatorTemplate : CompileOnlyAttribute() {
    override fun name(): String = "OperatorTemplate"

    override fun values(): Map<String, Any?> = mapOf()
}