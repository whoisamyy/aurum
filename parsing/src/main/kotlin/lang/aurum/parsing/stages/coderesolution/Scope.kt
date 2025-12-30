package lang.aurum.parsing.stages.coderesolution

import lang.aurum.ir.Label
import lang.aurum.model.Type
import lang.aurum.parsing.model.MutableMethod

abstract class AbstractScope(
    val name: String,
    val parentScope: AbstractScope? = null
) {
    val startLabel = Label("${name}_start")
    val endLabel = Label("${name}_end")

    val variables: MutableMap<String, Variable> = mutableMapOf()
    val typeDecls: MutableMap<String, Type> = mutableMapOf()

    open operator fun plusAssign(variable: Variable) {
        variables += variable.name to variable
    }

    open operator fun plusAssign(variables: Iterable<Variable>) {
        this.variables += variables.map { it.name to it }
    }

    open operator fun contains(string: String): Boolean = variables.contains(string)

    open operator fun get(string: String): Variable? = parentScope?.get(string) ?: variables[string]

    open fun getType(string: String): Type? = parentScope?.getType(string) ?: typeDecls[string]
}

class Scope(name: String, parentScope: AbstractScope? = null) : AbstractScope(name, parentScope)

class LambdaScope(method: MutableMethod, parentScope: AbstractScope? = null) : AbstractScope(method.name, parentScope) {
    val capturedVariables: MutableMap<String, Variable> = mutableMapOf()

    override operator fun get(string: String): Variable? {
        val variable = parentScope?.get(string)
        if (variable != null) {
            capturedVariables += string to variable
            return variable
        } else {
            return variables[string]
        }
    }
}