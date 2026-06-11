package aurum.lang.compiler.frontend.stages.optimization

import aurum.lang.ir.*

/**
 * Resolves method-group and member-group dispatch into direct calls when the
 * target method is statically known.
 */
object GroupRefInlining : Optimizer {
    override val minOptLevel: Int = 0

    private data class BoundMethod(
        val receiver: RValue,
        val method: MethodRef,
    )

    override fun optimize(context: OptimizationContext): Boolean {
        val instructions = context.instructions
        var changed = false
        val bindings = mutableMapOf<String, BoundMethod>()

        fun containsMethod(method: SingleMethodRef, group: MethodRef): Boolean = when (group) {
            is SingleMethodRef -> method.ref == group.ref
            is MethodGroupRef -> group.refs.any { it.ref == method.ref }
        }

        fun containsMethod(method: SingleMethodRef, group: MemberRef): Boolean = when (group) {
            is SingleMethodRef -> method.ref == group.ref
            is MethodGroupRef -> group.refs.any { it.ref == method.ref }
            is MemberGroupRef -> group.refs.filterIsInstance<SingleMethodRef>().any { it.ref == method.ref }
            else -> false
        }

        fun inlineGroupCall(
            index: Int,
            target: LValue,
            obj: RValue,
            method: MethodRef,
            args: List<RValue>,
        ): Boolean {
            if (method !is SingleMethodRef) return false
            val replacement = when (obj) {
                is MethodGroupRef -> if (containsMethod(method, obj)) {
                    Call(target, method, args)
                } else null

                is MemberGroupRef -> if (containsMethod(method, obj)) {
                    Call(target, method, args)
                } else null

                else -> null
            } ?: return false
            instructions[index] = replacement
            return true
        }

        fun inlineThroughBinding(
            index: Int,
            inst: CallVirtual,
            binding: BoundMethod,
            method: SingleMethodRef,
        ): Boolean {
            if (!containsMethod(method, binding.method)) return false
            instructions[index] = inst.copy(obj = binding.receiver, method = method)
            return true
        }

        fun inlineThroughBinding(
            index: Int,
            inst: CallMethod,
            binding: BoundMethod,
            method: SingleMethodRef,
        ): Boolean {
            if (!containsMethod(method, binding.method)) return false
            instructions[index] = inst.copy(obj = binding.receiver, method = method)
            return true
        }

        for (index in instructions.indices) {
            when (val inst = instructions[index]) {
                is GetMethod -> {
                    namedVariable(inst.target)?.let { name ->
                        bindings[name] = BoundMethod(inst.obj, inst.method)
                    }
                }

                is GetMember -> {
                    val refs = (inst.member as MemberGroupRef).refs
                    if (refs.any { it !is SingleMethodRef })
                        continue
                    namedVariable(inst.target)?.let { name ->
                        bindings[name] = BoundMethod(
                            inst.obj,
                            MethodGroupRef(refs.map { it as SingleMethodRef })
                        )
                    }
                }

                is Move -> {
                    val target = namedVariable(inst.target)
                    val source = namedVariable(inst.ref)
                    if (target != null) {
                        val binding = source?.let(bindings::get)
                        if (binding != null) bindings[target] = binding
                        else bindings.remove(target)
                    }
                }


                is CallVirtual -> {
                    if (inlineGroupCall(index, inst.target, inst.obj, inst.method, inst.args)) {
                        changed = true
                        continue
                    }
                    val method = inst.method
                    val objName = namedVariable(inst.obj)
                    if (method is SingleMethodRef && objName != null) {
                        val binding = bindings[objName]
                        if (binding != null && inlineThroughBinding(index, inst, binding, method)) {
                            changed = true
                        }
                    }
                }

                is CallMethod -> {
                    if (inlineGroupCall(index, inst.target, inst.obj, inst.method, inst.args)) {
                        changed = true
                        continue
                    }
                    val method = inst.method
                    val objName = namedVariable(inst.obj)
                    if (method is SingleMethodRef && objName != null) {
                        val binding = bindings[objName]
                        if (binding != null && inlineThroughBinding(index, inst, binding, method)) {
                            changed = true
                        }
                    }
                }

                is Instruction.WithAssignment -> {
                    namedVariable(inst.target)?.let(bindings::remove)
                }

                else -> Unit
            }
        }

        return changed
    }
}
