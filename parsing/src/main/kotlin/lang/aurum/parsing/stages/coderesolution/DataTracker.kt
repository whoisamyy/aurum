package lang.aurum.parsing.stages.coderesolution

import lang.aurum.ir.Reference
import lang.aurum.ir.Target
import lang.aurum.ir.TargetRef

class DataTracker(compiler: IRCompiler) {
    val variableMap: MutableMap<String, TargetRef> = mutableMapOf()

    fun track(variable: String, ref: TargetRef) {
        variableMap[variable] = ref
    }

    fun track(variable: Variable, ref: TargetRef) = track(variable.name, ref)
    fun track(variable: Reference, ref: TargetRef) = track(variable.name, ref)
    fun track(variable: Target, ref: TargetRef) = track(variable.name, ref)

    operator fun get(variable: String): TargetRef? {
        return variableMap[variable]
    }

    operator fun get(variable: Variable) = get(variable.name)
    operator fun get(variable: Reference) = get(variable.name)
    operator fun get(variable: Target) = get(variable.name)
}
