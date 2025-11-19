package lang.aurum.ir

sealed interface Operator {
    val symbol: String
    val precedence: Int
    val associativity: Associativity
    val defaultOpcode: Opcode?
}

data class CustomOpeartor(
    override val symbol: String,
    override val precedence: Int,
    override val associativity: Associativity = Associativity.LEFT_TO_RIGHT,
    override val defaultOpcode: Opcode? = null
) : Operator

enum class BinaryOperator(
    override val symbol: String,
    override val precedence: Int,
    override val associativity: Associativity,
    override val defaultOpcode: Opcode? = null
) : Operator {
    MUL    ("*"  , 12*10, Associativity.LEFT_TO_RIGHT, Opcode.Mul       ),
    DIVIDE ("/"  , 12*10, Associativity.LEFT_TO_RIGHT, Opcode.Div       ),
    MOD    ("%"  , 12*10, Associativity.LEFT_TO_RIGHT, Opcode.Mod       ),
    ADD    ("+"  , 11*10, Associativity.LEFT_TO_RIGHT, Opcode.Add       ),
    SUB    ("-"  , 11*10, Associativity.LEFT_TO_RIGHT, Opcode.Sub       ),
    SHL    ("<<" , 10*10, Associativity.LEFT_TO_RIGHT, Opcode.Shl       ),
    SHR    (">>" , 10*10, Associativity.LEFT_TO_RIGHT, Opcode.Shr       ),
    USHR   (">>>", 10*10, Associativity.LEFT_TO_RIGHT, Opcode.Ushr      ),
    LT     ("<"  , 9*10 , Associativity.LEFT_TO_RIGHT, Opcode.CmpLt     ),
    LE     ("<=" , 9*10 , Associativity.LEFT_TO_RIGHT, Opcode.CmpLe     ),
    GT     (">"  , 9*10 , Associativity.LEFT_TO_RIGHT, Opcode.CmpGt     ),
    GE     (">=" , 9*10 , Associativity.LEFT_TO_RIGHT, Opcode.CmpGe     ),
    IS     ("is" , 9*10 , Associativity.LEFT_TO_RIGHT, Opcode.InstanceOf),
    EQ     ("==" , 8*10 , Associativity.LEFT_TO_RIGHT, Opcode.CmpEq     ),
    NEQ    ("!=" , 8*10 , Associativity.LEFT_TO_RIGHT, Opcode.CmpNe     ),
    B_AND  ("&"  , 7*10 , Associativity.LEFT_TO_RIGHT, Opcode.And       ),
    XOR    ("^"  , 6*10 , Associativity.LEFT_TO_RIGHT, Opcode.Xor       ),
    B_OR   ("|"  , 5*10 , Associativity.LEFT_TO_RIGHT, Opcode.Or        ),
    AND    ("&&" , 4*10 , Associativity.LEFT_TO_RIGHT, Opcode.And       ),
    OR     ("||" , 3*10 , Associativity.LEFT_TO_RIGHT, Opcode.Or        );
}

enum class UnaryOperator(
    override val symbol: String,
    override val precedence: Int,
    override val associativity: Associativity,
    override val defaultOpcode: Opcode? = null
) : Operator {
    POST_INC("++", 14*10, Associativity.RIGHT_TO_LEFT, Opcode.Add),
    POST_DEC("--", 14*10, Associativity.RIGHT_TO_LEFT, Opcode.Sub),
    INC     ("++", 13*10, Associativity.LEFT_TO_RIGHT, Opcode.Add),
    DEC     ("--", 13*10, Associativity.LEFT_TO_RIGHT, Opcode.Sub),
    PLUS    ("+" , 13*10, Associativity.LEFT_TO_RIGHT),
    MINUS   ("-" , 13*10, Associativity.LEFT_TO_RIGHT, Opcode.Neg),
    NEG     ("!" , 13*10, Associativity.LEFT_TO_RIGHT, Opcode.Neg),
    COMPL   ("~" , 13*10, Associativity.LEFT_TO_RIGHT, Opcode.Neg);
}

/**
 * For unary operators:
 * LEFT_TO_RIGHT means that this operator is prefix and RIGHT_TO_LEFT means that this operator is postfix
 */
enum class Associativity {
    RIGHT_TO_LEFT, LEFT_TO_RIGHT
}

