package lang.aurum.attribute

import lang.aurum.ir.Associativity
import lang.aurum.ir.Opcode
import lang.aurum.ir.Operator
import lang.aurum.model.Attribute

data class OperatorAttribute (
    val operator: Operator
) : Attribute {
    val values: MutableMap<String, Any?> = mutableMapOf(
        "symbol" to operator.symbol,
        "precedence" to operator.precedence,
        "associativity" to operator.associativity,
    )

    override fun name(): String {
        return "Operator"
    }

    override fun values(): MutableMap<String, Any?> {
        return values
    }

    val symbol: String = operator.symbol
    val precedence: Int = operator.precedence
    val associativity: Associativity = operator.associativity
    val isBinary: Boolean = operator.isBinary
    val defaultOpcode: Opcode? = operator.defaultOpcode
}
