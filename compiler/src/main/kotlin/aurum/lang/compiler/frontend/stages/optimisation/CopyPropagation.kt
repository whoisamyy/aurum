package aurum.lang.compiler.frontend.stages.optimisation
//
//import aurum.lang.ir.*
//import aurum.lang.compiler.frontend.stages.FileContext
//
//object CopyPropagation : OptimizationPass {
//    private data class BoundSource(val value: RValue, val owner: RValue)
//
//    override fun run(fileCtx: FileContext, instructions: MutableList<Instruction>): Boolean {
//        var changed = false
//        // Track copy mappings per variable name (non-SSA: variables can be reassigned)
//        val copyMap = mutableMapOf<String, RValue>()
//
//        // Helper to get variable name from LValue
//        fun getVarName(lvalue: LValue): String? {
//            return when (lvalue) {
//                is Reference.Named -> lvalue.name
//                else -> null
//            }
//        }
//
//        // Helper to invalidate a variable's copy mapping (when it's reassigned)
//        fun invalidateVariable(lvalue: LValue) {
//            getVarName(lvalue)?.let { copyMap.remove(it) }
//        }
//
//        // Helper to substitute a reference with its copy if available
//        @Suppress("UNCHECKED_CAST")
//        fun <T : RValue> substitute(ref: T): T {
//            if (ref !is Reference.Named) return ref
//
//            val copy = copyMap[ref.name]
//            return if (copy != null) {
//                copy as T
//            } else {
//                ref
//            }
//        }
//
//        for (i in instructions.indices) {
//            when (val inst = instructions[i]) {
//                is GetStatic -> {
//                    val newRef = substitute(inst.field)
//                    if (newRef != inst.field) {
//                        instructions[i] = inst.copy(field = newRef)
//                        changed = true
//                    }
//                    // Track the result as a copy if it's a simple reference
//                    invalidateVariable(inst.target)
//                    getVarName(inst.target)?.let { copyMap[it] = newRef }
//                }
//
//                is GetMember -> {
//                    val newObj = substitute(inst.obj)
//                    val newMember = substitute(inst.member)
//                    if (newObj != inst.obj || newMember != inst.member) {
//                        instructions[i] = inst.copy(obj = newObj, member = newMember)
//                        changed = true
//                    }
//                    // GetMember produces a bound value, don't track as simple copy
//                    invalidateVariable(inst.target)
//                }
//
//                is GetMethod -> {
//                    val newObj = substitute(inst.obj)
//                    val newMethod = substitute(inst.method)
//
//                    if (newObj != inst.obj || newMethod != inst.method) {
//                        instructions[i] = inst.copy(obj = newObj, method = newMethod)
//                        changed = true
//                    }
//                    // GetMethod produces a bound value, don't track as simple copy
//                    invalidateVariable(inst.target)
//                }
//
//                is GetField -> {
//                    val newObj = substitute(inst.obj)
//                    val newField = substitute(inst.field)
//
//                    if (newObj != inst.obj || newField != inst.field) {
//                        instructions[i] = inst.copy(obj = newObj, field = newField)
//                        changed = true
//                    }
//                    // GetField produces a value from object, don't track as simple copy
//                    invalidateVariable(inst.target)
//                }
//
//                is GetMethodStatic -> {
//                    val newRef = substitute(inst.method)
//                    if (newRef != inst.method) {
//                        instructions[i] = inst.copy(method = newRef)
//                        changed = true
//                    }
//                    // Track the result as a copy if it's a simple reference
//                    invalidateVariable(inst.target)
//                    getVarName(inst.target)?.let { copyMap[it] = newRef }
//                }
//
//                is Null -> {
//                    invalidateVariable(inst.target)
//                    // Null is a constant, not a copy
//                }
//
//                is Move -> {
//                    val newRef = substitute(inst.ref)
//                    val target = inst.target
//
//                    // If source is a simple reference (not a complex expression), track it as a copy
//                    if (newRef is Reference.Named && target is Reference.Named && newRef.name != target.name) {
//                        // Simple copy: target = source
//                        invalidateVariable(target)
//                        copyMap[target.name] = newRef
//                    } else {
//                        // Not a simple copy, just invalidate
//                        invalidateVariable(target)
//                    }
//
//                    if (newRef != inst.ref) {
//                        instructions[i] = inst.copy(ref = newRef)
//                        changed = true
//                    }
//                }
//
//                is Closure -> {
//                    val newFunc = substitute(inst.func)
//                    val newCaptures = inst.captured.map { substitute(it) }
//                    if (newFunc != inst.func || newCaptures != inst.captured) {
//                        instructions[i] = inst.copy(func = newFunc, captured = newCaptures)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is New -> {
//                    val newClassRef = substitute(inst.classRef)
//                    if (newClassRef != inst.classRef) {
//                        instructions[i] = inst.copy(classRef = newClassRef)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is NewArray -> {
//                    val newElementType = substitute(inst.elementType)
//                    val newSizeRef = substitute(inst.sizeRef)
//                    if (newElementType != inst.elementType || newSizeRef != inst.sizeRef) {
//                        instructions[i] = inst.copy(elementType = newElementType, sizeRef = newSizeRef)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is BinaryOp -> {
//                    val newLeft = substitute(inst.left)
//                    val newRight = substitute(inst.right)
//                    if (newLeft != inst.left || newRight != inst.right) {
//                        instructions[i] = inst.copy(left = newLeft, right = newRight)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is Neg -> {
//                    val newRef = substitute(inst.ref)
//                    if (newRef != inst.ref) {
//                        instructions[i] = inst.copy(ref = newRef)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is JumpIf -> {
//                    val newCond = substitute(inst.cond)
//                    if (newCond != inst.cond) {
//                        instructions[i] = inst.copy(cond = newCond)
//                        changed = true
//                    }
//                }
//
//                is Return -> {
//                    val newVal = inst.value?.let { substitute(it) }
//                    if (newVal != inst.value) {
//                        instructions[i] = inst.copy(value = newVal)
//                        changed = true
//                    }
//                }
//
//                is Throw -> {
//                    val newRef = substitute(inst.ref)
//                    if (newRef != inst.ref) {
//                        instructions[i] = inst.copy(ref = newRef)
//                        changed = true
//                    }
//                }
//
//                is Call -> {
//                    val newMethod = substitute(inst.method)
//                    val newArgs = inst.args.map { substitute(it) }
//                    if (newArgs != inst.args || newMethod != inst.method) {
//                        instructions[i] = inst.copy(args = newArgs, method = newMethod)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is CallMethod -> {
//                    val newMethod = substitute(inst.method)
//                    val newArgs = inst.args.map { substitute(it) }
//                    val newObj = substitute(inst.obj)
//
//                    if (newObj != inst.obj || newArgs != inst.args || newMethod != inst.method) {
//                        instructions[i] = inst.copy(obj = newObj, args = newArgs, method = newMethod)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is CallVirtual -> {
//                    val newMethod = substitute(inst.method)
//                    val newArgs = inst.args.map { substitute(it) }
//                    val newObj = substitute(inst.obj)
//
//                    if (newObj != inst.obj || newArgs != inst.args || newMethod != inst.method) {
//                        instructions[i] = inst.copy(obj = newObj, args = newArgs, method = newMethod)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is InvokeConstructor -> {
//                    val newObj = substitute(inst.obj)
//                    val newArgs = inst.args.map { substitute(it) }
//                    if (newObj != inst.obj || newArgs != inst.args) {
//                        instructions[i] = inst.copy(obj = newObj, args = newArgs)
//                        changed = true
//                    }
//                }
//
//                is PutField -> {
//                    val newObj = substitute(inst.obj)
//                    val newVal = substitute(inst.value)
//                    if (newObj != inst.obj || newVal != inst.value) {
//                        instructions[i] = inst.copy(obj = newObj, value = newVal)
//                        changed = true
//                    }
//                }
//
//                is ArrayLoad -> {
//                    val newArr = substitute(inst.array)
//                    val newIdx = substitute(inst.index)
//                    if (newArr != inst.array || newIdx != inst.index) {
//                        instructions[i] = inst.copy(array = newArr, index = newIdx)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is ArrayStore -> {
//                    val newArr = substitute(inst.array)
//                    val newIdx = substitute(inst.index)
//                    val newVal = substitute(inst.value)
//                    if (newArr != inst.array || newIdx != inst.index || newVal != inst.value) {
//                        instructions[i] = inst.copy(array = newArr, index = newIdx, value = newVal)
//                        changed = true
//                    }
//                }
//
//                is Cast -> {
//                    val newRef = substitute(inst.ref)
//                    if (newRef != inst.ref) {
//                        instructions[i] = inst.copy(ref = newRef)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is InstanceOf -> {
//                    val newRef = substitute(inst.ref)
//                    if (newRef != inst.ref) {
//                        instructions[i] = inst.copy(ref = newRef)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is TypeOf -> {
//                    val newRef = substitute(inst.ref)
//                    if (newRef != inst.ref) {
//                        instructions[i] = inst.copy(ref = newRef)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                is Catch -> {
//                    invalidateVariable(inst.target)
//                }
//
//                is Phi -> {
//                    val newIncoming = inst.incoming.mapValues { (_, ref) ->
//                        substitute(ref)
//                    }
//                    if (newIncoming != inst.incoming) {
//                        instructions[i] = inst.copy(incoming = newIncoming)
//                        changed = true
//                    }
//                    invalidateVariable(inst.target)
//                }
//
//                else -> {}
//            }
//        }
//
//        return changed
//    }
//}
