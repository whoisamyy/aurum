package lang.aurum.parsing.stages.optimisation

import lang.aurum.ir.*
import lang.aurum.parsing.stages.FileContext

object CopyPropagation : OptimizationPass {
    override fun run(fileCtx: FileContext, instructions: MutableList<Instruction>): Boolean {
        var changed = false
        val copyMap = mutableMapOf<String, Ref>() // Target.name -> Ref

        @Suppress("UNCHECKED_CAST")
        fun <T : Ref> substitute(ref: T): T = when (ref) {
            is Reference -> copyMap[ref.name] ?: ref
            else -> ref
        } as T

        for (i in instructions.indices) {
            when (val inst = instructions[i]) {
                is GetStatic -> {
                    val newRef = substitute(inst.field)
                    copyMap[inst.target.name] = newRef
                    instructions[i] = inst.copy(field = newRef)
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
                    // a = Move(b)
                    val newRef = substitute(inst.ref)
                    copyMap[inst.target.name] = newRef
                    instructions[i] = inst.copy(ref = newRef)
                }

                is Closure -> {
                    val newFunc = substitute(inst.func)
                    val newCaptures = inst.captured.map { substitute(it) }
                    if (newFunc != inst.func || newCaptures != inst.captured) {
                        instructions[i] = inst.copy(func = newFunc, captured = newCaptures)
                    }
                }

                is New -> {
                    val newClassRef = substitute(inst.classRef)
                    if (newClassRef != inst.classRef) {
                        instructions[i] = inst.copy(classRef = newClassRef)
                        changed = true
                    }
                }

                is NewArray -> {
                    val newElementType = substitute(inst.elementType)
                    val newSizeRef = substitute(inst.sizeRef)
                    if (newElementType != inst.elementType || newSizeRef != inst.sizeRef) {
                        instructions[i] = inst.copy(elementType = newElementType, sizeRef = newSizeRef)
                        changed = true
                    }
                }

                is BinaryOp -> {
                    val newLeft = substitute(inst.left)
                    val newRight = substitute(inst.right)
                    if (newLeft != inst.left || newRight != inst.right) {
                        instructions[i] = inst.copy(left = newLeft, right = newRight)
                        changed = true
                    }
                }

                is Neg -> {
                    val newRef = substitute(inst.ref)
                    if (newRef != inst.ref) {
                        instructions[i] = inst.copy(ref = newRef)
                        changed = true
                    }
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
                    val newObj = substitute(inst.obj)
                    val newArgs = inst.args.map { substitute(it) }
                    if (newObj != inst.obj || newArgs != inst.args || newMethod != inst.method) {
                        instructions[i] = inst.copy(obj = newObj, args = newArgs, method = newMethod)
                        changed = true
                    }
                }

                is CallVirtual -> {
                    val newMethod = substitute(inst.method)
                    val newObj = substitute(inst.obj)
                    val newArgs = inst.args.map { substitute(it) }
                    if (newObj != inst.obj || newArgs != inst.args || newMethod != inst.method) {
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
                }

                is GetMethod -> {
                    val newObj = substitute(inst.obj)
                    if (newObj != inst.obj) {
                        instructions[i] = inst.copy(obj = newObj)
                        changed = true
                    }
                }

                is GetField -> {
                    val newObj = substitute(inst.obj)
                    if (newObj != inst.obj) {
                        instructions[i] = inst.copy(obj = newObj)
                        changed = true
                    }
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
                }

                is InstanceOf -> {
                    val newRef = substitute(inst.ref)
                    if (newRef != inst.ref) {
                        instructions[i] = inst.copy(ref = newRef)
                        changed = true
                    }
                }

                is TypeOf -> {
                    val newRef = substitute(inst.ref)
                    if (newRef != inst.ref) {
                        instructions[i] = inst.copy(ref = newRef)
                        changed = true
                    }
                }

                is Phi -> {
                    val newIncoming = inst.incoming.mapValues { (_, ref) ->
                        substitute(ref)
                    }
                    if (newIncoming != inst.incoming) {
                        instructions[i] = inst.copy(incoming = newIncoming)
                        changed = true
                    }
                }

                // инструкции без ссылок (Label, Jump, Nop, TryEnd, etc.) пропускаем
                else -> {}
            }
        }

        return changed
    }
}