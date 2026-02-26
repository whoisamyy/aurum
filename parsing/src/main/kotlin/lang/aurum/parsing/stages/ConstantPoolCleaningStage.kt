package lang.aurum.parsing.stages

import lang.aurum.ir.*
import lang.aurum.model.Method
import lang.aurum.parsing.attribute.get

class ConstantPoolCleaningStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    lateinit var constantPool: ConstantPool
    val allInstructions = mutableListOf<Instruction>()

    override fun execute(fileContext: FileContext) {
        this.constantPool = fileContext.constantPool
    }

    override fun execute(method: Method) {
        val instructions = method.attributes().get<CodeAttribute>()?.code ?: mutableListOf()
        allInstructions += instructions
    }

    override fun afterFileContext(fileContext: FileContext) {
        val usedRefs = mutableSetOf<ConstantPoolRef>()

        for (instruction in allInstructions) {
            usedRefs.addAll(extractConstantPoolRefs(instruction))
        }

        // Remove all unused entries from the constant pool and reindex the remaining
        // ones so that they form a dense range [0, size) while keeping the relative
        // order of indices. All ConstantPoolRef instances in the IR are updated in
        // place by the pool, so instructions continue to point to valid entries.
        constantPool.compactKeeping(usedRefs)
    }

    /**
     * Recursively extracts all ConstantPoolRef instances from an instruction.
     */
    private fun extractConstantPoolRefs(instruction: Instruction): Set<ConstantPoolRef> {
        val refs = mutableSetOf<ConstantPoolRef>()

        when (instruction) {
            is Move -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.ref))
            }
            is BinaryOp -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.left))
                refs.addAll(extractFromRValue(instruction.right))
            }
            is Neg -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.ref))
            }
            is Null -> {
                refs.addAll(extractFromLValue(instruction.target))
            }
            is JumpIf -> {
                refs.addAll(extractFromRValue(instruction.cond))
            }
            is Return -> {
                instruction.value?.let { refs.addAll(extractFromRValue(it)) }
            }
            is Throw -> {
                refs.addAll(extractFromRValue(instruction.ref))
            }
            is Call -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromMethodRef(instruction.method))
                instruction.args.forEach { refs.addAll(extractFromRValue(it)) }
            }
            is CallMethod -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.obj))
                refs.addAll(extractFromMethodRef(instruction.method))
                instruction.args.forEach { refs.addAll(extractFromRValue(it)) }
            }
            is CallVirtual -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.obj))
                refs.addAll(extractFromMethodRef(instruction.method))
                instruction.args.forEach { refs.addAll(extractFromRValue(it)) }
            }
            is InvokeConstructor -> {
                refs.addAll(extractFromRValue(instruction.obj))
                instruction.args.forEach { refs.addAll(extractFromRValue(it)) }
            }
            is Closure -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromMethodRef(instruction.func))
                instruction.captured.forEach { refs.addAll(extractFromRValue(it)) }
            }
            is New -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.add(instruction.classRef)
            }
            is NewArray -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.add(instruction.elementType)
                refs.addAll(extractFromRValue(instruction.sizeRef))
            }
            is GetField -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.obj))
                refs.add(instruction.field)
            }
            is PutField -> {
                refs.addAll(extractFromRValue(instruction.obj))
                refs.add(instruction.field)
                refs.addAll(extractFromRValue(instruction.value))
            }
            is GetMember -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.obj))
                refs.addAll(extractFromMemberRef(instruction.member))
            }
            is GetMethod -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.obj))
                refs.addAll(extractFromMethodRef(instruction.method))
            }
            is GetMethodStatic -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromMethodRef(instruction.method))
            }
            is GetStatic -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.add(instruction.field)
            }
            is ArrayLoad -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.array))
                refs.addAll(extractFromRValue(instruction.index))
            }
            is ArrayStore -> {
                refs.addAll(extractFromRValue(instruction.array))
                refs.addAll(extractFromRValue(instruction.index))
                refs.addAll(extractFromRValue(instruction.value))
            }
            is Cast -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.ref))
                refs.add(instruction.type)
            }
            is InstanceOf -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.ref))
                refs.add(instruction.type)
            }
            is TypeOf -> {
                refs.addAll(extractFromLValue(instruction.target))
                refs.addAll(extractFromRValue(instruction.ref))
            }
            is Catch -> {
                refs.addAll(extractFromLValue(instruction.target))
            }
            is Phi -> {
                refs.addAll(extractFromLValue(instruction.target))
            }
            is Switch -> {
                refs.addAll(extractFromRValue(instruction.ref))
                instruction.cases.forEach { (key, _) ->
                    refs.addAll(extractFromRValue(key))
                }
            }
            else -> {
                // Instructions like Jump, TryBegin, TryEnd, LabelInst, Nop
                // don't contain constant pool references
            }
        }

        return refs
    }
    private fun extractFromLValue(lvalue: LValue): Set<ConstantPoolRef> {
        val refs = mutableSetOf<ConstantPoolRef>()

        when (lvalue) {
            is ConstantPoolRef -> refs.add(lvalue)
            is Reference.Named, is Reference.Empty -> {
                // These don't contain constant pool references
            }
            else -> {
                // Should not happen
            }
        }

        return refs
    }

    private fun extractFromRValue(rvalue: RValue): Set<ConstantPoolRef> {
        val refs = mutableSetOf<ConstantPoolRef>()

        when (rvalue) {
            is ConstantPoolRef -> refs.add(rvalue)
            is Reference.Named, is Reference.This, is Reference.Super, is NullRef -> {
                // These don't contain constant pool references
            }
            else -> {
                // Should not happen
            }
        }

        return refs
    }

    private fun extractFromMethodRef(methodRef: MethodRef): Set<ConstantPoolRef> {
        val refs = mutableSetOf<ConstantPoolRef>()

        when (methodRef) {
            is SingleMethodRef -> refs.add(methodRef)
            is MethodGroupRef -> {
                methodRef.refs.forEach { refs.add(it) }
            }
        }

        return refs
    }

    private fun extractFromMemberRef(memberRef: MemberRef): Set<ConstantPoolRef> {
        val refs = mutableSetOf<ConstantPoolRef>()

        when (memberRef) {
            is FieldRef -> refs.add(memberRef)
            is SingleMethodRef -> refs.add(memberRef)
            is FieldGroupRef -> {
                memberRef.refs.forEach { refs.add(it) }
            }
            is MethodGroupRef -> {
                memberRef.refs.forEach { refs.add(it) }
            }
            is MemberGroupRef -> {
                memberRef.refs.forEach { refs.addAll(extractFromMemberRef(it)) }
            }
        }

        return refs
    }
}