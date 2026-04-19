package aurum.lang.compiler.frontend.stages.optimisation
//
//import aurum.lang.ir.*
//import aurum.lang.compiler.frontend.stages.FileContext
//
//object ConstantFolding : OptimizationPass {
//    override fun run(fileCtx: FileContext, instructions: MutableList<Instruction>): Boolean {
//        var changed = false
//        val constantPool = fileCtx.constantPool
//        val constants = mutableMapOf<String, Number>()
//
//        fun getConstantValue(rvalue: RValue): Number? {
//            return when (rvalue) {
//                is Reference.Named -> null // constants[rvalue.name]
//                is ConstantPoolRef -> {
//                    @Suppress("UNCHECKED_CAST")
//                    val value = constantPool.dereference<Any>(rvalue)
//                    value as? Number
//                }
//                else -> null
//            }
//        }
//
//        fun invalidateVariable(lvalue: LValue) {
//            if (lvalue is Reference.Named) {
//                constants.remove(lvalue.name)
//            }
//        }
//
//        for (i in instructions.indices) {
//            when (val inst = instructions[i]) {
//                is BinaryOp -> {
//                    val left = getConstantValue(inst.left)
//                    val right = getConstantValue(inst.right)
//
//                    if (left != null && right != null) {
//                        // Both operands are constants - fold the operation
//                        val result = when (inst.opcode) {
//                            Opcode.Add -> left + right
//                            Opcode.Sub -> left - right
//                            Opcode.Mul -> left * right
//                            Opcode.Div -> left / right
//                            Opcode.Mod -> left % right
//                            Opcode.And -> left and right
//                            Opcode.Or -> left or right
//                            Opcode.Xor -> left xor right
//                            Opcode.Shl -> left shl right
//                            Opcode.Shr -> left shr right
//                            Opcode.Ushr -> left ushr right
//                            else -> null
//                        }
//
//                        if (result != null) {
//                            // Replace with Move instruction containing the constant
//                            val constRef = constantPool.getConstant(result)
//                            val target = inst.target
//                            instructions[i] = Move(target, constRef)
//
//                            // Track the constant value for the target variable
//                            if (target is Reference.Named) {
//                                constants[target.name] = result
//                            }
//
//                            changed = true
//                        } else {
//                            // Operation not foldable - invalidate target
//                            invalidateVariable(inst.target)
//                        }
//                    } else {
//                        // At least one operand is not constant - invalidate target
//                        invalidateVariable(inst.target)
//                    }
//                }
//
//                is Move -> {
//                    val target = inst.target
//                    invalidateVariable(target)
//
//                    val constValue = getConstantValue(inst.ref)
//                    if (constValue != null && target is Reference.Named) {
//                        constants[target.name] = constValue
//                    } else if (target is Reference.Named) {
//                        constants.remove(target.name)
//                    }
//                }
//
//                is Neg -> {
//                    val target = inst.target
//                    invalidateVariable(target)
//                    val constValue = getConstantValue(inst.ref)
//                    if (constValue != null && target is Reference.Named) {
//                        val result = when (constValue) {
//                            is Byte -> (-constValue).toByte()
//                            is Short -> (-constValue).toShort()
//                            is Int -> -constValue
//                            is Long -> -constValue
//                            is Float -> -constValue
//                            is Double -> -constValue
//                            else -> null
//                        }
//                        if (result != null) {
//                            val constRef = constantPool.getConstant(result)
//                            instructions[i] = Move(target, constRef)
//                            constants[target.name] = result
//                            changed = true
//                        }
//                    }
//                }
//
//                is Instruction.WithAssignment -> {
//                    invalidateVariable(inst.target)
//                }
//
//                else -> {
//                    // Instructions that don't write to variables don't affect constants
//                }
//            }
//        }
//
//        return changed
//    }
//}
//
//private fun Number.castPrecedence(): Int {
//    return when (this) {
//        is Byte -> 1
//        is Short -> 2
//        is Int -> 3
//        is Long -> 4
//        is Float -> 5
//        is Double -> 6
//        else -> throw IllegalStateException("todo")
//    }
//}
//
//private operator fun Number.plus(other: Number): Number {
//    val thisCastPrecedence = this.castPrecedence()
//    val otherCastPrecedence = other.castPrecedence()
//    if (thisCastPrecedence >= otherCastPrecedence) {
//        return when (this) {
//            is Byte -> (this + other.toByte()).toByte()
//            is Short -> (this + other.toShort()).toShort()
//            is Int -> this + other.toInt()
//            is Long -> this + other.toLong()
//            is Float -> this + other.toFloat()
//            is Double -> this + other.toDouble()
//            else -> throw IllegalStateException("todo")
//        }
//    } else {
//        return when (other) {
//            is Byte -> (this.toByte() + other).toByte()
//            is Short -> (this.toShort() + other).toShort()
//            is Int -> this.toInt() + other
//            is Long -> this.toLong() + other
//            is Float -> this.toFloat() + other
//            is Double -> this.toDouble() + other
//            else -> throw IllegalStateException("todo")
//        }
//    }
//}
//
//private operator fun Number.minus(other: Number): Number {
//    val thisCastPrecedence = this.castPrecedence()
//    val otherCastPrecedence = other.castPrecedence()
//    if (thisCastPrecedence >= otherCastPrecedence) {
//        return when (this) {
//            is Byte -> (this - other.toByte()).toByte()
//            is Short -> (this - other.toShort()).toShort()
//            is Int -> this - other.toInt()
//            is Long -> this - other.toLong()
//            is Float -> this - other.toFloat()
//            is Double -> this - other.toDouble()
//            else -> throw IllegalStateException("todo")
//        }
//    } else {
//        return when (other) {
//            is Byte -> (this.toByte() - other).toByte()
//            is Short -> (this.toShort() - other).toShort()
//            is Int -> this.toInt() - other
//            is Long -> this.toLong() - other
//            is Float -> this.toFloat() - other
//            is Double -> this.toDouble() - other
//            else -> throw IllegalStateException("todo")
//        }
//    }
//}
//
//private operator fun Number.times(other: Number): Number {
//    val thisCastPrecedence = this.castPrecedence()
//    val otherCastPrecedence = other.castPrecedence()
//    if (thisCastPrecedence >= otherCastPrecedence) {
//        return when (this) {
//            is Byte -> (this * other.toByte()).toByte()
//            is Short -> (this * other.toShort()).toShort()
//            is Int -> this * other.toInt()
//            is Long -> this * other.toLong()
//            is Float -> this * other.toFloat()
//            is Double -> this * other.toDouble()
//            else -> throw IllegalStateException("todo")
//        }
//    } else {
//        return when (other) {
//            is Byte -> (other * this.toByte()).toByte()
//            is Short -> (other * this.toShort()).toShort()
//            is Int -> other * this.toInt()
//            is Long -> other * this.toLong()
//            is Float -> other * this.toFloat()
//            is Double -> other * this.toDouble()
//            else -> throw IllegalStateException("todo")
//        }
//    }
//}
//
//private operator fun Number.div(other: Number): Number {
//    val thisCastPrecedence = this.castPrecedence()
//    val otherCastPrecedence = other.castPrecedence()
//    if (thisCastPrecedence >= otherCastPrecedence) {
//        return when (this) {
//            is Byte -> (this / other.toByte()).toByte()
//            is Short -> (this / other.toShort()).toShort()
//            is Int -> this / other.toInt()
//            is Long -> this / other.toLong()
//            is Float -> this / other.toFloat()
//            is Double -> this / other.toDouble()
//            else -> throw IllegalStateException("todo")
//        }
//    } else {
//        return when (other) {
//            is Byte -> (this.toByte() / other).toByte()
//            is Short -> (this.toShort() / other).toShort()
//            is Int -> this.toInt() / other
//            is Long -> this.toLong() / other
//            is Float -> this.toFloat() / other
//            is Double -> this.toDouble() / other
//            else -> throw IllegalStateException("todo")
//        }
//    }
//}
//
//private operator fun Number.rem(other: Number): Number {
//    val thisCastPrecedence = this.castPrecedence()
//    val otherCastPrecedence = other.castPrecedence()
//    if (thisCastPrecedence >= otherCastPrecedence) {
//        return when (this) {
//            is Byte -> (this % other.toByte()).toByte()
//            is Short -> (this % other.toShort()).toShort()
//            is Int -> this % other.toInt()
//            is Long -> this % other.toLong()
//            is Float -> this % other.toFloat()
//            is Double -> this % other.toDouble()
//            else -> throw IllegalStateException("todo")
//        }
//    } else {
//        return when (other) {
//            is Byte -> (this.toByte() % other).toByte()
//            is Short -> (this.toShort() % other).toShort()
//            is Int -> this.toInt() % other
//            is Long -> this.toLong() % other
//            is Float -> this.toFloat() % other
//            is Double -> this.toDouble() % other
//            else -> throw IllegalStateException("todo")
//        }
//    }
//}
//
//private infix fun Number.and(other: Number): Number {
//    val thisCastPrecedence = this.castPrecedence()
//    val otherCastPrecedence = other.castPrecedence()
//    return if (thisCastPrecedence >= otherCastPrecedence) {
//        when (this) {
//            is Byte, Short, Int -> this.toInt() and other.toInt()
//            is Long -> this and other.toLong()
//            else -> throw IllegalStateException("todo")
//        }
//    } else {
//        when (other) {
//            is Byte, Short, Int -> this.toInt() and other.toInt()
//            is Long -> this.toLong() and other
//            else -> throw IllegalStateException("todo")
//        }
//    }
//}
//
//private infix fun Number.or(other: Number): Number {
//    val thisCastPrecedence = this.castPrecedence()
//    val otherCastPrecedence = other.castPrecedence()
//    return if (thisCastPrecedence >= otherCastPrecedence) {
//        when (this) {
//            is Byte, Short, Int -> this.toInt() or other.toInt()
//            is Long -> this or other.toLong()
//            else -> throw IllegalStateException("todo")
//        }
//    } else {
//        when (other) {
//            is Byte, Short, Int -> this.toInt() or other.toInt()
//            is Long -> this.toLong() or other
//            else -> throw IllegalStateException("todo")
//        }
//    }
//}
//
//private infix fun Number.xor(other: Number): Number {
//    val thisCastPrecedence = this.castPrecedence()
//    val otherCastPrecedence = other.castPrecedence()
//
//    return if (thisCastPrecedence >= otherCastPrecedence) {
//        when (this) {
//            is Byte, Short, Int -> this.toInt() xor other.toInt()
//            is Long -> this xor other.toLong()
//            else -> throw IllegalStateException("todo")
//        }
//    } else {
//        when (other) {
//            is Byte, Short, Int -> this.toInt() xor other.toInt()
//            is Long -> this.toLong() xor other
//            else -> throw IllegalStateException("todo")
//        }
//    }
//}
//
//private infix fun Number.shl(other: Number): Number {
//    val thisCastPrecedence = this.castPrecedence()
//    val otherCastPrecedence = other.castPrecedence()
//    return if (thisCastPrecedence >= otherCastPrecedence) {
//        when (this) {
//            is Byte, Short, Int -> this.toInt() shl other.toInt()
//            is Long -> this shl other.toLong()
//            else -> throw IllegalStateException("todo")
//        }
//    } else {
//        when (other) {
//            is Byte, Short, Int -> this.toInt() shl other.toInt()
//            is Long -> this.toLong() shl other
//            else -> throw IllegalStateException("todo")
//        }
//    }
//}
//
//private infix fun Number.shr(other: Number): Number {
//    val thisCastPrecedence = this.castPrecedence()
//    val otherCastPrecedence = other.castPrecedence()
//    return if (thisCastPrecedence >= otherCastPrecedence) {
//        when (this) {
//            is Byte, Short, Int -> this.toInt() shr other.toInt()
//            is Long -> this shr other.toLong()
//            else -> throw IllegalStateException("todo")
//        }
//    } else {
//        when (other) {
//            is Byte, Short, Int -> this.toInt() shr other.toInt()
//            is Long -> this.toLong() shr other
//            else -> throw IllegalStateException("todo")
//        }
//    }
//}
//
//private infix fun Number.ushr(other: Number): Number {
//    val thisCastPrecedence = this.castPrecedence()
//    val otherCastPrecedence = other.castPrecedence()
//    return if (thisCastPrecedence >= otherCastPrecedence) {
//        when (this) {
//            is Byte, Short, Int -> this.toInt() ushr other.toInt()
//            is Long -> this ushr other.toLong()
//            else -> throw IllegalStateException("todo")
//        }
//    } else {
//        when (other) {
//            is Byte, Short, Int -> this.toInt() ushr other.toInt()
//            is Long -> this.toLong() ushr other
//            else -> throw IllegalStateException("todo")
//        }
//    }
//}
//
