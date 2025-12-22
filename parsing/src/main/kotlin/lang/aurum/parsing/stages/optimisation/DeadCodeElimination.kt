package lang.aurum.parsing.stages.optimisation

import lang.aurum.ir.*
import lang.aurum.parsing.stages.FileContext

object DeadCodeElimination : OptimizationPass {
    override fun run(
        fileCtx: FileContext,
        instructions: MutableList<Instruction>
    ): Boolean {
        // Шаг 1: собрать множество всех использованных имён
//        val used = getUsages(instructions)
        val usages = getUsages(instructions)

        // Шаг 2: определить, какие инструкции можно удалить
        val newList = mutableListOf<Instruction>()
        var changed = false

        for (inst in instructions) {
            val targetName = when (inst) {
                is Move -> inst.target.name
                is BinaryOp -> inst.target.name
                is Neg -> inst.target.name
                is Call -> inst.target.name
                is CallMethod -> inst.target.name
                is CallVirtual -> inst.target.name
                is InvokeConstructor -> inst.target.name
                is Closure -> inst.target.name
                is GetMember -> inst.target.name
                is GetMethod -> inst.target.name
                is GetMethodStatic -> inst.target.name
                is GetField -> inst.target.name
                is GetStatic -> inst.target.name
                is ArrayLoad -> inst.target.name
                is Cast -> inst.target.name
                is InstanceOf -> inst.target.name
                is TypeOf -> inst.target.name
                is Phi -> inst.target.name
                is New -> inst.target.name
                is NewArray -> inst.target.name
                else -> null
            }

            val hasSideEffects = when (inst) {
                is PutField,
                is PutStatic,
                is ArrayStore,
                is Throw,
                is Return,
                is Jump,
                is JumpIf,
                is TryBegin,
                is TryEnd,
                is Catch,
                is Call,
                is CallMethod,
                is CallVirtual,
                is InvokeConstructor,
                is Switch -> true
                else -> false
            }

            // если инструкция не имеет побочных эффектов и результат не используется — удаляем
            if (targetName != null && targetName !in usages && !hasSideEffects) {
                changed = true
//                newList.add(Nop)
                continue
            }

            newList.add(inst)
        }

        if (changed) {
            instructions.clear()
            instructions.addAll(newList)
        }

        return changed
    }

    private fun getUsages(
        instructions: MutableList<Instruction>
    ): MutableSet<String> {
        val used: MutableSet<String> = mutableSetOf()

        fun addRef(ref: RValue?) {
            when (ref) {
                is Reference -> used.add(ref.name)
                is RValue -> {} // ignore constant pool refs, labels
                else -> {}
            }
        }

        // Собираем все ссылки
        for (inst in instructions) {
            when (inst) {
                is Move -> addRef(inst.ref)
                is BinaryOp -> {
                    addRef(inst.left); addRef(inst.right)
                }

                is Neg -> addRef(inst.ref)
                is JumpIf -> addRef(inst.cond)
                is Return -> addRef(inst.value)
                is Throw -> addRef(inst.ref)
                is Call -> inst.args.forEach(::addRef)
                is CallMethod -> {
                    addRef(inst.obj); inst.args.forEach(::addRef)
                }

                is CallVirtual -> {
                    addRef(inst.obj); inst.args.forEach(::addRef)
                }

                is InvokeConstructor -> {
                    addRef(inst.obj); inst.args.forEach(::addRef)
                }

                is Closure -> inst.captured.forEach(::addRef)
                is GetMember -> addRef(inst.obj)
                is GetMethod -> addRef(inst.obj)
                is GetField -> addRef(inst.obj)
                is PutField -> {
                    addRef(inst.obj); addRef(inst.value)
                }

                is GetMethodStatic -> {}
                is GetStatic -> {}
                is PutStatic -> addRef(inst.value)
                is ArrayLoad -> {
                    addRef(inst.array); addRef(inst.index)
                }

                is ArrayStore -> {
                    addRef(inst.array); addRef(inst.index); addRef(inst.value)
                }

                is Cast -> addRef(inst.ref)
                is InstanceOf -> addRef(inst.ref)
                is TypeOf -> addRef(inst.ref)
                is Phi -> inst.incoming.values.forEach(::addRef)
                is Switch -> {
                    addRef(inst.ref)
                    inst.cases.keys.forEach(::addRef)
                }

                else -> {}
            }
        }

        return used
    }
}