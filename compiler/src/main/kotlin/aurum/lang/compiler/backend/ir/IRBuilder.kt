package aurum.lang.compiler.backend.ir

import aurum.lang.ir.Instruction

/**
 * Appends IR instructions and binds [Instruction.WithAssignment] producers on [Variable]s.
 */
internal class IRBuilder(
    private val code: MutableList<Instruction>,
) {
    fun emit(instruction: Instruction) {
        code += instruction
    }

    fun emit(instruction: Instruction.WithAssignment, variable: Variable) {
        code += instruction
        variable.producer = instruction
    }

    operator fun plusAssign(instruction: Instruction) = emit(instruction)
}
