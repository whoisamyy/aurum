package aurum.lang.compiler.frontend.stages.parsing

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
class AST internal constructor(private val nodes: List<ASTNode>) : List<ASTNode> by nodes

// ==========================================
// Base file nodes
// ==========================================
interface ASTNode {
    override fun toString(): String

    class QualifiedName(val identifiers: List<String>) : ASTNode {
        override fun toString(): String = identifiers.joinToString(".")
    }

    class Import(val import: QualifiedName, val alias: String? = null) : ASTNode {
        override fun toString(): String = buildString {
            append("import $import")
            alias?.let { append(" as $it") }
        }
    }

    class Package(val packageName: QualifiedName) : ASTNode {
        override fun toString(): String = "package $packageName"
    }

    // ==========================================
    // Metadata
    // ==========================================

    class Decorator(val name: QualifiedName, val args: List<Expression>? = null) : ASTNode {
        override fun toString(): String = "@$name${args?.joinToString(",", "(", ")") ?: ""}"
    }

    sealed interface Modifier : ASTNode {
        object Final : Modifier { override fun toString() = "final" }
        object Abstract : Modifier { override fun toString() = "abstract" }
        object Static : Modifier { override fun toString() = "static" }
        object Private : Modifier { override fun toString() = "private" }
        object Protected : Modifier { override fun toString() = "protected" }
        object Public : Modifier { override fun toString() = "public" }
    }

    // ==========================================
    // Type Expressions
    // ==========================================

    sealed interface TypeSuffix : ASTNode {
        object Vararg : TypeSuffix { override fun toString() = "..." }
        object Array : TypeSuffix { override fun toString() = "[]" }
    }

    sealed interface TypeArg : ASTNode {
        class Wildcard(val extends: TypeExpr? = null) : TypeArg {
            override fun toString(): String = if (extends == null) "?" else "? : $extends"
        }
    }

    class TypeParam(val name: String, val bound: TypeExpr? = null) : ASTNode {
        override fun toString(): String = if (bound == null) name else "$name : $bound"
    }

    sealed class TypeExpr : Expression, TypeArg {
        abstract val suffix: List<TypeSuffix>?

        protected fun formatSuffix() = suffix?.joinToString("") ?: ""
    }

    class ParenthesizedType (
        val type: TypeExpr,
        override val suffix: List<TypeSuffix>? = null
    ) : TypeExpr() {
        override fun toString(): String = "($type)${formatSuffix()}"
    }

    class PlainType(val name: QualifiedName, override val suffix: List<TypeSuffix>? = null) : TypeExpr() {
        override fun toString(): String = "$name${formatSuffix()}"
    }

    class ParametrizedType(
        val name: QualifiedName,
        val typeArgs: List<TypeArg>,
        override val suffix: List<TypeSuffix>? = null
    ) : TypeExpr() {
        override fun toString(): String =
            "$name${typeArgs.joinToString(", ", "[", "]")}${formatSuffix()}"
    }

    class UnionType(val types: List<TypeExpr>, override val suffix: List<TypeSuffix>? = null) : TypeExpr() {
        override fun toString(): String {
            val base = types.joinToString(" | ")
            return if (suffix == null) base else "($base)${formatSuffix()}"
        }
    }

    class IntersectionType(val types: List<TypeExpr>, override val suffix: List<TypeSuffix>? = null) : TypeExpr() {
        override fun toString(): String {
            val base = types.joinToString(" & ")
            return if (suffix == null) base else "($base)${formatSuffix()}"
        }
    }

    class LambdaType(
        val paramTypes: List<TypeExpr>? = null,
        val returnType: TypeExpr,
        override val suffix: List<TypeSuffix>? = null
    ) : TypeExpr() {
        override fun toString(): String {
            val params = paramTypes?.joinToString(", ", "(", ")") ?: "()"
            val base = "$params -> $returnType"
            return if (suffix == null) base else "($base)${formatSuffix()}"
        }
    }

    // ==========================================
    // Expressions
    // ==========================================

    sealed interface Expression : ASTNode, CodeBlock, Statement
    sealed interface LValueExpression : Expression
    sealed interface RValueExpression : Expression

