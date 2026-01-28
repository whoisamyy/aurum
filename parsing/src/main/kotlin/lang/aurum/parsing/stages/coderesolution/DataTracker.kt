package lang.aurum.parsing.stages.coderesolution

import lang.aurum.ir.LValue
import lang.aurum.ir.Reference
import lang.aurum.ir.TargetRef

class DataTracker(compiler: IRCompiler) {
    val variableMap: MutableMap<LValue, TargetRef> = mutableMapOf()

    fun track(variable: LValue, ref: TargetRef) {
        variableMap[variable] = ref
    }

    fun track(variable: String, ref: TargetRef) {
        val newVariable = when (variable) {
            "_" -> Reference.Empty
            "this", "super" -> throw IllegalStateException("todo")
            else -> Reference.Named(variable)
        }
        variableMap[newVariable] = ref
    }

    fun track(variable: Variable, ref: TargetRef) = track(variable.toLValue(), ref)
    fun track(variable: Reference, ref: TargetRef) = track(variable.name, ref)

    operator fun get(variable: String): TargetRef? {
        val newVariable = when (variable) {
            "_" -> Reference.Empty
            "this", "super" -> throw IllegalStateException("todo")
            else -> Reference.Named(variable)
        }

        return variableMap[newVariable]
    }

    operator fun get(variable: Variable): TargetRef? = get(variable.toLValue())
    operator fun get(variable: Reference): TargetRef? = get(variable.name)
    operator fun get(variable: LValue): TargetRef? = variableMap[variable]
}
