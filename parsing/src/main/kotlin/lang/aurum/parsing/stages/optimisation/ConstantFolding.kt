package lang.aurum.parsing.stages.optimisation

import lang.aurum.ir.*
import lang.aurum.parsing.stages.FileContext

object ConstantFolding : OptimizationPass {
    override fun run(fileCtx: FileContext, instructions: MutableList<Instruction>): Boolean {
        var changed = false
        val consts = mutableMapOf<Any, Any>()
        val constantPool = fileCtx.constantPool
        consts += constantPool.constantPool

        for (i in instructions.indices) {
            val inst = instructions[i]
            when (inst) {
                is Move -> {
                    val target = inst.target
                    val ref = if (inst.ref !is ConstRef<*>) inst.ref.toString() else inst.ref
                    if (ref is Reference) {
                        consts[target.name] = consts[ref.name]!!
                        continue
                    }
                    if (consts.keys.containsRef(ref)) {
                        consts += target.name to consts[ref]!!
                        consts += ref to consts[ref]!!
                        continue
                    }
                    if (ref is ConstantPoolRef) {
                        consts += target.name to constantPool.dereference(ref.ref.toInt())
                    }
                }
                is BinaryOp -> {
                    val leftRef = if (inst.left !is ConstRef<*>) inst.left.toString() else inst.left
                    val rightRef = if (inst.right !is ConstRef<*>) inst.right.toString() else inst.right
                    val left = if (consts.keys.containsRef(leftRef)) consts[leftRef] else null
                    val right = if (consts.keys.containsRef(rightRef)) consts[rightRef] else null
                    if (left is Number && right is Number) {
                        val result = when (inst.opcode) {
                            Opcode.Add -> left.toDouble() + right.toDouble()
                            Opcode.Sub -> left.toDouble() - right.toDouble()
                            Opcode.Mul -> left.toDouble() * right.toDouble()
                            Opcode.Div -> left.toDouble() / right.toDouble()
                            Opcode.Mod -> left.toDouble() % right.toDouble()
                            Opcode.And -> left.toLong() and right.toLong()
                            Opcode.Or -> left.toLong() or right.toLong()
                            Opcode.Xor -> left.toLong() xor right.toLong()
                            Opcode.Shl -> left.toLong() shl right.toInt()
                            Opcode.Shr -> left.toLong() shr right.toInt()
                            Opcode.Ushr -> left.toLong() ushr right.toInt()
                            else -> null
                        }
                        if (result != null) {
                            consts[inst.target.name] = result
                            instructions[i] = Move(inst.target, constantPool.getConstant(result))
                            changed = true
                        }
                    } else if (left is Number) {
                        instructions[i] = BinaryOp(
                            inst.target,
                            constantPool.getConstant(left.toDouble()),
                            inst.right,
                            inst.operator
                        )
                    } else if (right is Number) {
                        instructions[i] = BinaryOp(
                            inst.target,
                            inst.left,
                            constantPool.getConstant(right.toDouble()),
                            inst.operator
                        )
                    }
                }
                else -> {}
            }
        }
        return changed
    }

    private fun MutableSet<Any>.containsRef(ref: Any): Boolean {
        if (ref is Reference)
            return ref.name in this
        return ref in this
    }
}