    class IdentifierExpression (
        val identifier: String
    ) : LValueExpression, RValueExpression {
        override fun toString(): String = identifier
    }

    sealed interface Literal : RValueExpression {
        object Null : Literal {
            override fun toString(): kotlin.String = "null"
        }
        class String (
            val value: kotlin.String
        ) : Literal, LValueExpression {
            override fun toString(): kotlin.String = value
        }
        class Number (
            val value: kotlin.Number
        ) : Literal, LValueExpression {
            override fun toString(): kotlin.String = value.toString()
        }
        object True : Literal {
            override fun toString(): kotlin.String = "true"
        }
        object False : Literal {
            override fun toString(): kotlin.String = "false"
        }
    }

    class ParenthesizedExpression (
        val expression: Expression
    ) : RValueExpression {
        override fun toString(): String = "(${expression})"
    }

    class ArrayExpression (
        val expressions: List<Expression>?
    ) : RValueExpression {
        override fun toString(): String = expressions?.toString() ?: "[]"
    }

    class TupleExpression (
        val expressions: List<Expression>?
    ) : RValueExpression {
        override fun toString(): String = expressions?.joinToString(", ", "(", ")") ?: "()"
    }

    class MapExpression (
        val keyValues: List<Pair<Expression, Expression>>?
    ) : RValueExpression {
        override fun toString(): String = keyValues?.joinToString { (k, v) -> "$k : $v" } ?: "[:]"
    }

    class NamedTupleExpression (
        val keyValues: List<Pair<String, Expression>>?
    ) : RValueExpression {
        override fun toString(): String = keyValues?.joinToString(", ", "(", ")") { (k, v) -> "$k : $v" } ?: "(:)"
    }

    class LambdaExpression (
        val parameters: List<LambdaParameter>?,
        val codeBlock: CodeBlock
    ) : RValueExpression {
        override fun toString(): String = (parameters?.joinToString(", ", "(", ")") ?: "()") +
                " => " + codeBlock
    }

    data class LambdaParameter (
        val name: String,
        val type: TypeExpr?
    ) : ASTNode {
        override fun toString(): String = name + if (type == null) "" else ": $type"
    }

    class BinaryExpression (
        val expressions: List<Expression>,
        val operators: List<Token.OperatorSymbol>
    ) : RValueExpression {
        init {
            if (operators.size != expressions.size - 1)
                error("Operator count should be one less than expressions count")
        }

        override fun toString(): String {
            if (operators.isEmpty())
                return expressions[0].toString()

            return expressions.zip(operators).joinToString(" ", postfix = " ") { (expr, op) -> "$expr $op" } + expressions.last()
        }
    }

    // Prefix Expressions

    class PrefixOperatorExpression (
        val prefix: Token.OperatorSymbol,
        val expression: Expression
    ) : RValueExpression {
        override fun toString(): String = "$prefix$expression"
    }

    // Postfix Expressions

    class MemberAccess (
        val expression: Expression,
        val member: String
    ) : LValueExpression, RValueExpression {
        override fun toString(): String = "$expression.$member"
    }

    class Cast (
        val expression: Expression,
        val type: TypeExpr
    ) : RValueExpression {
        override fun toString(): String = "$expression as $type"
    }

    class FunctionCall (
        val expression: Expression,
        val arguments: List<Expression>?
    ) : RValueExpression {
        override fun toString(): String = "$expression${arguments?.joinToString(", ", "(", ")") ?: "()"}"
    }

    class IndexAccess (
        val expression: Expression,
        val arguments: List<Expression>
    ) : LValueExpression, RValueExpression {
        override fun toString(): String =
            if (arguments.isEmpty()) error("Expected at least one argument. Got none")
            else "$expression${arguments.joinToString(", ", "[", "]")}"
    }

    // ==========================================
    // Statements
    // ==========================================

    sealed interface Statement : ASTNode, CodeBlock

    class Assignment (
        val lvalue: LValueExpression,
        val expression: RValueExpression
    ) : Statement {
        override fun toString(): String = "$lvalue = $expression"
    }

    class Return (
        val value: Expression?
    ) : Statement {
        override fun toString(): String = if (value == null) "return" else "return $value"
    }

