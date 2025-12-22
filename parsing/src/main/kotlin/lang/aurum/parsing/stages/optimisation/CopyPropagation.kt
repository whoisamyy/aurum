package lang.aurum.parsing.stages.optimisation

import lang.aurum.ir.*
import lang.aurum.parsing.stages.FileContext

object CopyPropagation : OptimizationPass {
    private data class BoundSource(val value: RValue, val owner: RValue)

    override fun run(fileCtx: FileContext, instructions: MutableList<Instruction>): Boolean {
        var changed = false
        val copyMap = mutableMapOf<String, Any>()

        @Suppress("UNCHECKED_CAST")
        fun <T : RValue> substitute(ref: T): T {
            if (ref !is Reference) return ref

            val mapped = copyMap[ref.name] ?: return ref

            return when (mapped) {
                is RValue -> mapped as T
                is BoundSource -> mapped.value as T 
                else -> ref
            }
        }

        for (i in instructions.indices) {
            when (val inst = instructions[i]) {
                is GetStatic -> {
                    val newRef = substitute(inst.field)
                    copyMap[inst.target.name] = newRef
                    instructions[i] = inst.copy(field = newRef)
                }

                is GetMember -> {
                    val newObj = substitute(inst.obj)
                    val newMember = substitute(inst.member)
                    if (newObj != inst.obj || newMember != inst.member) {
                        instructions[i] = inst.copy(obj = newObj, member = newMember)
                        changed = true
                    }
                    
                    copyMap[inst.target.name] = BoundSource(instructions[i].let {  it as? RValue ?: Reference(inst.target) }, newObj)
                    copyMap[inst.target.name] = BoundSource(Reference(inst.target), newObj)
                }

                is GetMethod -> {
                    val newObj = substitute(inst.obj)
                    val newMethod = substitute(inst.method)
                    
                    copyMap[inst.target.name] = BoundSource(newMethod, newObj)

                    if (newObj != inst.obj || newMethod != inst.method) {
                        instructions[i] = inst.copy(obj = newObj, method = newMethod)
                        changed = true
                    }
                }

                is GetField -> {
                    val newObj = substitute(inst.obj)
                    val newField = substitute(inst.field)
                    
                    copyMap[inst.target.name] = BoundSource(Reference(inst.target), newObj)

                    if (newObj != inst.obj || newField != inst.field) {
                        instructions[i] = inst.copy(obj = newObj, field = newField)
                        changed = true
                    }
                }

                is GetMethodStatic -> {
                    val newRef = substitute(inst.method)
                    copyMap[inst.target.name] = newRef
                    instructions[i] = inst.copy(method = newRef)
                }

                is Null -> {
                    copyMap[inst.target.name] = NullRef
                }

                is Move -> {
                    val ref = inst.ref
                    val newRef = substitute(ref)
                    
                    val rawRef = if (ref is Reference) copyMap[ref.name] else null
                    if (rawRef is BoundSource) {
                        copyMap[inst.target.name] = rawRef 
                    } else {
                        copyMap[inst.target.name] = newRef
                    }
                    instructions[i] = inst.copy(ref = newRef)
                }

                is Closure -> {
                    val newFunc = substitute(inst.func)
                    val newCaptures = inst.captured.map { substitute(it) }
                    if (newFunc != inst.func || newCaptures != inst.captured) {
                        instructions[i] = inst.copy(func = newFunc, captured = newCaptures)
                    }
                    copyMap[inst.target.name] = instructions[i]
                }

                is New -> {
                    val newClassRef = substitute(inst.classRef)
                    if (newClassRef != inst.classRef) {
                        instructions[i] = inst.copy(classRef = newClassRef)
                        changed = true
                    }
                    copyMap[inst.target.name] = instructions[i]
                }

                is NewArray -> {
                    val newElementType = substitute(inst.elementType)
                    val newSizeRef = substitute(inst.sizeRef)
                    if (newElementType != inst.elementType || newSizeRef != inst.sizeRef) {
                        instructions[i] = inst.copy(elementType = newElementType, sizeRef = newSizeRef)
                        changed = true
                    }
                    copyMap[inst.target.name] = instructions[i]
                }

                is BinaryOp -> {
                    val newLeft = substitute(inst.left)
                    val newRight = substitute(inst.right)
                    if (newLeft != inst.left || newRight != inst.right) {
                        instructions[i] = inst.copy(left = newLeft, right = newRight)
                        changed = true
                    }
                    copyMap[inst.target.name] = instructions[i]
                }

                is Neg -> {
                    val newRef = substitute(inst.ref)
                    if (newRef != inst.ref) {
                        instructions[i] = inst.copy(ref = newRef)
                        changed = true
                    }
                    copyMap[inst.target.name] = instructions[i]
                }

                is JumpIf -> {
                    val newCond = substitute(inst.cond)
                    if (newCond != inst.cond) {
                        instructions[i] = inst.copy(cond = newCond)
                        changed = true
                    }
                }

                is Return -> {
                    val newVal = inst.value?.let { substitute(it) }
                    if (newVal != inst.value) {
                        instructions[i] = inst.copy(value = newVal)
                        changed = true
                    }
                }

                is Throw -> {
                    val newRef = substitute(inst.ref)
                    if (newRef != inst.ref) {
                        instructions[i] = inst.copy(ref = newRef)
                        changed = true
                    }
                }

                is Call -> {
                    val newMethod = substitute(inst.method)
                    val newArgs = inst.args.map { substitute(it) }
                    if (newArgs != inst.args || newMethod != inst.method) {
                        instructions[i] = inst.copy(args = newArgs, method = newMethod)
                        changed = true
                    }
                }
                is CallMethod -> {
                    val newMethod = substitute(inst.method)
                    val newArgs = inst.args.map { substitute(it) }

                    
                    val obj = inst.obj
                    val rawObjEntry = if (obj is Reference) copyMap[obj.name] else null

                    val optimizationOwner = if (rawObjEntry is BoundSource) rawObjEntry.owner else null

                    val newObj = substitute(obj)

                    if (optimizationOwner != null && newObj is MethodRef) {
                        instructions[i] = CallMethod(inst.target, optimizationOwner, newMethod, newArgs)
                        changed = true
                    } else if (newObj == newMethod) {
                        instructions[i] = Call(inst.target, newMethod, newArgs)
                        changed = true
                    } else if (newObj is MethodGroupRef) {
                        instructions[i] = Call(inst.target, newMethod, newArgs)
                        changed = true
                    } else if (newObj != obj || newArgs != inst.args || newMethod != inst.method) {
                        instructions[i] = inst.copy(obj = newObj, args = newArgs, method = newMethod)
                        changed = true
                    }
                }

                is CallVirtual -> {
                    val newMethod = substitute(inst.method)
                    val newArgs = inst.args.map { substitute(it) }

                    val obj = inst.obj
                    val rawObjEntry = if (obj is Reference) copyMap[obj.name] else null

                    val optimizationOwner = if (rawObjEntry is BoundSource) rawObjEntry.owner else null

                    val newObj = substitute(obj)

                    if (optimizationOwner != null && newObj is MethodRef) {
                        instructions[i] = CallVirtual(inst.target, optimizationOwner, newMethod, newArgs)
                        changed = true
                    } else if (newObj == newMethod) {
                        instructions[i] = Call(inst.target, newMethod, newArgs)
                        changed = true
                    } else if (newObj is MethodGroupRef) {
                        instructions[i] = Call(inst.target, newMethod, newArgs)
                        changed = true
                    } else if (newObj != obj || newArgs != inst.args || newMethod != inst.method) {
                        instructions[i] = inst.copy(obj = newObj, args = newArgs, method = newMethod)
                        changed = true
                    }
                }

                is InvokeConstructor -> {
                    val newObj = substitute(inst.obj)
                    val newArgs = inst.args.map { substitute(it) }
                    if (newObj != inst.obj || newArgs != inst.args) {
                        instructions[i] = inst.copy(obj = newObj, args = newArgs)
                        changed = true
                    }
                    copyMap[inst.target.name] = instructions[i]
                }


                is PutStatic -> {
                    val newVal = substitute(inst.value)
                    if (newVal != inst.value) {
                        instructions[i] = inst.copy(value = newVal)
                        changed = true
                    }
                }

                is PutField -> {
                    val newObj = substitute(inst.obj)
                    val newVal = substitute(inst.value)
                    if (newObj != inst.obj || newVal != inst.value) {
                        instructions[i] = inst.copy(obj = newObj, value = newVal)
                        changed = true
                    }
                }

                is ArrayLoad -> {
                    val newArr = substitute(inst.array)
                    val newIdx = substitute(inst.index)
                    if (newArr != inst.array || newIdx != inst.index) {
                        instructions[i] = inst.copy(array = newArr, index = newIdx)
                        changed = true
                    }
                    copyMap[inst.target.name] = instructions[i]
                }

                is ArrayStore -> {
                    val newArr = substitute(inst.array)
                    val newIdx = substitute(inst.index)
                    val newVal = substitute(inst.value)
                    if (newArr != inst.array || newIdx != inst.index || newVal != inst.value) {
                        instructions[i] = inst.copy(array = newArr, index = newIdx, value = newVal)
                        changed = true
                    }
                }

                is Cast -> {
                    val newRef = substitute(inst.ref)
                    if (newRef != inst.ref) {
                        instructions[i] = inst.copy(ref = newRef)
                        changed = true
                    }
                    copyMap[inst.target.name] = instructions[i]
                }

                is InstanceOf -> {
                    val newRef = substitute(inst.ref)
                    if (newRef != inst.ref) {
                        instructions[i] = inst.copy(ref = newRef)
                        changed = true
                    }
                    copyMap[inst.target.name] = instructions[i]
                }

                is TypeOf -> {
                    val newRef = substitute(inst.ref)
                    if (newRef != inst.ref) {
                        instructions[i] = inst.copy(ref = newRef)
                        changed = true
                    }
                    copyMap[inst.target.name] = instructions[i]
                }

                is Phi -> {
                    val newIncoming = inst.incoming.mapValues { (_, ref) ->
                        substitute(ref)
                    }
                    if (newIncoming != inst.incoming) {
                        instructions[i] = inst.copy(incoming = newIncoming)
                        changed = true
                    }
                    copyMap[inst.target.name] = instructions[i]
                }

                else -> {}
            }
        }

        return changed
    }
}