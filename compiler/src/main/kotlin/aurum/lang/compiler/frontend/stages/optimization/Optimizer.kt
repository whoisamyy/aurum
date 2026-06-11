package aurum.lang.compiler.frontend.stages.optimization

import aurum.lang.ir.ConstantPool
import aurum.lang.ir.Instruction

/**
 * A single IR optimization pass over one method body.
 */
interface Optimizer {
    /** Minimum [CompilationData.optimizationLevel] required (0–3). */
    val minOptLevel: Int

    fun optimize(context: OptimizationContext): Boolean

    fun run(constantPool: ConstantPool, instructions: MutableList<Instruction>): Boolean =
        optimize(OptimizationContext(constantPool, instructions, level = minOptLevel))
}
