package lang.aurum.parsing.stages.optimisation

import lang.aurum.ir.*
import lang.aurum.parsing.stages.FileContext

object CallOnClosure : OptimizationPass {
    override fun run(
        fileCtx: FileContext,
        instructions: MutableList<Instruction>
    ): Boolean {
        var changed = false

        for ((i, inst) in instructions.withIndex()) {
            when (inst) {
                is CallMethod -> {
                    val obj = inst.obj
                    if (obj !is MethodGroupRef)
                        continue

                    instructions[i] = Call(inst.target, inst.method, inst.args)
                    changed = true
                }
                is CallVirtual -> {
                    val obj = inst.obj
//                    for (j in instructions.indices) {
//
//                    }
                    if (obj !is MethodGroupRef)
                        continue

                    instructions[i] = Call(inst.target, inst.method, inst.args)
                    changed = true
                }
            }
        }

        return changed
    }
}