@file:Suppress("ConstPropertyName")

package lang.aurum.ir

import lang.aurum.model.Field
import lang.aurum.model.Method
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

sealed interface TargetRef

sealed interface RValue : Sized, TargetRef
object NullRef : RValue {
    override fun size(): Int = 1

    override fun write(out: DataOutputStream) {
        out.writeByte(0)
    }

    override fun toString(): String {
        return "null"
    }
}

sealed class Target (
    val name: String
) : Sized {
    constructor(ref: Reference) : this(ref.name)
    override fun size(): Int = name.length
    override fun write(out: DataOutputStream) {
        out.writeUTF(name)
    }

    override fun toString(): String {
        return name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Target

        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    object Empty : Target("_")

    companion object {
        fun Target(name: String): Target {
            return ::Target.invoke(name)
        }
    }
}

data class Reference (
    val name: String
) : RValue {
    constructor(target: Target) : this(target.name)

    override fun size(): Int = name.length
    override fun write(out: DataOutputStream) {
        out.writeUTF(name)
    }

    override fun toString(): String {
        return name
    }
}

interface ConstantPoolRef : RValue {
    var ref: UShort
}

abstract class ConstRef<T>(override var ref: UShort) : ConstantPoolRef {
    override fun size(): Int = CONSTANT_POOL_ELEMENT_SIZE
    override fun write(out: DataOutputStream) {
        writeConstantPoolRef(ref, out)
    }

    final override fun toString(): String {
        return "#$ref"
    }

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConstRef<*>

        return ref == other.ref
    }

    final override fun hashCode(): Int {
        return ref.hashCode()
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

sealed interface MemberRef : RValue, TargetRef

class FieldRef (
    ref: UShort
) : ConstRef<Field>(ref), MemberRef {
    override fun size(): Int = CONSTANT_POOL_ELEMENT_SIZE // bytes
    override fun write(out: DataOutputStream) {
        writeConstantPoolRef(ref, out)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
data class FieldGroupRef (
    val refs: List<FieldRef>
) : MemberRef {

    override fun size(): Int = refs.size * CONSTANT_POOL_ELEMENT_SIZE

    override fun write(out: DataOutputStream) {
        refs.forEach { it.write(out) }
    }

    override fun toString(): String {
        return "(${refs.joinToString(", ") { "#${it.ref}" }})"
    }
}

sealed interface MethodRef : MemberRef

class SingleMethodRef (
    ref: UShort
) : ConstRef<Method>(ref), MethodRef {
    override fun size(): Int = CONSTANT_POOL_ELEMENT_SIZE // bytes

    override fun write(out: DataOutputStream) {
        writeConstantPoolRef(ref, out)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
data class MethodGroupRef (
    val refs: List<SingleMethodRef>
) : MethodRef {

    override fun size(): Int = refs.size * CONSTANT_POOL_ELEMENT_SIZE

    override fun write(out: DataOutputStream) {
        refs.forEach { it.write(out) }
    }

    override fun toString(): String {
        return "(${refs.joinToString(", ") { "#${it.ref}" }})"
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
data class MemberGroupRef (
    val refs: List<MemberRef>
) : MemberRef {
    init {
        if (refs.any { it is MemberGroupRef || it is MethodGroupRef || it is FieldGroupRef })
            throw IllegalStateException("todo")
    }

    override fun size(): Int = refs.size * CONSTANT_POOL_ELEMENT_SIZE

    override fun write(out: DataOutputStream) {
        refs.forEach { it.write(out) }
    }

    override fun toString(): String {
        return "(${refs.joinToString(", ") { "#$it" }})"
    }
}


data class Label (
    val name: String
) : RValue {

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
) : Instruction(Opcode.Null), TargetRef {
    override fun size(): Int = OPCODE_SIZE + target.size()

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
    }
}

data class Move (
    val target: Target,
    val ref: RValue
) : Instruction(Opcode.Move), TargetRef {
    override fun size(): Int = target.size() + ref.size()

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        ref.write(out)
    }
}

data class BinaryOp (
    val target: Target,
    val left: RValue,
    val right: RValue,
    val operator: BinaryOperator
) : Instruction(operator.defaultOpcode!!), TargetRef {
    override fun size(): Int = target.size() + left.size() + right.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        left.write(out)
        right.write(out)
    }
}

data class Neg (
    val target: Target,
    val ref: RValue
) : Instruction(Opcode.Neg), TargetRef {
    override fun size(): Int = target.size() + ref.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        ref.write(out)
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
}

data class JumpIf (
    val cond: RValue,
    val label: Label
) : Instruction(Opcode.JumpIf) {
    override fun size(): Int = cond.size() + label.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        cond.write(out)
        label.write(out)
    }
}

data class Return (
    val value: RValue? = null
) : Instruction(Opcode.Return) {
    override fun size(): Int = value?.size() ?: 0
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        value?.write(out)
    }
}

data class Throw (
    val ref: RValue
) : Instruction(Opcode.Throw) {
    override fun size(): Int = ref.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        ref.write(out)
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
}

class TryEnd : Instruction(Opcode.TryEnd) {
    override fun size(): Int = 0
    override fun write(out: DataOutputStream) = out.writeByte(code)
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
}

data class Call (
    val target: Target,
    val method: MethodRef,
    val args: List<RValue>
) : Instruction(Opcode.Call), TargetRef {
    override fun size(): Int = target.size() + method.size() + args.sumOf { it.size() }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        method.write(out)
        out.writeShort(args.size)
        args.forEach { it.write(out) }
    }
}

data class CallMethod (
    val target: Target,
    val obj: RValue,
    val method: MethodRef,
    val args: List<RValue>
) : Instruction(Opcode.CallMethod), TargetRef {
    override fun size(): Int = target.size() + obj.size() + method.size() + args.sumOf { it.size() }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        obj.write(out)
        method.write(out)
        out.writeShort(args.size)
        args.forEach { it.write(out) }
    }
}

data class CallVirtual (
    val target: Target,
    val obj: RValue,
    val method: MethodRef,
    val args: List<RValue>
) : Instruction(Opcode.CallVirtual), TargetRef {
    override fun size(): Int = 2 + target.size() + obj.size() + method.size() + args.sumOf { it.size() }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        obj.write(out)
        method.write(out)
        out.writeShort(args.size)
        args.forEach { it.write(out) }
    }
}

