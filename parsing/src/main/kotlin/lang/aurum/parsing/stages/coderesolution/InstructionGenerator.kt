package lang.aurum.parsing.stages.coderesolution

import lang.aurum.ir.*

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
    fun null_(target: LValue) = addInstruction(Null(target))
    fun move(target: LValue, value: RValue) = addInstruction(Move(target, value))
    fun binaryOp(target: LValue, left: RValue, right: RValue, operator: BinaryOperator) = addInstruction(BinaryOp(target, left, right, operator))
    fun add(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.ADD)
    fun sub(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.SUB)
    fun mul(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.MUL)
    fun div(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.DIVIDE)
    fun mod(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.MOD)
    fun and(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.B_AND)
    fun or(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.B_OR)
    fun xor(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.XOR)
    fun shl(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.SHL)
    fun shr(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.SHR)
    fun ushr(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.USHR)
    fun cmpEq(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.EQ)
    fun cmpNe(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.NEQ)
    fun cmpLt(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.LT)
    fun cmpLe(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.LE)
    fun cmpGt(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.GT)
    fun cmpGe(target: LValue, left: RValue, right: RValue) = binaryOp(target, left, right, BinaryOperator.GE)
    fun neg(target: LValue, ref: RValue) = addInstruction(Neg(target, ref))
    fun jump(label: Label) = addInstruction(Jump(label))
    fun jumpIf(cond: RValue, label: Label) = addInstruction(JumpIf(cond, label))
    fun return_(value: RValue? = null) = addInstruction(Return(value))
    fun throw_(ref: RValue) = addInstruction(Throw(ref))
    fun tryBegin(labelCatch: Label, labelFinally: Label? = null) = addInstruction(TryBegin(labelCatch, labelFinally))
    fun tryEnd() = addInstruction(TryEnd())
    fun catch(exceptionVar: LValue, labelEnd: Label) = addInstruction(Catch(exceptionVar, labelEnd))
    fun call(target: LValue, method: MethodRef, args: List<RValue> = listOf()) =
        addInstruction(Call(target, method, args))
    fun callMethod(target: LValue, obj: RValue, method: MethodRef, args: List<RValue> = listOf()) =
        addInstruction(CallMethod(target, obj, method, args))
    fun callVirtual(target: LValue, obj: RValue, method: MethodRef, args: List<RValue> = listOf()) =
        addInstruction(CallVirtual(target, obj, method, args))
    fun invokeConstructor(obj: RValue, args: List<RValue> = listOf()) =
        addInstruction(InvokeConstructor(obj, args))
    fun closure(target: LValue, func: MethodRef, captured: List<RValue> = listOf()) =
        addInstruction(Closure(target, func, captured))
    fun new(target: LValue, classRef: TypeRef) = addInstruction(New(target, classRef))
    fun newArray(target: LValue, elementType: TypeRef, sizeRef: RValue) =
        addInstruction(NewArray(target, elementType, sizeRef))
    fun getField(target: LValue, obj: RValue, field: FieldRef) =
        addInstruction(GetField(target, obj, field))
    fun putField(obj: RValue, field: FieldRef, value: RValue) =
        addInstruction(PutField(obj, field, value))
    fun getStatic(target: LValue, field: FieldRef) =
        addInstruction(GetStatic(target, field))
    fun getMember(target: LValue, obj: RValue, member: MemberRef) =
        addInstruction(GetMember(target, obj, member))
    fun getMethod(target: LValue, obj: RValue, method: MethodRef) =
        addInstruction(GetMethod(target, obj, method))
    fun getMethodStatic(target: LValue, method: MethodRef) =
        addInstruction(GetMethodStatic(target, method))
    fun arrayLoad(target: LValue, array: RValue, index: RValue) =
        addInstruction(ArrayLoad(target, array, index))
    fun arrayStore(array: RValue, index: RValue, value: RValue) =
        addInstruction(ArrayStore(array, index, value))
    fun cast(target: LValue, ref: RValue, type: TypeRef) = addInstruction(Cast(target, ref, type))
    fun instanceOf(target: LValue, ref: RValue, type: TypeRef) = addInstruction(InstanceOf(target, ref, type))
    fun typeOf(target: LValue, ref: RValue) = addInstruction(TypeOf(target, ref))
    fun label(label: Label) = addInstruction(LabelInst(label))
    fun switch(ref: RValue, cases: Map<RValue, Label>, defaultLabel: Label) =
        addInstruction(Switch(ref, cases, defaultLabel))
    fun phi(target: LValue, incoming: Map<Label, Reference.Named>) =
        addInstruction(Phi(target, incoming))
}