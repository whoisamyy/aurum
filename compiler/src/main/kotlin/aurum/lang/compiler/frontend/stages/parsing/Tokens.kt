package aurum.lang.compiler.frontend.stages.parsing

import kotlin.reflect.full.primaryConstructor

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Token> constructor(): (Int, Int) -> T {
    return T::class.primaryConstructor as (Int, Int) -> T
}

@Suppress("UNCHECKED_CAST")
enum class TokenType(
    val regex: Regex,
    val tokenConstructor: (Int, Int) -> Token
) : ((Int, Int) -> Token) by tokenConstructor {
    Number(Regex("[+-]?(0b[01]+[lL]?|0[0-8]+[lL]?|0x[\\da-fA-F]+[lL]?|\\d+\\.\\d+[dDfF]?|\\d+[fFdDlL]?)"), constructor<Token.Number>()),
    String(Regex("(\".*?\")|(\'.*?\')"), constructor<Token.String>()),
    Identifier(Regex("[a-zA-Z_$]\\w*"), constructor<Token.Identifier>()),
    OperatorSymbol(Regex("[+\\-*/\\\\%|&!?~^<>=]+|as|is|\\[]|\\(\\)"), constructor<Token.OperatorSymbol>()),
    Comment(Regex("#.*|#\\*[\\s\\S]+\\*#"), constructor<Token.Comment>()),

    As(Regex("as"), constructor<Token.As>()),
    QuestionMark(Regex("\\?"), constructor<Token.QuestionMark>()),
    Bar(Regex("\\|"), constructor<Token.Bar>()),
    Ampersand(Regex("&"), constructor<Token.Ampersand>()),
    VarargSuffix(Regex("\\.\\.\\."), constructor<Token.VarargSuffix>()),
    Arrow(Regex("->"), constructor<Token.Arrow>()),
    DoubleArrow(Regex("=>"), constructor<Token.DoubleArrow>()),
    At(Regex("@"), constructor<Token.At>()),
    Equal(Regex("="), constructor<Token.Equal>()),
    LCurlyBrace(Regex("\\{"), constructor<Token.LCurlyBrace>()),
    RCurlyBrace(Regex("}"), constructor<Token.RCurlyBrace>()),
    LParen(Regex("\\("), constructor<Token.LParen>()),
    RParen(Regex("\\)"), constructor<Token.RParen>()),
    LBracket(Regex("\\["), constructor<Token.LBracket>()),
    RBracket(Regex("]"), constructor<Token.RBracket>()),
    Colon(Regex(":"), constructor<Token.Colon>()),
    Semicolon(Regex(";"), constructor<Token.Semicolon>()),
    Comma(Regex(","), constructor<Token.Comma>()),
    Dot(Regex("\\."), constructor<Token.Dot>()),
    Boolean(Regex("true|false"), constructor<Token.Boolean>()),
    Null(Regex("null"), constructor<Token.Null>()),
    Fn(Regex("fn"), constructor<Token.Fn>()),
    Init(Regex("init"), constructor<Token.Init>()),
    Class(Regex("class"), constructor<Token.Class>()),
    When(Regex("when"), constructor<Token.When>()),
    Package(Regex("package"), constructor<Token.Package>()),
    Interface(Regex("interface"), constructor<Token.Interface>()),
    Type(Regex("type"), constructor<Token.Type>()),
    Let(Regex("let"), constructor<Token.Let>()),
    Var(Regex("var"), constructor<Token.Var>()),
    Match(Regex("match"), constructor<Token.Match>()),
    Default(Regex("default"), constructor<Token.Default>()),
    Operator(Regex("operator"), constructor<Token.Operator>()),
    Return(Regex("return"), constructor<Token.Return>()),
    If(Regex("if"), constructor<Token.If>()),
    Else(Regex("else"), constructor<Token.Else>()),
    Elif(Regex("elif"), constructor<Token.Elif>()),
    Do(Regex("do"), constructor<Token.Do>()),
    Continue(Regex("continue"), constructor<Token.Continue>()),
    Break(Regex("break"), constructor<Token.Break>()),
    For(Regex("for"), constructor<Token.For>()),
    In(Regex("in"), constructor<Token.In>()),
    While(Regex("while"), constructor<Token.While>()),
    Loop(Regex("loop"), constructor<Token.Loop>()),
    Extends(Regex("extends"), constructor<Token.Extends>()),
    Final(Regex("final"), constructor<Token.Final>()),
    Abstract(Regex("abstract"), constructor<Token.Abstract>()),
    Static(Regex("static"), constructor<Token.Static>()),
    Private(Regex("private"), constructor<Token.Private>()),
    Public(Regex("public"), constructor<Token.Public>()),
    Import(Regex("import"), constructor<Token.Import>()),
    EndLine(Regex("[\n\r]"), constructor<Token.EndLine>());
}

abstract class Token : ASTNode {
    //    override val children: List<ASTNode> = emptyNodes
    abstract var value: kotlin.String
        internal set

    abstract val line: Int
    abstract val char: Int

    final override fun toString(): kotlin.String = value

    val positionString get() = "$line:$char"
    val position get() = line to char
    val infoString get() = "${this::class.simpleName}@${this.positionString}=$value"

    object EOF : Token() {
        override var value: kotlin.String = ""
        override val line: Int = -1
        override val char: Int = -1
    }

    data class As (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "as"
    }
    data class QuestionMark (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "?"
    }
    data class Bar (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "|"
    }
    data class Ampersand (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "&"
    }
    data class VarargSuffix (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "..."
    }
    data class Arrow (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "->"
    }
    data class DoubleArrow (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "=>"
    }
    data class At (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "@"
    }
    data class Equal (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "="
    }
    data class LCurlyBrace (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "{"
    }
    data class RCurlyBrace (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "}"
    }
    data class LParen (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "("
    }
    data class RParen (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = ")"
    }
    data class LBracket (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "["
    }
    data class RBracket (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "]"
    }
    data class Colon (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = ":"
    }
    data class Semicolon (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = ";"
    }
    data class Comma (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = ","
    }
    data class Dot (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "."
    }
    data class OperatorSymbol (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = ""
    }
    data class Number (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = ""
    }
    data class String (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = ""
    }
    data class Boolean (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = ""
    }
    data class Null (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "null"
    }
    data class Fn (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "fn"
    }
    data class Init (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "init"
    }
    data class Class (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "class"
    }
    data class When (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "when"
    }
    data class Package (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "package"
    }
    data class Interface (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "interface"
    }
    data class Type (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "type"
    }
    data class Let (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "let"
    }
    data class Var (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "var"
    }
    data class Match (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "match"
    }
    data class Default (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "default"
    }
    data class Operator (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "operator"
    }
    data class Return (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "return"
    }
    data class If (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "if"
    }
    data class Else (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "else"
    }
    data class Elif (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "elif"
    }
    data class Do (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "do"
    }
    data class Continue (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "continue"
    }
    data class Break (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "break"
    }
    data class For (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "for"
    }
    data class In (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "in"
    }
    data class While (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "while"
    }
    data class Loop (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "loop"
    }
    data class Extends (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "extends"
    }
    data class Final (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "final"
    }
    data class Abstract (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "abstract"
    }
    data class Static (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "static"
    }
    data class Private (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "private"
    }
    data class Public (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "public"
    }
    data class Import (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "import"
    }
    data class Identifier (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = ""
    }
    data class Comment (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = ""
    }
    data class EndLine (
        override val line: Int,
        override val char: Int
    ) : Token() {
        override var value: kotlin.String = "\\n"
    }
}