@file:Suppress("SpellCheckingInspection")

package aurum.lang.model.attribute

import aurum.lang.model.Attribute

sealed interface Operator {
    val symbol: String
    val precedence: Int
    val associativity: Associativity
    val isBinary: Boolean

    companion object {
        const val DEFAULT_PRECEDENCE = 130
    }
}

data class CustomOperator (
    override val symbol: String,
    override val precedence: Int,
    override val associativity: Associativity = Associativity.LEFT_TO_RIGHT,
    override val isBinary: Boolean = true,
) : Operator, Attribute {
    override fun name(): String = "CustomOperator"

    override fun values(): Map<String, Any?> = mapOf(
        "symbol" to symbol,
        "precedence" to precedence,
        "associativity" to associativity,
        "isBinary" to isBinary
    )
}

enum class BinaryOperator (
    override val symbol: String,
    override val precedence: Int,
    override val associativity: Associativity,
) : Operator {
    MUL    ("*"  , 12*10, Associativity.LEFT_TO_RIGHT),
    DIVIDE ("/"  , 12*10, Associativity.LEFT_TO_RIGHT),
    MOD    ("%"  , 12*10, Associativity.LEFT_TO_RIGHT),
    ADD    ("+"  , 11*10, Associativity.LEFT_TO_RIGHT),
    SUB    ("-"  , 11*10, Associativity.LEFT_TO_RIGHT),
    SHL    ("<<" , 10*10, Associativity.LEFT_TO_RIGHT),
    SHR    (">>" , 10*10, Associativity.LEFT_TO_RIGHT),
    USHR   (">>>", 10*10, Associativity.LEFT_TO_RIGHT),
    LT     ("<"  , 9*10 , Associativity.LEFT_TO_RIGHT),
    LE     ("<=" , 9*10 , Associativity.LEFT_TO_RIGHT),
    GT     (">"  , 9*10 , Associativity.LEFT_TO_RIGHT),
    GE     (">=" , 9*10 , Associativity.LEFT_TO_RIGHT),
    IS     ("is" , 9*10 , Associativity.LEFT_TO_RIGHT),
    EQ     ("==" , 8*10 , Associativity.LEFT_TO_RIGHT),
    NEQ    ("!=" , 8*10 , Associativity.LEFT_TO_RIGHT),
    B_AND  ("&"  , 7*10 , Associativity.LEFT_TO_RIGHT),
    XOR    ("^"  , 6*10 , Associativity.LEFT_TO_RIGHT),
    B_OR   ("|"  , 5*10 , Associativity.LEFT_TO_RIGHT),
    AND    ("&&" , 4*10 , Associativity.LEFT_TO_RIGHT),
    OR     ("||" , 3*10 , Associativity.LEFT_TO_RIGHT);

    override val isBinary: Boolean = true
}

enum class UnaryOperator (
    override val symbol: String,
    override val precedence: Int,
    override val associativity: Associativity,
) : Operator {
    POST_INC("++", 14*10, Associativity.POSTFIX),
    POST_DEC("--", 14*10, Associativity.POSTFIX),
    INC     ("++", 13*10, Associativity.PREFIX),
    DEC     ("--", 13*10, Associativity.PREFIX),
    PLUS    ("+" , 13*10, Associativity.PREFIX),
    MINUS   ("-" , 13*10, Associativity.PREFIX),
    NEG     ("!" , 13*10, Associativity.PREFIX),
    COMPL   ("~" , 13*10, Associativity.PREFIX);

    override val isBinary: Boolean = false
}

/**
 * For unary operators:
 * LEFT_TO_RIGHT means that this operator is prefix and RIGHT_TO_LEFT means that this operator is postfix operator
 */
enum class Associativity {
    RIGHT_TO_LEFT, LEFT_TO_RIGHT;

    companion object {
        @JvmStatic
        val PREFIX = LEFT_TO_RIGHT
        @JvmStatic
        val POSTFIX = RIGHT_TO_LEFT
    }
}

