package lang.aurum.attribute

import lang.aurum.ir.Associativity
import lang.aurum.model.Attribute

data class OperatorAttribute (
    val symbol: String,
    val precedence: Int,
    val associativity: Associativity = Associativity.LEFT_TO_RIGHT,
    val isBinary: Boolean = true
) : Attribute {
    val values: MutableMap<String, Any?> = mutableMapOf(
        "symbol" to symbol,
        "precedence" to precedence,
        "associativity" to associativity
    )

    override fun name(): String {
        return "Operator"
    }

    override fun values(): MutableMap<String, Any?> {
        return values
    }
}
