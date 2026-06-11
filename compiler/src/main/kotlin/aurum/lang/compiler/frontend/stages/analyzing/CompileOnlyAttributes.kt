package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.compiler.frontend.stages.typeresolving.AbstractTypeResolver
import aurum.lang.model.Attribute

sealed class CompileOnlyAttribute : Attribute {
    final override fun isVisible(): Boolean = false

    override fun values(): Map<String, Any?> = mapOf()
}

data class DefaultValueAttribute (
    val value: ASTNode.Expression
) : CompileOnlyAttribute() {
    override fun name(): String = "DefaultValue"
}

data class CodeBlockAttribute (
    val codeBlock: ASTNode.CodeBlock,
    val typeResolver: AbstractTypeResolver
) : CompileOnlyAttribute() {
    override fun name(): String = "CodeBlock"
}

object OperatorTemplate : CompileOnlyAttribute() {
    override fun name(): String = "OperatorTemplate"
}

object GeneratedClassAttribute : CompileOnlyAttribute() {
    override fun name(): String = "GeneratedClass"
}