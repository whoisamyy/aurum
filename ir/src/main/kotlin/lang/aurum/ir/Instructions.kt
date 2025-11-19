@file:Suppress("ConstPropertyName")

package lang.aurum.ir

import lang.aurum.model.Type
import java.io.DataOutputStream

const val CONSTANT_POOL_ELEMENT_SIZE: Int = 2
const val OPCODE_SIZE: Int = 1 // in bytes

private fun writeConstantPoolRef(ref: UShort, out: DataOutputStream) {
    out.writeShort(ref.toInt())
}

interface Sized : Writable {
    fun size(): Int
}

interface Writable {
    fun write(out: DataOutputStream)
}
interface CodeElement : Sized

sealed interface TargetValue

sealed interface Ref : Sized, TargetValue
object NullRef : Ref {
    override fun size(): Int = 1

    override fun write(out: DataOutputStream) {
        out.writeByte(0)
    }

    override fun toString(): String {
        return "null"
    }
}

interface Value : Ref


data class Target (
    val name: String,
    val usages: Int = 0
) : Sized {
    constructor(ref: Reference) : this(ref.name)
    override fun size(): Int = name.length
    override fun write(out: DataOutputStream) {
        out.writeUTF(name)
    }

    override fun toString(): String {
        return "${name}${if (usages == 0) "" else "_$usages"}"
    }
}

data class Reference (
    val name: String
) : Value {
    constructor(target: Target) : this(target.name)

    override fun size(): Int = name.length
    override fun write(out: DataOutputStream) {
        out.writeUTF(name)
    }

    override fun toString(): String {
        return name
    }
}

interface ConstantPoolRef : Ref {
    val ref: UShort
}

abstract class ConstRef<T>(override val ref: UShort) : Value, ConstantPoolRef {
    override fun size(): Int = CONSTANT_POOL_ELEMENT_SIZE
    override fun write(out: DataOutputStream) {
        writeConstantPoolRef(ref, out)
    }

    override fun toString(): String {
        return "#$ref"
    }
}

class BooleanRef(ref: UShort) : ConstRef<Boolean>(ref)
class ByteRef(ref: UShort) : ConstRef<Byte>(ref)
class ShortRef(ref: UShort) : ConstRef<Short>(ref)
class CharRef(ref: UShort) : ConstRef<Char>(ref)
class IntRef(ref: UShort) : ConstRef<Int>(ref)
class FloatRef(ref: UShort) : ConstRef<Float>(ref)
class LongRef(ref: UShort) : ConstRef<Long>(ref)
class DoubleRef(ref: UShort) : ConstRef<Double>(ref)
class StringRef(ref: UShort) : ConstRef<String>(ref)

class TypeRef(ref: UShort) : ConstRef<Type>(ref)

sealed interface MethodRef : Value

data class SingleMethodRef (
    override val ref: UShort
) : MethodRef, ConstantPoolRef {
    override fun size(): Int = CONSTANT_POOL_ELEMENT_SIZE // bytes
    override fun write(out: DataOutputStream) {
        writeConstantPoolRef(ref, out)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SingleMethodRef

        return ref == other.ref
    }

    override fun hashCode(): Int {
        return ref.hashCode()
    }

    override fun toString(): String = "#$ref"
}