    class Break (
        val value: Expression?
    ) : Statement {
        override fun toString(): String = if (value == null) "break" else "break $value"
    }

    object Continue : Statement {
        override fun toString(): String = "continue"
    }

    class Match (
        val what: RValueExpression,
        val cases: List<MatchCase>
    ) : Statement, RValueExpression {
        override fun toString(): String = "match $what { ${cases.joinToString("\n")} }"
    }

    class MatchCase (
        val pattern: MatchCasePattern,
        val block: CodeBlock
    ) : ASTNode {
        override fun toString(): String = "$pattern => $block"
    }

    sealed class MatchCasePattern(val whens: List<Expression>?) : ASTNode
    class ExpressionPattern (
        val expression: Expression,
        whens: List<Expression>?
    ) : MatchCasePattern(whens) {
        override fun toString(): String = "$expression" + (whens?.joinToString(" ", " ") { "when $it" } ?: "")
    }

    class TypePattern (
        val identifier: String,
        val type: TypeExpr,
        whens: List<Expression>?
    ) : MatchCasePattern(whens) {
        override fun toString(): String = "$identifier : $type" + (whens?.joinToString(" ", " ") { "when $it" } ?: "")
    }

    class DefaultPattern(
        whens: List<Expression>?
    ) : MatchCasePattern(whens) {
        override fun toString(): String = "default" + (whens?.joinToString(" ", " ") { "when $it" } ?: "")
    }

    class If (
        val condition: Expression,
        val block: CodeBlock,
        val elseIfs: List<ElseIf>?,
        val elseBlock: CodeBlock?
    ) : Statement, RValueExpression {
        override fun toString(): String = "if $condition $block" +
                (elseIfs?.joinToString("\n") ?: "") +
                (elseBlock ?: "")
    }

    class ElseIf (
        val condition: Expression,
        val block: CodeBlock
    ) : ASTNode {
        override fun toString(): String = "elif $condition $block"
    }

    class Loop (
        val block: CodeBlock
    ) : Statement {
        override fun toString(): String = "loop $block"
    }

    class While (
        val condition: Expression,
        val block: CodeBlock
    ) : Statement {
        override fun toString(): String = "while $condition $block"
    }

    class DoWhile (
        val condition: Expression,
        val block: CodeBlock
    ) : Statement {
        override fun toString(): String = "do $block while $condition"
    }

    class For (
        val parameters: List<ForParameter>,
        val expression: Expression,
        val block: CodeBlock
    ) : Statement {
        override fun toString(): String = "for ${parameters.joinToString(", ")} in $expression $block"
    }

    class ForParameter (
        val identifier: String,
        val type: TypeExpr?
    ) : ASTNode {
        override fun toString(): String = identifier + (type ?: "")
    }

    // ==========================================
    // Members and parameters
    // ==========================================

    sealed interface MemberDeclaration : ASTNode {
        val decorators: List<Decorator>?
        val modifiers: List<Modifier>?
    }

    sealed interface ExtensionMemberDeclaration : MemberDeclaration
    sealed interface InterfaceMemberDeclaration : MemberDeclaration
    sealed interface DefaultConstructorParameter : ASTNode

    class Parameter(
        val name: String,
        val type: TypeExpr,
        val defaultValue: Expression? = null
    ) : ASTNode, DefaultConstructorParameter {
        override fun toString(): String = "$name: $type" + (defaultValue?.let { " = $it" } ?: "")
    }

    class FunctionDeclaration(
        override val decorators: List<Decorator>? = null,
        override val modifiers: List<Modifier>? = null,
        val name: String,
        val typeParams: List<TypeParam>? = null,
        val parameters: List<Parameter>,
        val returnType: TypeExpr? = null,
        val codeBlock: CodeBlock? = null
    ) : MemberDeclaration, ExtensionMemberDeclaration, InterfaceMemberDeclaration, TopLevelDeclaration {
        override fun toString(): String = buildString {
            append(stringify(decorators, modifiers))
            append("fn $name")
            typeParams?.let { append(it.joinToString(", ", "[", "]")) }
            append(parameters.joinToString(", ", "(", ")"))
            returnType?.let { append(": $it") }
            codeBlock?.let { append(" $it") }
        }
    }

