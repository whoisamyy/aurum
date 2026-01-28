package lang.aurum.parsing.stages

import lang.aurum.ir.*
import lang.aurum.model.Method
import lang.aurum.parsing.attribute.get

class ConstantPoolCleaningStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    lateinit var constantPool: ConstantPool
    val allInstructions = mutableListOf<Instruction>()
    val instructionsMap = mutableMapOf<Method, MutableList<Instruction>>()

    override fun execute(fileContext: FileContext) {
        this.constantPool = fileContext.constantPool
    }

    override fun execute(method: Method) {
        val instructions = method.attributes().get<CodeAttribute>()?.code ?: mutableListOf()
        allInstructions += instructions
        instructionsMap += method to instructions
    }

    override fun afterFileContext(fileContext: FileContext) {
        val usedRefs = mutableSetOf<ConstantPoolRef>()
        
        for (instruction in allInstructions) {
            usedRefs.addAll(extractConstantPoolRefs(instruction))
        }

        constantPool.keepAll(usedRefs)

        reindexConstantPool()
    }
    
    /**
     * Reindexes the constant pool so that all remaining entries have contiguous indices starting from 0.
     * Updates all ConstantPoolRef instances in instructions to use the new indices.
     */
    private fun reindexConstantPool() {
        val sortedRefs = constantPool.references.sortedBy { it.ref }
        
        if (sortedRefs.isEmpty()) {
            return
        }

        val indexMapping = sortedRefs.mapIndexed { newIndex, ref ->
            ref.ref to newIndex.toUShort()
        }.toMap()

        val values = sortedRefs.map { constantPool.constantPool[it]!! }

        constantPool.references.clear()
        constantPool.constantPool.clear()

        sortedRefs.forEachIndexed { newIndex, ref ->
            ref.ref = newIndex.toUShort()
        }

        sortedRefs.forEachIndexed { newIndex, ref ->
            constantPool.references.add(ref)
            constantPool.constantPool[ref] = values[newIndex]
        }
    }
    
    /**
     * Updates all ConstantPoolRef instances in an instruction to use new indices.
     */
    private fun updateConstantPoolRefsInInstruction(instruction: Instruction, indexMapping: Map<UShort, UShort>) {
        when (instruction) {
            is Move -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.ref, indexMapping)
            }
            is BinaryOp -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.left, indexMapping)
                updateRValue(instruction.right, indexMapping)
            }
            is Neg -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.ref, indexMapping)
            }
            is Null -> {
                updateLValue(instruction.target, indexMapping)
            }
            is JumpIf -> {
                updateRValue(instruction.cond, indexMapping)
            }
            is Return -> {
                instruction.value?.let { updateRValue(it, indexMapping) }
            }
            is Throw -> {
                updateRValue(instruction.ref, indexMapping)
            }
            is Call -> {
                updateLValue(instruction.target, indexMapping)
                updateMethodRef(instruction.method, indexMapping)
                instruction.args.forEach { updateRValue(it, indexMapping) }
            }
            is CallMethod -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.obj, indexMapping)
                updateMethodRef(instruction.method, indexMapping)
                instruction.args.forEach { updateRValue(it, indexMapping) }
            }
            is CallVirtual -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.obj, indexMapping)
                updateMethodRef(instruction.method, indexMapping)
                instruction.args.forEach { updateRValue(it, indexMapping) }
            }
            is InvokeConstructor -> {
                updateRValue(instruction.obj, indexMapping)
                instruction.args.forEach { updateRValue(it, indexMapping) }
            }
            is Closure -> {
                updateLValue(instruction.target, indexMapping)
                updateMethodRef(instruction.func, indexMapping)
                instruction.captured.forEach { updateRValue(it, indexMapping) }
            }
            is New -> {
                updateLValue(instruction.target, indexMapping)
                updateConstantPoolRef(instruction.classRef, indexMapping)
            }
            is NewArray -> {
                updateLValue(instruction.target, indexMapping)
                updateConstantPoolRef(instruction.elementType, indexMapping)
                updateRValue(instruction.sizeRef, indexMapping)
            }
            is GetField -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.obj, indexMapping)
                updateConstantPoolRef(instruction.field, indexMapping)
            }
            is PutField -> {
                updateRValue(instruction.obj, indexMapping)
                updateConstantPoolRef(instruction.field, indexMapping)
                updateRValue(instruction.value, indexMapping)
            }
            is GetMember -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.obj, indexMapping)
                updateMemberRef(instruction.member, indexMapping)
            }
            is GetMethod -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.obj, indexMapping)
                updateMethodRef(instruction.method, indexMapping)
            }
            is GetMethodStatic -> {
                updateLValue(instruction.target, indexMapping)
                updateMethodRef(instruction.method, indexMapping)
            }
            is GetStatic -> {
                updateLValue(instruction.target, indexMapping)
                updateConstantPoolRef(instruction.field, indexMapping)
            }
            is ArrayLoad -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.array, indexMapping)
                updateRValue(instruction.index, indexMapping)
            }
            is ArrayStore -> {
                updateRValue(instruction.array, indexMapping)
                updateRValue(instruction.index, indexMapping)
                updateRValue(instruction.value, indexMapping)
            }
            is Cast -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.ref, indexMapping)
                updateConstantPoolRef(instruction.type, indexMapping)
            }
            is InstanceOf -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.ref, indexMapping)
                updateConstantPoolRef(instruction.type, indexMapping)
            }
            is TypeOf -> {
                updateLValue(instruction.target, indexMapping)
                updateRValue(instruction.ref, indexMapping)
            }
            is Catch -> {
                updateLValue(instruction.target, indexMapping)
            }
            is Phi -> {
                updateLValue(instruction.target, indexMapping)
            }
            is Switch -> {
                updateRValue(instruction.ref, indexMapping)
                instruction.cases.forEach { (key, _) ->
                    updateRValue(key, indexMapping)
                }
            }
            else -> {
                // Instructions like Jump, TryBegin, TryEnd, LabelInst, Nop
                // don't contain constant pool references
            }
        }
    }

    private fun updateConstantPoolRef(ref: ConstantPoolRef, indexMapping: Map<UShort, UShort>) {
        val newIndex = indexMapping[ref.ref]
        if (newIndex != null) {
            ref.ref = newIndex
        }
    }

    private fun updateLValue(lvalue: LValue, indexMapping: Map<UShort, UShort>) {
        if (lvalue is ConstantPoolRef) {
            updateConstantPoolRef(lvalue, indexMapping)
        }
    }

    private fun updateRValue(rvalue: RValue, indexMapping: Map<UShort, UShort>) {
        if (rvalue is ConstantPoolRef) {
            updateConstantPoolRef(rvalue, indexMapping)
        }
    }

    private fun updateMethodRef(methodRef: MethodRef, indexMapping: Map<UShort, UShort>) {
        when (methodRef) {
            is SingleMethodRef -> updateConstantPoolRef(methodRef, indexMapping)
            is MethodGroupRef -> {
                methodRef.refs.forEach { updateConstantPoolRef(it, indexMapping) }
            }
        }
    }

    private fun updateMemberRef(memberRef: MemberRef, indexMapping: Map<UShort, UShort>) {
        when (memberRef) {
            is FieldRef -> updateConstantPoolRef(memberRef, indexMapping)
            is SingleMethodRef -> updateConstantPoolRef(memberRef, indexMapping)
            is FieldGroupRef -> {
                memberRef.refs.forEach { updateConstantPoolRef(it, indexMapping) }
            }
            is MethodGroupRef -> {
                memberRef.refs.forEach { updateConstantPoolRef(it, indexMapping) }
            }
            is MemberGroupRef -> {
                memberRef.refs.forEach { updateMemberRef(it, indexMapping) }
            }
        }
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