@OptIn(ExperimentalUnsignedTypes::class)
data class MethodGroupRef (
    val refs: UShortArray
) : MethodRef {
    @OptIn(ExperimentalUnsignedTypes::class)
    override fun size(): Int = refs.size * CONSTANT_POOL_ELEMENT_SIZE

    override fun write(out: DataOutputStream) {
        refs.forEach { writeConstantPoolRef(it, out) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as MethodGroupRef

        return refs.contentEquals(other.refs)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + refs.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "(${refs.joinToString(", ") { "#$it" }})"
    }
}

data class FieldRef (
    override val ref: UShort
) : ConstantPoolRef {
    override fun size(): Int = CONSTANT_POOL_ELEMENT_SIZE // bytes
    override fun write(out: DataOutputStream) {
        writeConstantPoolRef(ref, out)
    }

    override fun toString(): String = "#$ref"
}

data class Label (
    val name: String
) : Value {

    override fun size(): Int = name.length

    override fun write(out: DataOutputStream) {
        out.writeUTF(name)
    }

    override fun toString(): String {
        return name
    }
}

abstract class Instruction(open val opcode: Opcode, val code: Int = opcode.ordinal) : CodeElement

data class Null (
    val target: Target
) : Instruction(Opcode.Null), TargetValue {
    override fun size(): Int = OPCODE_SIZE + target.size()

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
    }

    override fun toString(): String {
        return "$target = null"
    }
}

data class Move (
    val target: Target,
    val ref: Ref
) : Instruction(Opcode.Move), TargetValue {
    override fun size(): Int = target.size() + ref.size()

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        ref.write(out)
    }

    override fun toString(): String {
        return "$target = $ref"
    }
}

data class BinaryOp (
    val target: Target,
    val left: Ref,
    val right: Ref,
    val operator: BinaryOperator
) : Instruction(operator.defaultOpcode!!), TargetValue {
    override fun size(): Int = target.size() + left.size() + right.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        left.write(out)
        right.write(out)
    }

    override fun toString(): String {
        return "$target = $left ${operator.symbol} $right"
    }
}

data class Neg (
    val target: Target,
    val ref: Ref
) : Instruction(Opcode.Neg), TargetValue {
    override fun size(): Int = target.size() + ref.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        ref.write(out)
    }

    override fun toString(): String {
        return "$target = !$ref"
    }
}

data class Jump (
    val label: Label
) : Instruction(Opcode.Jump) {
    override fun size(): Int = label.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        label.write(out)
    }

    override fun toString(): String {
        return "jump $label"
    }
}

data class JumpIf (
    val cond: Value,
    val label: Label
) : Instruction(Opcode.JumpIf) {
    override fun size(): Int = cond.size() + label.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        cond.write(out)
        label.write(out)
    }

    override fun toString(): String {
        return "if $cond then $label"
    }
}

data class Return (
    val value: Ref? = null
) : Instruction(Opcode.Return) {
    override fun size(): Int = value?.size() ?: 0
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        value?.write(out)
    }

    override fun toString(): String {
        return "return ${value ?: ""}"
    }
}

data class Throw (
    val ref: Value
) : Instruction(Opcode.Throw) {
    override fun size(): Int = ref.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        ref.write(out)
    }

    override fun toString(): String {
        return "throw $ref"
    }
}

data class TryBegin (
    val labelCatch: Label,
    val labelFinally: Label? = null
) : Instruction(Opcode.TryBegin) {
    override fun size(): Int = labelCatch.size() + (labelFinally?.size() ?: 0)
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        labelCatch.write(out)
        labelFinally?.write(out)
    }

    override fun toString(): String {
        return "try $labelCatch ${labelFinally ?: ""}"
    }
}

class TryEnd : Instruction(Opcode.TryEnd) {
    override fun size(): Int = 0
    override fun write(out: DataOutputStream) = out.writeByte(code)
    override fun toString(): String {
        return "end"
    }
}

data class Catch (
    val exceptionVar: Target,
    val labelEnd: Label
) : Instruction(Opcode.Catch) {
    override fun size(): Int = exceptionVar.size() + labelEnd.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        exceptionVar.write(out)
        labelEnd.write(out)
    }

    override fun toString(): String {
        return "catch $exceptionVar $labelEnd"
    }
}

data class Call (
    val target: Target,
    val method: MethodRef,
    val args: List<Ref>
) : Instruction(Opcode.Call), TargetValue {
    override fun size(): Int = target.size() + method.size() + args.sumOf { it.size() }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        method.write(out)
        out.writeShort(args.size)
        args.forEach { it.write(out) }
    }

    override fun toString(): String {
        return "$target = $method(${args.joinToString(", ")})"
    }
}

