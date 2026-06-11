package aurum.lang.ir

import aurum.lang.model.attribute.BinaryOperator
import aurum.lang.model.attribute.UnaryOperator

object Operators {
    @JvmField
    val OPERATOR_OPCODES = mapOf(
        BinaryOperator.MUL to Opcode.Mul,
        BinaryOperator.DIVIDE to Opcode.Div,
        BinaryOperator.MOD to Opcode.Mod,
        BinaryOperator.ADD to Opcode.Add,
        BinaryOperator.SUB to Opcode.Sub,
        BinaryOperator.SHL to Opcode.Shl,
        BinaryOperator.SHR to Opcode.Shr,
        BinaryOperator.USHR to Opcode.Ushr,
        BinaryOperator.LT to Opcode.CmpLt,
        BinaryOperator.LE to Opcode.CmpLe,
        BinaryOperator.GT to Opcode.CmpGt,
        BinaryOperator.GE to Opcode.CmpGe,
        BinaryOperator.IS to Opcode.InstanceOf,
        BinaryOperator.EQ to Opcode.CmpEq,
        BinaryOperator.NEQ to Opcode.CmpNe,
        BinaryOperator.B_AND to Opcode.And,
        BinaryOperator.XOR to Opcode.Xor,
        BinaryOperator.B_OR to Opcode.Or,
        BinaryOperator.AND to Opcode.And,
        BinaryOperator.OR to Opcode.Or,
        UnaryOperator.POST_INC to Opcode.Add,
        UnaryOperator.POST_DEC to Opcode.Sub,
        UnaryOperator.INC to Opcode.Add,
        UnaryOperator.DEC to Opcode.Sub,
        UnaryOperator.PLUS to null,
        UnaryOperator.MINUS to Opcode.Neg,
        UnaryOperator.NEG to Opcode.Neg,
        UnaryOperator.COMPL to Opcode.Neg,
    )
}