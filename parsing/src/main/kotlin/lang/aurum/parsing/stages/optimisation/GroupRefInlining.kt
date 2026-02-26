package lang.aurum.parsing.stages.optimisation

import lang.aurum.ir.*
import lang.aurum.parsing.stages.FileContext

/**
 * Optimization pass that inlines calls on MethodGroupRef and MemberGroupRef.
 * 
 * Examples:
 * 1.
 *     ```
 *     CallVirtual(target=a, obj=(#1, #2, #3), method=#2, args=[])
 *     ```
 *
 *     turns into
 *     ```
 *     Call(target=a, method=#2, args=[])
 *     ```
 * 
 * 2.
 *    ```
 *    GetMethod(target=b, obj=#0, method=(#1, #2, #3))
 *    ...
 *    CallVirtual(target=c, obj=b, method=#2, args=[])
 *    ```
 *
 *    turns into
 *    ```
 *    CallVirtual(target=c, obj=#0, method=#2, args=[])
 *    ```
 *
 */
object GroupRefInlining : OptimizationPass {

    /**
     * Tracks assignments from GetMethod instructions
     */
    private data class MethodRefSource(
        val originalObj: RValue,
        val originalMethod: MethodRef
    )

    override fun run(fileCtx: FileContext, instructions: MutableList<Instruction>): Boolean {
        var changed = false
        val methodRefMap = mutableMapOf<String, MethodRefSource>()

        fun getVarName(lvalue: LValue): String? {
            return when (lvalue) {
                is Reference.Named -> lvalue.name
                else -> null
            }
        }

        fun getVarName(rvalue: RValue): String? {
            return when (rvalue) {
                is Reference.Named -> rvalue.name
                else -> null
            }
        }

        fun isMethodInGroup(method: SingleMethodRef, group: MethodRef): Boolean {
            return when (group) {
                is SingleMethodRef -> method.ref == group.ref
                is MethodGroupRef -> group.refs.any { it.ref == method.ref }
            }
        }

        fun isMethodInMemberGroup(method: SingleMethodRef, group: MemberRef): Boolean {
            return when (group) {
                is SingleMethodRef -> method.ref == group.ref
                is MethodGroupRef -> group.refs.any { it.ref == method.ref }
                is MemberGroupRef -> {
                    group.refs.filterIsInstance<SingleMethodRef>().any { it.ref == method.ref }
                }
                else -> false
            }
        }

        for (i in instructions.indices) {
            when (val inst = instructions[i]) {
                is GetMethod -> {
                    val targetName = getVarName(inst.target)
                    if (targetName != null) {
                        methodRefMap[targetName] = MethodRefSource(
                            originalObj = inst.obj,
                            originalMethod = inst.method
                        )
                    }
                }

                is GetMethodStatic -> {
                    val targetName = getVarName(inst.target)
                    targetName?.let { methodRefMap.remove(it) }
                }

                is Move -> {
                    val targetName = getVarName(inst.target)
                    val sourceName = getVarName(inst.ref)
                    if (targetName != null && sourceName != null) {
                        val source = methodRefMap[sourceName]
                        if (source != null) {
                            methodRefMap[targetName] = source
                        } else {
                            methodRefMap.remove(targetName)
                        }
                    } else {
                        targetName?.let { methodRefMap.remove(it) }
                    }
                }

                is GetField, is GetMember, is GetStatic, is Closure, is New, is NewArray,
                is BinaryOp, is Neg, is Cast, is InstanceOf, is TypeOf, is ArrayLoad,
                is Catch, is Phi, is Null -> {
                    val targetName = getVarName(inst.target)
                    targetName?.let { methodRefMap.remove(it) }
                }

                is CallVirtual -> {
                    var localChanged = false
                    val obj = inst.obj
                    val method = inst.method
                    
                    if (method is SingleMethodRef) {
                        when {
//                            obj is SingleMethodRef -> {
//                                instructions[i] = Call(
//                                    inst.target,
//                                    method,
//                                    inst.args
//                                )
//                                changed = true
//                                localChanged = true
//                            }
                            obj is MethodGroupRef -> {
                                if (isMethodInGroup(method, obj)) {
                                    instructions[i] = Call(
                                        target = inst.target,
                                        method = method,
                                        args = inst.args
                                    )
                                    changed = true
                                    localChanged = true
                                }
                            }
                            obj is MemberGroupRef -> {
                                if (isMethodInMemberGroup(method, obj)) {
                                    instructions[i] = Call(
                                        target = inst.target,
                                        method = method,
                                        args = inst.args
                                    )
                                    changed = true
                                    localChanged = true
                                }
                            }
                        }
                    }
                    if (obj is Reference.Named && !localChanged) {
                        val objVarName = obj.name
                        val objMethodSource = methodRefMap[objVarName]
                        
                        if (objMethodSource != null && method is SingleMethodRef) {
                            if (isMethodInGroup(method, objMethodSource.originalMethod)) {
                                instructions[i] = inst.copy(
                                    obj = objMethodSource.originalObj,
                                    method = method
                                )
                                changed = true
                            }
                        }
                    }
                }

                is CallMethod -> {
                    var localChanged = false
                    val obj = inst.obj
                    val method = inst.method

                    if (method is SingleMethodRef) {
                        when {
                            obj is MethodGroupRef -> {
                                if (isMethodInGroup(method, obj)) {
                                    instructions[i] = Call(
                                        target = inst.target,
                                        method = method,
                                        args = inst.args
                                    )
                                    changed = true
                                    localChanged = true
                                }
                            }
                            obj is MemberGroupRef -> {
                                if (isMethodInMemberGroup(method, obj)) {
                                    instructions[i] = Call(
                                        target = inst.target,
                                        method = method,
                                        args = inst.args
                                    )
                                    changed = true
                                    localChanged = true
                                }
                            }
                        }
                    }
                    if (obj is Reference.Named && !localChanged) {
                        val objVarName = obj.name
                        val objMethodSource = methodRefMap[objVarName]
                        
                        if (objMethodSource != null && method is SingleMethodRef) {
                            if (isMethodInGroup(method, objMethodSource.originalMethod)) {
                                instructions[i] = inst.copy(
                                    obj = objMethodSource.originalObj,
                                    method = method
                                )
                                changed = true
                            }
                        }
                    }
                }

                is Call -> {}

                else -> {}
            }
        }

        return changed
    }
}