data class InvokeConstructor (
    val target: Target,
    val obj: RValue,
    val args: List<RValue>
) : Instruction(Opcode.InvokeConstructor), TargetRef {
    override fun size(): Int = target.size() + obj.size() + args.sumOf { it.size() }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        obj.write(out)
        out.writeShort(args.size)
        args.forEach { it.write(out) }
    }
}

data class Closure (
    val target: Target,
    val func: MethodRef,
    val captured: List<RValue>
) : Instruction(Opcode.Closure), TargetRef {
    override fun size(): Int = target.size() + func.size() + captured.sumOf { it.size() }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        func.write(out)
        out.writeShort(captured.size)
        captured.forEach { it.write(out) }
    }
}

data class New (
    val target: Target,
    val classRef: TypeRef
) : Instruction(Opcode.New), TargetRef {
    override fun size(): Int = target.size() + classRef.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        classRef.write(out)
    }
}

data class NewArray (
    val target: Target,
    val elementType: TypeRef,
    val sizeRef: RValue
) : Instruction(Opcode.NewArray), TargetRef {
    override fun size(): Int = target.size() + elementType.size() + sizeRef.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        elementType.write(out)
        sizeRef.write(out)
    }
}

data class GetField (
    val target: Target,
    val obj: RValue,
    val field: FieldRef
) : Instruction(Opcode.GetField), TargetRef {
    override fun size(): Int = target.size() + obj.size() + field.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        obj.write(out)
        field.write(out)
    }
}

data class PutField (
    val obj: RValue,
    val field: FieldRef,
    val value: RValue
) : Instruction(Opcode.PutField) {
    override fun size(): Int = obj.size() + field.size() + value.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        obj.write(out)
        field.write(out)
        value.write(out)
    }
}

data class GetMember (
    val target: Target,
    val obj: RValue,
    val member: MemberRef
) : Instruction(Opcode.GetMember), TargetRef {
    override fun size(): Int = target.size() + obj.size() + member.size()

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        obj.write(out)
        member.write(out)
    }
}

data class GetMethod (
    val target: Target,
    val obj: RValue,
    val method: MethodRef
) : Instruction(Opcode.GetMethod), TargetRef {
    override fun size(): Int = target.size() + method.size()

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        obj.write(out)
        method.write(out)
    }
}

data class GetMethodStatic (
    val target: Target,
    val method: MethodRef
) : Instruction(Opcode.GetMethodStatic), TargetRef {
    override fun size(): Int = target.size() + method.size()

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        method.write(out)
    }
}

data class GetStatic (
    val target: Target,
    val field: FieldRef
) : Instruction(Opcode.GetStatic), TargetRef {
    override fun size(): Int = target.size() + field.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        field.write(out)
    }
}

data class PutStatic (
    val field: FieldRef,
    val value: RValue
) : Instruction(Opcode.PutStatic) {
    override fun size(): Int = field.size() + value.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        field.write(out)
        value.write(out)
    }
}

/// Get value from array
data class ArrayLoad (
    val target: Target,
    val array: RValue,
    val index: RValue
) : Instruction(Opcode.ArrayLoad), TargetRef {
    override fun size(): Int = target.size() + array.size() + index.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        array.write(out)
        index.write(out)
    }
}

data class ArrayStore (
    val array: RValue,
    val index: RValue,
    val value: RValue
) : Instruction(Opcode.ArrayStore) {
    override fun size(): Int = array.size() + index.size() + value.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        array.write(out)
        index.write(out)
        value.write(out)
    }
}

data class Cast (
    val target: Target,
    val ref: RValue,
    val type: TypeRef
) : Instruction(Opcode.Cast), TargetRef {
    override fun size(): Int = target.size() + ref.size() + type.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        ref.write(out)
        type.write(out)
    }
}

data class InstanceOf (
    val target: Target,
    val ref: RValue,
    val type: TypeRef
) : Instruction(Opcode.InstanceOf), TargetRef {
    override fun size(): Int = target.size() + ref.size() + type.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        ref.write(out)
        type.write(out)
    }
}

data class TypeOf (
    val target: Target,
    val ref: RValue
) : Instruction(Opcode.TypeOf), TargetRef {
    override fun size(): Int = target.size() + ref.size()
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        target.write(out)
        ref.write(out)
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
    val ref: RValue,
    val cases: Map<RValue, Label>, // case : label
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
}

data class Phi (
    val target: Target,
    val incoming: Map<Label, Reference>
) : Instruction(Opcode.Phi), TargetRef {
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
}

object Nop : Instruction(Opcode.Nop) {
    override fun size(): Int = 1

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
    }
}