    class ConstructorDeclaration(
        override val decorators: List<Decorator>? = null,
        override val modifiers: List<Modifier>? = null,
        val typeParams: List<TypeParam>? = null,
        val parameters: List<Parameter>? = null,
        val codeBlock: CodeBlock
    ) : MemberDeclaration {
        override fun toString(): String = buildString {
            append(stringify(decorators, modifiers))
            append("init")
            typeParams?.let { append(it.joinToString(", ", "[", "]")) }
            append(parameters?.joinToString(", ", "(", ") ") ?: "()")
            append(codeBlock)
        }
    }

    class OperatorDeclaration(
        override val decorators: List<Decorator>? = null,
        override val modifiers: List<Modifier>? = null,
        val name: Token.OperatorSymbol,
        val typeParams: List<TypeParam>? = null,
        val parameters: List<Parameter>,
        val returnType: TypeExpr? = null,
        val codeBlock: CodeBlock? = null
    ) : MemberDeclaration, InterfaceMemberDeclaration, ExtensionMemberDeclaration, TopLevelDeclaration {
        override fun toString(): String = buildString {
            append(stringify(decorators, modifiers))
            append("operator $name")
            buildFunctionString(this@OperatorDeclaration, this@buildString)
        }
    }

    // ==========================================
    // Variable Declarations
    // ==========================================

    sealed interface VariableDeclaration : Declaration, Statement

    class SingleTypedVariable(
        override val decorators: List<Decorator>? = null,
        override val modifiers: List<Modifier>? = null,
        val name: String,
        val type: TypeExpr,
        val defaultValue: Expression? = null
    ) : VariableDeclaration, DefaultConstructorParameter, MemberDeclaration, ExtensionMemberDeclaration, TopLevelDeclaration {
        override fun toString(): String =
            "${stringify(decorators, modifiers)}let $name: $type" + (defaultValue?.let { " = $it" } ?: "")
    }

    class MultiVariableDeclaration(
        override val decorators: List<Decorator>? = null,
        override val modifiers: List<Modifier>? = null,
        val variables: List<Parameter>
    ) : VariableDeclaration, MemberDeclaration, ExtensionMemberDeclaration, TopLevelDeclaration {
        override fun toString(): String = "let ${variables.joinToString(", ")}"
    }

