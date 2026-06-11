package aurum.lang.compiler.backend.ir

import aurum.lang.ir.Instruction
import aurum.lang.ir.Label
import aurum.lang.ir.RValue
import aurum.lang.ir.Reference
import aurum.lang.model.Type
import aurum.lang.model.Types

/**
 * Typed result of compiling an expression to IR.
 *
 * - [Const] — operand is already an [RValue] (literal, `this`, method/type ref, …).
 * - [Variable] — operand lives in a named local slot; use [Variable.reference] as the IR target.
 */
internal sealed class Value {
    abstract val type: Type
    abstract var producer: Instruction.WithAssignment?

    abstract fun ref(): RValue

    class Const(
        val operand: RValue,
        override val type: Type,
        override var producer: Instruction.WithAssignment? = null,
    ) : Value() {
        override fun ref(): RValue = operand
    }

    class Variable(
        val name: String,
        override var type: Type,
        override var producer: Instruction.WithAssignment? = null,
    ) : Value() {
        val reference: Reference.Named = Reference.Named(name)

        override fun ref(): RValue = reference
    }

    companion object {
        operator fun invoke(operand: RValue, type: Type, producer: Instruction.WithAssignment? = null): Const =
            Const(operand, type, producer)

        val DISCARD: Variable = Variable("_", Types.VOID)
    }
}

internal typealias Variable = Value.Variable

internal fun List<Value>.refs(): List<RValue> = map { it.ref() }

internal open class Scope(
    val name: String,
    val parent: Scope? = null,
    protected val variables: MutableMap<String, Variable> = mutableMapOf(),
) {
    val startLabel: Label = Label("$name#start")
    val endLabel: Label = Label("$name#end")

    operator fun set(key: String, value: Variable) {
        variables[key] = value
    }

    operator fun plusAssign(variable: Variable) {
        variables += variable.name to variable
    }

    open operator fun get(key: String): Variable? {
        return parent?.get(key) ?: variables[key]
    }

    operator fun contains(key: String): Boolean {
        return parent?.contains(key) ?: false || (key in variables)
    }
}

internal class LambdaScope(
    name: String,
    parent: Scope?,
    variables: MutableMap<String, Variable> = mutableMapOf(),
) : Scope(name, parent, variables) {
    val captures: MutableMap<String, Variable> = mutableMapOf()

    override operator fun get(key: String): Variable? {
        return parent?.get(key)?.also { captures += key to it } ?: variables[key]
    }
}
