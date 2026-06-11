package aurum.lang.compiler.frontend.stages.optimization

import aurum.lang.ir.*

/**
 * Removes assignments to locals that are never read (non-SSA liveness).
 */
object DeadCodeElimination : Optimizer {
    override val minOptLevel: Int = 1

    override fun optimize(context: OptimizationContext): Boolean {
        val instructions = context.instructions
        var changed = false

        repeat(2) {
            val live = collectLiveVariables(instructions)
            val retained = mutableListOf<Instruction>()

            for (inst in instructions) {
                val defined = definedVariable(inst)
                if (defined != null && defined !in live && !hasSideEffects(inst)) {
                    changed = true
                    continue
                }

                if (defined != null && defined !in live && hasSideEffects(inst)) {
                    retained += when (inst) {
                        is Call -> inst.copy(target = Reference.Empty)
                        is CallMethod -> inst.copy(target = Reference.Empty)
                        is CallVirtual -> inst.copy(target = Reference.Empty)
                        else -> inst
                    }
                    changed = true
                    continue
                }

                retained += inst
            }

            if (changed) {
                instructions.clear()
                instructions.addAll(retained)
            }
        }

        return changed
    }

    private fun collectLiveVariables(instructions: List<Instruction>): Set<String> {
        val live = mutableSetOf<String>()
        instructions.forEach { inst ->
            forEachRValueUse(inst) { ref ->
                namedVariable(ref)?.let(live::add)
            }
        }
        return live
    }
}