    class MixedVariableDeclarations(
        val declarations: List<VariableDeclaration>
    ) : VariableDeclaration {
        override fun toString(): String = "let " + declarations.joinToString {
            when (it) {
                is SingleTypedVariable -> "${it.name}: ${it.type}" + if (it.defaultValue == null) "" else " = ${it.defaultValue}"
                is SingleUntypedVariable -> it.name + if (it.defaultValue == null) "" else " = ${it.defaultValue}"
                is UnpackingVariableDeclaration -> "${
                    it.variables.joinToString(
                        ", ",
                        "(",
                        ")"
                    ) { (n, t) -> (n + (t?.let { type -> ": $type" } ?: "")) }
                } = ${it.expression}"
                else -> error("TODO")
            }
        }
    }

    class SingleUntypedVariable(
        val name: String,
        val defaultValue: Expression? = null
    ) : VariableDeclaration {
        override fun toString(): String = "let $name" + (defaultValue?.let { " = $it" } ?: "")
    }

    class UnpackingVariableDeclaration(
        override val decorators: List<Decorator>? = null,
        override val modifiers: List<Modifier>? = null,
        val variables: List<Pair<String, TypeExpr?>>,
        val expression: Expression
    ) : VariableDeclaration, MemberDeclaration, ExtensionMemberDeclaration, TopLevelDeclaration {
        override fun toString(): String = "let ${variables.joinToString(", ", "(", ")") { (n, t) -> (n +( t?.let { ": $it" } ?: "")) } } = $expression"
    }

    // ==========================================
    // Top-Level declarations
    // ==========================================

    sealed interface Declaration : ASTNode
    sealed interface TopLevelDeclaration : Declaration
    sealed interface TypeDeclaration : TopLevelDeclaration, Declaration, MemberDeclaration, ExtensionMemberDeclaration, InterfaceMemberDeclaration
    sealed interface CodeBlock : ASTNode

    class ExpressionBlock (
        val statements: List<Statement>?,
        val expression: Expression
    ) : CodeBlock {
        override fun toString(): String = (statements?.joinToString("\n", "{") ?: "{") + "$expression}"
    }

    class PlainBlock (
        val statements: List<Statement>?
    ) : CodeBlock {
        override fun toString(): String = statements?.joinToString("\n", "{", "}") ?: "{}"
    }

    class TypeDef(val identifier: String, val type: TypeExpr) : TopLevelDeclaration {
        override fun toString(): String = "type $identifier = $type"
    }

    class ClassDeclaration(
        override val decorators: List<Decorator>? = null,
        override val modifiers: List<Modifier>? = null,
        val name: String,
        val typeParameters: List<TypeParam>? = null,
        val defaultConstructorParameters: List<DefaultConstructorParameter>? = null,
        val extensions: List<TypeExpr>? = null,
        val members: List<MemberDeclaration>? = null
    ) : TypeDeclaration, MemberDeclaration, InterfaceMemberDeclaration, ExtensionMemberDeclaration {
        override fun toString(): String = buildString {
            append(stringify(decorators, modifiers))
            append("class $name")
            typeParameters?.let { append(it.joinToString(", ", "[", "]")) }
            defaultConstructorParameters?.let { append(it.joinToString(", ", "(", ")")) }
            extensions?.let { append(it.joinToString(", ", " : ")) }
            members?.let { append(it.joinToString(" ", " {", "}")) }
        }
    }

    class InterfaceDeclaration(
        override val decorators: List<Decorator>? = null,
        override val modifiers: List<Modifier>? = null,
        val name: String,
        val typeParameters: List<TypeParam>? = null,
        val extensions: List<TypeExpr>? = null,
        val members: List<InterfaceMemberDeclaration>? = null
    ) : TypeDeclaration, MemberDeclaration, InterfaceMemberDeclaration, ExtensionMemberDeclaration {
        override fun toString(): String = buildString {
            append(stringify(decorators, modifiers))
            append("interface $name")
            typeParameters?.let { append(it.joinToString(", ", "[", "]")) }
            extensions?.let { append(it.joinToString(", ", " : ")) }
            members?.let { append(it.joinToString(" ", " {", "}")) }
        }
    }

    class ExtensionDeclaration(
        override val decorators: List<Decorator>? = null,
        override val modifiers: List<Modifier>? = null,
        val type: TypeExpr,
        val members: List<ExtensionMemberDeclaration>? = null
    ) : TypeDeclaration, MemberDeclaration, InterfaceMemberDeclaration, ExtensionMemberDeclaration {
        override fun toString(): String =
            "${stringify(decorators, modifiers)}: $type { ${members?.joinToString("\n") ?: ""} }"
    }

    class DecoratorDeclaration(
        override val decorators: List<Decorator>? = null,
        override val modifiers: List<Modifier>? = null,
        val name: String,
        val parameters: List<Parameter>? = null
    ) : TypeDeclaration, MemberDeclaration, InterfaceMemberDeclaration, ExtensionMemberDeclaration {
        override fun toString(): String =
            "${stringify(decorators, modifiers)}@class $name${parameters?.joinToString(", ", "(", ")") ?: ""}"
    }
}

private fun buildFunctionString(
    operator: ASTNode.OperatorDeclaration,
    builder: StringBuilder
) {
    operator.typeParams?.let { builder.append(it.joinToString(", ", "[", "]")) }
    builder.append(operator.parameters.joinToString(", ", "(", ")"))
    operator.returnType?.let { builder.append(": $it") }
    operator.codeBlock?.let { builder.append(" $it") }
}

// ==========================================
// Helpers
// ==========================================

private fun stringify(decorators: List<ASTNode.Decorator>?, modifiers: List<ASTNode.Modifier>?): String = buildString {
    decorators?.forEach { append("$it ") }
    modifiers?.forEach { append("$it ") }
}