data class CallMethod (
    val target: Target,
    val obj: Value,
    val method: MethodRef,
    val args: List<Ref>
) : Instruction(Opcode.CallMethod), TargetValue {
    override fun size(): Int = target.size() + obj.size() + method.size() + args.sumOf { it.size() }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        obj.write(out)
        method.write(out)
        out.writeShort(args.size)
        args.forEach { it.write(out) }
    }

    override fun toString(): String {
        return "$target = $obj.$method(${args.joinToString(", ")})"
    }
}

data class CallVirtual (
    val target: Target,
    val obj: Value,
    val method: MethodRef,
    val args: List<Ref>
) : Instruction(Opcode.CallVirtual), TargetValue {
    override fun size(): Int = 2 + target.size() + obj.size() + method.size() + args.sumOf { it.size() }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        obj.write(out)
        method.write(out)
        out.writeShort(args.size)
        args.forEach { it.write(out) }
    }

    override fun toString(): String {
        return "$target = $obj.$method(${args.joinToString(", ")})"
    }
}

data class InvokeConstructor (
    val target: Target,
    val obj: Value,
    val args: List<Ref>
) : Instruction(Opcode.InvokeConstructor), TargetValue {
    override fun size(): Int = target.size() + obj.size() + args.sumOf { it.size() }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        obj.write(out)
        out.writeShort(args.size)
        args.forEach { it.write(out) }
    }

    override fun toString(): String {
        return "$target = $obj.<init>(${args.joinToString(", ")})"
    }
}

data class Closure (
    val target: Target,
    val func: MethodRef,
    val captured: List<Ref>
) : Instruction(Opcode.Closure), TargetValue {
    override fun size(): Int = target.size() + func.size() + captured.sumOf { it.size() }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        func.write(out)
        out.writeShort(captured.size)
        captured.forEach { it.write(out) }
    }

    override fun toString(): String {
        return "$target = closure $func ${captured.joinToString(", ")}"
    }
}

data class New (
    val target: Target,
    val classRef: TypeRef
) : Instruction(Opcode.New), TargetValue {
    override fun size(): Int = target.size() + classRef.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        classRef.write(out)
    }

    override fun toString(): String {
        return "$target = new $classRef"
    }
}

data class NewArray (
    val target: Target,
    val elementType: TypeRef,
    val sizeRef: Value
) : Instruction(Opcode.NewArray), TargetValue {
    override fun size(): Int = target.size() + elementType.size() + sizeRef.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        elementType.write(out)
        sizeRef.write(out)
    }

    override fun toString(): String {
        return "$target = $elementType[$sizeRef]"
    }
}

data class GetField (
    val target: Target,
    val obj: Ref,
    val field: FieldRef
) : Instruction(Opcode.GetField), TargetValue {
    override fun size(): Int = target.size() + obj.size() + field.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        obj.write(out)
        field.write(out)
    }

    override fun toString(): String {
        return "$target = $obj.$field"
    }
}

data class PutField (
    val obj: Ref,
    val field: FieldRef,
    val value: Ref
) : Instruction(Opcode.PutField) {
    override fun size(): Int = obj.size() + field.size() + value.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        obj.write(out)
        field.write(out)
        value.write(out)
    }

    override fun toString(): String {
        return "$obj.$field = $value"
    }
}

data class GetMethod (
    val target: Target,
    val obj: Ref,
    val method: MethodRef
) : Instruction(Opcode.GetMethod), TargetValue {
    override fun size(): Int = target.size() + method.size()

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        obj.write(out)
        method.write(out)
    }

    override fun toString(): String {
        return "$target = $obj.$method"
    }
}

