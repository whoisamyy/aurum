package lang.aurum.parsing.stages.coderesolution

import lang.aurum.ir.*
import lang.aurum.ir.Target

class InstructionGenerator(
    val compiler: IRCompiler
) {
    fun addInstruction(inst: Instruction) {
        when (inst) {
            is Null -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is Move -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is BinaryOp -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is Neg -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is Call -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is CallMethod -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is CallVirtual -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is InvokeConstructor -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is Closure -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is New -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is NewArray -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is GetField -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is GetStatic -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is GetMember -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is GetMethod -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is GetMethodStatic -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is ArrayLoad -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is Cast -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is InstanceOf -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is TypeOf -> {
                compiler.dataTracker.track(inst.target, inst)
            }
            is Phi -> {
                compiler.dataTracker.track(inst.target, inst)
            }
        }
        compiler.instructions.add(inst)
    }

    fun nop() = addInstruction(Nop)
    fun null_(target: Target) = addInstruction(Null(target))
    fun move(target: Target, value: RValue) = addInstruction(Move(target, value))
    fun binaryOp(target: Target, left: RValue, right: RValue, operator: BinaryOperator) = addInstruction(BinaryOp(target, left, right, operator))
    fun add(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.ADD)
    fun sub(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.SUB)
    fun mul(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.MUL)
    fun div(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.DIVIDE)
    fun mod(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.MOD)
    fun and(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.B_AND)
    fun or(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.B_OR)
    fun xor(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.XOR)
    fun shl(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.SHL)
    fun shr(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.SHR)
    fun ushr(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.USHR)
    fun cmpEq(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.EQ)
    fun cmpNe(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.NEQ)
    fun cmpLt(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.LT)
    fun cmpLe(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.LE)
    fun cmpGt(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.GT)
    fun cmpGe(target: Target, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.GE)
    fun neg(target: Target, ref: RValue) = addInstruction(Neg(target, ref))
    fun jump(label: Label) = addInstruction(Jump(label))
    fun jumpIf(cond: RValue, label: Label) = addInstruction(JumpIf(cond, label))
    fun return_(value: RValue? = null) = addInstruction(Return(value))
    fun throw_(ref: RValue) = addInstruction(Throw(ref))
    fun tryBegin(labelCatch: Label, labelFinally: Label? = null) = addInstruction(TryBegin(labelCatch, labelFinally))
    fun tryEnd() = addInstruction(TryEnd())
    fun catch(exceptionVar: Target, labelEnd: Label) = addInstruction(Catch(exceptionVar, labelEnd))
    fun call(target: Target, method: MethodRef, args: List<RValue> = listOf()) =
        addInstruction(Call(target, method, args))
    fun callMethod(target: Target, obj: RValue, method: MethodRef, args: List<RValue> = listOf()) =
        addInstruction(CallMethod(target, obj, method, args))
    fun callVirtual(target: Target, obj: RValue, method: MethodRef, args: List<RValue> = listOf()) =
        addInstruction(CallVirtual(target, obj, method, args))
    fun invokeConstructor(target: Target, obj: RValue, args: List<RValue> = listOf()) =
        addInstruction(InvokeConstructor(target, obj, args))
    fun closure(target: Target, func: MethodRef, captured: List<RValue> = listOf()) =
        addInstruction(Closure(target, func, captured))
    fun new(target: Target, classRef: TypeRef) = addInstruction(New(target, classRef))
    fun newArray(target: Target, elementType: TypeRef, sizeRef: RValue) =
        addInstruction(NewArray(target, elementType, sizeRef))
    fun getField(target: Target, obj: RValue, field: FieldRef) =
        addInstruction(GetField(target, obj, field))
    fun putField(obj: RValue, field: FieldRef, value: RValue) =
        addInstruction(PutField(obj, field, value))
    fun getStatic(target: Target, field: FieldRef) =
        addInstruction(GetStatic(target, field))
    fun putStatic(field: FieldRef, value: RValue) =
        addInstruction(PutStatic(field, value))
    fun getMember(target: Target, obj: RValue, member: MemberRef) =
        addInstruction(GetMember(target, obj, member))
    fun getMethod(target: Target, obj: RValue, method: MethodRef) =
        addInstruction(GetMethod(target, obj, method))
    fun getMethodStatic(target: Target, method: MethodRef) =
        addInstruction(GetMethodStatic(target, method))
    fun arrayLoad(target: Target, array: RValue, index: RValue) =
        addInstruction(ArrayLoad(target, array, index))
    fun arrayStore(array: RValue, index: RValue, value: RValue) =
        addInstruction(ArrayStore(array, index, value))
    fun cast(target: Target, ref: RValue, type: TypeRef) = addInstruction(Cast(target, ref, type))
    fun instanceOf(target: Target, ref: RValue, type: TypeRef) = addInstruction(InstanceOf(target, ref, type))
    fun typeOf(target: Target, ref: RValue) = addInstruction(TypeOf(target, ref))
    fun label(label: Label) = addInstruction(LabelInst(label))
    fun switch(ref: RValue, cases: Map<RValue, Label>, defaultLabel: Label) =
        addInstruction(Switch(ref, cases, defaultLabel))
    fun phi(target: Target, incoming: Map<Label, Reference>) =
        addInstruction(Phi(target, incoming))
}