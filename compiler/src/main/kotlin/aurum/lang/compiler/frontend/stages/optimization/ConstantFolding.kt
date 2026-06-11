package aurum.lang.compiler.frontend.stages.optimization

import aurum.lang.ir.*

/**
 * Forward constant propagation and folding on non-SSA IR.
 *
 * Tracks known numeric constants for [Reference.Named] variables and replaces
 * arithmetic with [Move] when both operands are known.
 */
object ConstantFolding : Optimizer {
    override val minOptLevel: Int = 0

    override fun optimize(context: OptimizationContext): Boolean {
        val pool = context.constantPool
        val instructions = context.instructions
        var changed = false
        val constants = mutableMapOf<String, Number>()

        fun resolveConstant(ref: RValue): Number? = when (ref) {
            is Reference.Named -> constants[ref.name]
            is ConstantPoolRef -> pool.dereference<Any>(ref) as? Number
            else -> null
        }

        fun invalidate(target: LValue) {
            invalidateNamedBindings(target, constants)
        }

        for (index in instructions.indices) {
            when (val inst = instructions[index]) {
                is BinaryOp -> {
                    val left = resolveConstant(inst.left)
                    val right = resolveConstant(inst.right)
                    if (left != null && right != null) {
                        @Suppress("UNCHECKED_CAST")
                        val folded = ConstantEvaluator.foldBinary(inst.opcode, left, right)
                        if (folded != null) {
                            val target = inst.target
                            val constRef = pool.getConstant(folded)
                            instructions[index] = Move(target, constRef)
                            if (folded is Number) {
                                namedVariable(target)?.let { constants[it] = folded }
                            }
                            changed = true
                            continue
                        }
                    }
                    invalidate(inst.target)
                }

                is Neg -> {
                    invalidate(inst.target)
                    val value = resolveConstant(inst.ref)
                    if (value != null) {
                        val negated = ConstantEvaluator.foldNeg(value)
                        if (negated != null) {
                            val target = inst.target
                            instructions[index] = Move(target, pool.getConstant(negated))
                            namedVariable(target)?.let { constants[it] = negated }
                            changed = true
                            continue
                        }
                    }
                }

                is Move -> {
                    invalidate(inst.target)
                    val value = resolveConstant(inst.ref)
                    namedVariable(inst.target)?.let { name ->
                        if (value != null) constants[name] = value
                    }
                }

                is Instruction.WithAssignment -> invalidate(inst.target)
                else -> Unit
            }
        }

        return changed
    }
}
