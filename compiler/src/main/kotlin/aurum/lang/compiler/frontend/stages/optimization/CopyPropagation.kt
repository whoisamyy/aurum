package aurum.lang.compiler.frontend.stages.optimization

import aurum.lang.ir.Instruction
import aurum.lang.ir.Move
import aurum.lang.ir.RValue
import aurum.lang.ir.Reference

/**
 * Local copy propagation for non-SSA IR: `y = x` followed by uses of `y` become uses of `x`.
 */
object CopyPropagation : Optimizer {
    override val minOptLevel: Int = 0

    override fun optimize(context: OptimizationContext): Boolean {
        val instructions = context.instructions
        var changed = false
        val copies = mutableMapOf<String, RValue>()

        fun substitute(ref: RValue): RValue {
            if (ref !is Reference.Named) return ref
            return copies[ref.name] ?: ref
        }

        for (index in instructions.indices) {
            val inst = instructions[index]
            val rewritten = mapInstructionOperands(inst, ::substitute)
            if (rewritten !== inst) {
                instructions[index] = rewritten
                changed = true
            }

            when (val current = instructions[index]) {
                is Move -> {
                    val target = current.target
                    val source = substitute(current.ref)
                    invalidateNamedBindings(target, copies)
                    val targetName = namedVariable(target)
                    val sourceName = namedVariable(source)
                    if (targetName != null &&
                        sourceName != null &&
                        sourceName != targetName
                    ) {
                        copies[targetName] = source
                    }
                }

                is Instruction.WithAssignment -> invalidateNamedBindings(current.target, copies)
                else -> Unit
            }
        }

        return changed
    }
}
