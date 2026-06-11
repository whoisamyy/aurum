package aurum.lang.compiler.frontend.stages.optimization

import aurum.lang.ir.Opcode

/**
 * Constant-folds primitive numeric operations at compile time.
 */
internal object ConstantEvaluator {
    fun foldBinary(opcode: Opcode, left: Number, right: Number): Any? = when (opcode) {
        Opcode.Add -> left.binary(right, ::widenPlus)
        Opcode.Sub -> left.binary(right, ::widenMinus)
        Opcode.Mul -> left.binary(right, ::widenTimes)
        Opcode.Div -> left.binary(right, ::widenDiv)
        Opcode.Mod -> left.binary(right, ::widenRem)
        Opcode.And -> left.binary(right, ::widenAnd)
        Opcode.Or -> left.binary(right, ::widenOr)
        Opcode.Xor -> left.binary(right, ::widenXor)
        Opcode.Shl -> left.binary(right, ::widenShl)
        Opcode.Shr -> left.binary(right, ::widenShr)
        Opcode.Ushr -> left.binary(right, ::widenUshr)
        Opcode.CmpEq -> left.compareNumeric(right) == 0
        Opcode.CmpNe -> left.compareNumeric(right) != 0
        Opcode.CmpLt -> left.compareNumeric(right) < 0
        Opcode.CmpLe -> left.compareNumeric(right) <= 0
        Opcode.CmpGt -> left.compareNumeric(right) > 0
        Opcode.CmpGe -> left.compareNumeric(right) >= 0
        else -> null
    }

    fun foldNeg(value: Number): Number? = when (value) {
        is Byte -> (-value).toByte()
        is Short -> (-value).toShort()
        is Int -> -value
        is Long -> -value
        is Float -> -value
        is Double -> -value
        else -> null
    }
}

private fun Number.castPrecedence(): Int = when (this) {
    is Byte -> 1
    is Short -> 2
    is Int -> 3
    is Long -> 4
    is Float -> 5
    is Double -> 6
    else -> error("unsupported numeric type: ${this::class}")
}

private fun Number.compareNumeric(other: Number): Int = when {
    this is Double || other is Double -> this.toDouble().compareTo(other.toDouble())
    this is Float || other is Float -> this.toFloat().compareTo(other.toFloat())
    this is Long || other is Long -> this.toLong().compareTo(other.toLong())
    else -> this.toInt().compareTo(other.toInt())
}

/** Applies [op] with JVM binary numeric promotion (wider type wins). */
private inline fun Number.binary(other: Number, op: (Number, Number) -> Number): Number =
    if (castPrecedence() >= other.castPrecedence()) op(this, other) else op(other, this)

private fun widenPlus(primary: Number, secondary: Number): Number = when (primary) {
    is Byte -> (primary + secondary.toByte()).toByte()
    is Short -> (primary + secondary.toShort()).toShort()
    is Int -> primary + secondary.toInt()
    is Long -> primary + secondary.toLong()
    is Float -> primary + secondary.toFloat()
    is Double -> primary + secondary.toDouble()
    else -> error("unsupported")
}

private fun widenMinus(primary: Number, secondary: Number): Number = when (primary) {
    is Byte -> (primary - secondary.toByte()).toByte()
    is Short -> (primary - secondary.toShort()).toShort()
    is Int -> primary - secondary.toInt()
    is Long -> primary - secondary.toLong()
    is Float -> primary - secondary.toFloat()
    is Double -> primary - secondary.toDouble()
    else -> error("unsupported")
}

private fun widenTimes(primary: Number, secondary: Number): Number = when (primary) {
    is Byte -> (primary * secondary.toByte()).toByte()
    is Short -> (primary * secondary.toShort()).toShort()
    is Int -> primary * secondary.toInt()
    is Long -> primary * secondary.toLong()
    is Float -> primary * secondary.toFloat()
    is Double -> primary * secondary.toDouble()
    else -> error("unsupported")
}

private fun widenDiv(primary: Number, secondary: Number): Number = when (primary) {
    is Byte -> (primary / secondary.toByte()).toByte()
    is Short -> (primary / secondary.toShort()).toShort()
    is Int -> primary / secondary.toInt()
    is Long -> primary / secondary.toLong()
    is Float -> primary / secondary.toFloat()
    is Double -> primary / secondary.toDouble()
    else -> error("unsupported")
}

private fun widenRem(primary: Number, secondary: Number): Number = when (primary) {
    is Byte -> (primary % secondary.toByte()).toByte()
    is Short -> (primary % secondary.toShort()).toShort()
    is Int -> primary % secondary.toInt()
    is Long -> primary % secondary.toLong()
    is Float -> primary % secondary.toFloat()
    is Double -> primary % secondary.toDouble()
    else -> error("unsupported")
}

private fun widenAnd(primary: Number, secondary: Number): Number = when (primary) {
    is Long -> primary and secondary.toLong()
    else -> primary.toInt() and secondary.toInt()
}

private fun widenOr(primary: Number, secondary: Number): Number = when (primary) {
    is Long -> primary or secondary.toLong()
    else -> primary.toInt() or secondary.toInt()
}

private fun widenXor(primary: Number, secondary: Number): Number = when (primary) {
    is Long -> primary xor secondary.toLong()
    else -> primary.toInt() xor secondary.toInt()
}

private fun widenShl(primary: Number, secondary: Number): Number = when (primary) {
    is Long -> primary shl secondary.toInt()
    else -> primary.toInt() shl secondary.toInt()
}

private fun widenShr(primary: Number, secondary: Number): Number = when (primary) {
    is Long -> primary shr secondary.toInt()
    else -> primary.toInt() shr secondary.toInt()
}

private fun widenUshr(primary: Number, secondary: Number): Number = when (primary) {
    is Long -> primary ushr secondary.toInt()
    else -> primary.toInt() ushr secondary.toInt()
}
