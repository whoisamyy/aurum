package aurum.lang.compiler.frontend.stages.optimization

import aurum.lang.ir.ConstantPool
import aurum.lang.ir.Instruction

/**
 * Per-method state for IR optimization passes.
 */
class OptimizationContext(
    val constantPool: ConstantPool,
    val instructions: MutableList<Instruction>,
    val level: Int,
)
