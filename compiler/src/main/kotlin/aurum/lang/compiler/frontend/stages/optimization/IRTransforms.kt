package aurum.lang.compiler.frontend.stages.optimization

import aurum.lang.ir.*

internal fun namedVariable(lvalue: LValue): String? =
    (lvalue as? Reference.Named)?.name

internal fun namedVariable(rvalue: RValue): String? =
    (rvalue as? Reference.Named)?.name

/**
 * Applies [transform] to every [RValue] operand of [instruction].
 */
internal fun mapInstructionOperands(
    instruction: Instruction,
    transform: (RValue) -> RValue,
): Instruction = when (instruction) {
    is Move -> instruction.copy(ref = transform(instruction.ref))
    is BinaryOp -> instruction.copy(
        left = transform(instruction.left),
        right = transform(instruction.right),
    )
    is Neg -> instruction.copy(ref = transform(instruction.ref))
    is JumpIf -> instruction.copy(cond = transform(instruction.cond))
    is JumpIfN -> instruction.copy(cond = transform(instruction.cond))
//    is Return -> instruction.copy(value = instruction.value?.let(transform))
    is Throw -> instruction.copy(ref = transform(instruction.ref))
    is Call -> instruction.copy(
        method = instruction.method,
        args = instruction.args.map(transform),
    )
    is CallMethod -> instruction.copy(
        obj = transform(instruction.obj),
        method = instruction.method,
        args = instruction.args.map(transform),
    )
    is CallVirtual -> instruction.copy(
        obj = transform(instruction.obj),
        method = instruction.method,
        args = instruction.args.map(transform),
    )
    is InvokeConstructor -> instruction.copy(
        obj = transform(instruction.obj),
        args = instruction.args.map(transform),
    )
    is Closure -> instruction.copy(
        func = instruction.func,
        captured = instruction.captured.map(transform),
    )
    is New -> instruction
    is NewArray -> instruction.copy(
        elementType = instruction.elementType,
        sizeRef = transform(instruction.sizeRef),
    )
    is GetField -> instruction.copy(
        obj = transform(instruction.obj),
        field = instruction.field,
    )
    is PutField -> instruction.copy(
        obj = transform(instruction.obj),
        value = transform(instruction.value),
        field = instruction.field,
    )
    is GetMember -> instruction.copy(
        obj = transform(instruction.obj),
        member = instruction.member,
    )
    is GetMethod -> instruction.copy(
        obj = transform(instruction.obj),
        method = instruction.method,
    )
    is ArrayLoad -> instruction.copy(
        array = transform(instruction.array),
        index = transform(instruction.index),
    )
    is ArrayStore -> instruction.copy(
        array = transform(instruction.array),
        index = transform(instruction.index),
        value = transform(instruction.value),
    )
    is Cast -> instruction.copy(ref = transform(instruction.ref), type = instruction.type)
    is InstanceOf -> instruction.copy(ref = transform(instruction.ref), type = instruction.type)
    is TypeOf -> instruction.copy(ref = transform(instruction.ref))
    is Switch -> instruction.copy(
        ref = transform(instruction.ref),
        cases = instruction.cases.mapKeys { (key, _) -> transform(key) },
        defaultLabel = instruction.defaultLabel,
    )
    else -> instruction
}

internal fun forEachRValueUse(instruction: Instruction, action: (RValue) -> Unit) {
    when (instruction) {
        is Move -> action(instruction.ref)
        is BinaryOp -> {
            action(instruction.left)
            action(instruction.right)
        }
        is Neg -> action(instruction.ref)
        is JumpIf -> action(instruction.cond)
        is JumpIfN -> action(instruction.cond)
        is Return -> instruction.value?.let(action)
        is Throw -> action(instruction.ref)
        is Call -> instruction.args.forEach(action)
        is CallMethod -> {
            action(instruction.obj)
            instruction.args.forEach(action)
        }
        is CallVirtual -> {
            action(instruction.obj)
            instruction.args.forEach(action)
        }
        is InvokeConstructor -> {
            action(instruction.obj)
            instruction.args.forEach(action)
        }
        is Closure -> instruction.captured.forEach(action)
        is NewArray -> action(instruction.sizeRef)
        is GetField -> action(instruction.obj)
        is PutField -> {
            action(instruction.obj)
            action(instruction.value)
        }
        is GetMember -> action(instruction.obj)
        is GetMethod -> action(instruction.obj)
        is ArrayLoad -> {
            action(instruction.array)
            action(instruction.index)
        }
        is ArrayStore -> {
            action(instruction.array)
            action(instruction.index)
            action(instruction.value)
        }
        is Cast -> action(instruction.ref)
        is InstanceOf -> action(instruction.ref)
        is TypeOf -> action(instruction.ref)
        is Switch -> {
            action(instruction.ref)
            instruction.cases.keys.forEach(action)
        }
        else -> Unit
    }
}

internal fun definedVariable(instruction: Instruction): String? =
    (instruction as? Instruction.WithAssignment)?.let { namedVariable(it.target) }

internal fun hasSideEffects(instruction: Instruction): Boolean = when (instruction) {
    is PutField,
    is ArrayStore,
    is Throw,
    is Return,
    is Jump,
    is JumpIf,
    is JumpIfN,
    is TryBegin,
    is TryEnd,
    is Catch,
    is Call,
    is CallMethod,
    is CallVirtual,
    is InvokeConstructor,
    is Switch -> true

    is Move -> instruction.target is FieldRef
    else -> false
}

internal fun invalidateNamedBindings(
    target: LValue,
    bindings: MutableMap<String, *>,
) {
    namedVariable(target)?.let(bindings::remove)
}