data class GetMethodStatic (
    val target: Target,
    val method: MethodRef
) : Instruction(Opcode.GetMethodStatic), TargetValue {
    override fun size(): Int = target.size() + method.size()

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        method.write(out)
    }

    override fun toString(): String {
        return "$target = $method"
    }
}

data class GetStatic (
    val target: Target,
    val field: FieldRef
) : Instruction(Opcode.GetStatic), TargetValue {
    override fun size(): Int = target.size() + field.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        field.write(out)
    }

    override fun toString(): String {
        return "$target = $field"
    }
}

data class PutStatic (
    val field: FieldRef,
    val value: Ref
) : Instruction(Opcode.PutStatic) {
    override fun size(): Int = field.size() + value.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        field.write(out)
        value.write(out)
    }

    override fun toString(): String {
        return "$field = $value"
    }
}

/// Get value from array
data class ArrayLoad (
    val target: Target,
    val array: Value,
    val index: Value
) : Instruction(Opcode.ArrayLoad), TargetValue {
    override fun size(): Int = target.size() + array.size() + index.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        array.write(out)
        index.write(out)
    }

    override fun toString(): String {
        return "$target = $array[$index]"
    }
}

data class ArrayStore (
    val array: Value,
    val index: Value,
    val value: Value
) : Instruction(Opcode.ArrayStore) {
    override fun size(): Int = array.size() + index.size() + value.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        array.write(out)
        index.write(out)
        value.write(out)
    }

    override fun toString(): String {
        return "$array[$index] = $value"
    }
}

data class Cast (
    val target: Target,
    val ref: Ref,
    val type: TypeRef
) : Instruction(Opcode.Cast), TargetValue {
    override fun size(): Int = target.size() + ref.size() + type.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        ref.write(out)
        type.write(out)
    }

    override fun toString(): String {
        return "$target = $ref as $type"
    }
}

data class InstanceOf (
    val target: Target,
    val ref: Ref,
    val type: TypeRef
) : Instruction(Opcode.InstanceOf), TargetValue {
    override fun size(): Int = target.size() + ref.size() + type.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        ref.write(out)
        type.write(out)
    }

    override fun toString(): String {
        return "$target = $ref is $type"
    }
}

data class TypeOf (
    val target: Target,
    val ref: Ref
) : Instruction(Opcode.TypeOf), TargetValue {
    override fun size(): Int = target.size() + ref.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        ref.write(out)
    }

    override fun toString(): String {
        return "$target = typeof($ref)"
    }
}

data class LabelInst (
    val label: Label
) : Instruction(Opcode.Label) {
    override fun size(): Int = label.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        label.write(out)
    }

    override fun toString(): String {
        return "$label:"
    }
}

data class Switch (
    val ref: Ref,
    val cases: Map<Ref, Label>, // case : label
    val defaultLabel: Label
) : Instruction(Opcode.Switch) {
    override fun size(): Int = ref.size() + cases.entries.sumOf { 4 + it.value.size() } + defaultLabel.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        ref.write(out)
        out.writeShort(cases.size)
        for ((value, label) in cases) {
            value.write(out)
            label.write(out)
        }
        defaultLabel.write(out)
    }

    override fun toString(): String {
        return "switch $ref ${cases.toList().joinToString("\n    ", prefix = "\n    ") { "${it.first} : ${it.second}" }}"
    }
}

data class Phi (
    val target: Target,
    val incoming: Map<Label, Reference>
) : Instruction(Opcode.Phi), TargetValue {
    override fun size(): Int = target.size() + incoming.entries.sumOf { it.key.size() + it.value.size() }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        out.writeShort(incoming.size)
        for ((label, ref) in incoming) {
            label.write(out)
            ref.write(out)
        }
    }

    override fun toString(): String {
        return "$target = phi(${incoming.toList().joinToString("\n  ") { "${it.first}: ${it.second}" }})"
    }
}

object Nop : Instruction(Opcode.Nop) {
    override fun size(): Int = 1

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
    }

    override fun toString(): String {
        return "nop"
    }
}