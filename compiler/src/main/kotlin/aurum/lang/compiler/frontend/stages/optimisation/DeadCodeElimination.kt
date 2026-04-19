package aurum.lang.compiler.frontend.stages.optimisation
//
//import aurum.lang.ir.*
//import aurum.lang.compiler.frontend.stages.FileContext
//import aurum.lang.compiler.frontend.attribute.*
//
//object DeadCodeElimination : OptimizationPass {
//    override fun run(
//        fileCtx: FileContext,
//        instructions: MutableList<Instruction>
//    ): Boolean {
//        var changed = false
//
//        fun eliminate() {
//            val usages = getUsages(instructions)
//            val newList = mutableListOf<Instruction>()
//            for (inst in instructions) {
//                val targetName = when (inst) {
//                    is Instruction.WithAssignment -> (inst.target as? Reference)?.name
//                    else -> null
//                }
//
//                val hasSideEffects = when (inst) {
//                    is PutField,
//                    is ArrayStore,
//                    is Throw,
//                    is Return,
//                    is Jump,
//                    is JumpIf,
//                    is TryBegin,
//                    is TryEnd,
//                    is Catch,
//                    is Call,
//                    is CallMethod,
//                    is CallVirtual,
//                    is InvokeConstructor,
//                    is Switch -> true
//
//                    is Move -> inst.target is FieldRef
//                    else -> false
//                }
//
//                if (targetName != null && targetName !in usages /*&& !hasSideEffects*/) {
//                    if (hasSideEffects)
//                        when (inst) {
//                            is Call -> newList.add(inst.copy(target = Reference.Empty))
//                            is CallMethod -> newList.add(inst.copy(target = Reference.Empty))
//                            is CallVirtual -> newList.add(inst.copy(target = Reference.Empty))
//                        }
//
//                    changed = true
//                    continue
//                }
//
//                newList.add(inst)
//            }
//            if (changed) {
//                instructions.clear()
//                instructions.addAll(newList)
//            }
//        }
//
//        eliminate()
//        eliminate()
//
//
//        return changed
//    }
//
//    private fun getUsages(
//        instructions: MutableList<Instruction>
//    ): MutableSet<String> {
//        val used: MutableMap<String, RValue> = mutableMapOf()
//
//        fun addRef(ref: RValue?) {
//            when (ref) {
//                is Reference -> {
//                    used[ref.name] = ref
//                }
//                is RValue -> {} // ignore constant pool refs, labels
//                else -> {}
//            }
//        }
//
//        for (inst in instructions) {
//            when (inst) {
//                is Move -> addRef(inst.ref)
//                is BinaryOp -> {
//                    addRef(inst.left); addRef(inst.right)
//                }
//
//                is Neg -> addRef(inst.ref)
//                is JumpIf -> addRef(inst.cond)
//                is Return -> addRef(inst.value)
//                is Throw -> addRef(inst.ref)
//                is Call -> inst.args.forEach(::addRef)
//                is CallMethod -> {
//                    addRef(inst.obj); inst.args.forEach(::addRef)
//                }
//
//                is CallVirtual -> {
//                    addRef(inst.obj); inst.args.forEach(::addRef)
//                }
//
//                is InvokeConstructor -> {
//                    addRef(inst.obj); inst.args.forEach(::addRef)
//                }
//
//                is Closure -> inst.captured.forEach(::addRef)
//                is GetMember -> addRef(inst.obj)
//                is GetMethod -> addRef(inst.obj)
//                is GetField -> addRef(inst.obj)
//                is PutField -> {
//                    addRef(inst.obj); addRef(inst.value)
//                }
//
//                is GetMethodStatic -> addRef(inst.method)
//                is GetStatic -> addRef(inst.field)
//                is ArrayLoad -> {
//                    addRef(inst.array); addRef(inst.index)
//                }
//
//                is ArrayStore -> {
//                    addRef(inst.array); addRef(inst.index); addRef(inst.value)
//                }
//
//                is Cast -> addRef(inst.ref)
//                is InstanceOf -> addRef(inst.ref)
//                is TypeOf -> addRef(inst.ref)
//                is Phi -> inst.incoming.values.forEach(::addRef)
//                is Switch -> {
//                    addRef(inst.ref)
//                    inst.cases.keys.forEach(::addRef)
//                }
//
//                else -> {}
//            }
//        }
//
//        used.values.forEach {
//            val ref = (it as Reference)
//            if (!ref.attributes.contains<UsagesAttribute>())
//                ref.attributes += UsagesAttribute()
//            ref.attributes.get<UsagesAttribute>()!!.usages++
//        }
//
//        return used.keys
//    }
//}