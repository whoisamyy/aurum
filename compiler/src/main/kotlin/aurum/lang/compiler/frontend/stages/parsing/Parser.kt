package aurum.lang.compiler.frontend.stages.parsing

import kotlin.reflect.KClass

class Parser(internal val tokens: List<Token>) {
    internal var current = 0
    internal var openDelimiters = 0
    /** When false, a newline before `(`/`[` cannot start a postfix call/index on the current expression. */
    internal var allowBrokenLinePostfixCall = true

    internal fun isAtEnd(): Boolean = current >= tokens.size

    internal fun peek(): Token = tokens.getOrElse(current) { Token.EOF }

    internal fun previous(): Token = tokens[current - 1]

    internal fun peekNext(): Token? = tokens.getOrNull(current + 1)

    internal fun <T : Token> check(type: KClass<out T>): Boolean =
        !isAtEnd() && peek()::class == type

    internal inline fun <reified T : Token> check(): Boolean =
        !isAtEnd() && peek() is T

    internal fun <T : Token> checkNext(type: KClass<out T>): Boolean =
        peekNext()?.let { it::class == type } ?: false

    internal inline fun <reified T : Token> checkNext(): Boolean =
        peekNext() is T

    internal fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Token> consume(
        type: KClass<out T>,
        errorMessage: String = "Error at ${peek().positionString}: could not parse `${peek().value}` as ${type.simpleName}"
    ): T {
        if (check(type)) return advance() as T
        error(errorMessage)
    }

    internal inline fun <reified T : Token> consume(
        errorMessage: String = "Error at ${peek().positionString}: could not parse `${peek().value}` as ${T::class.simpleName}"
    ): T {
        if (check<T>()) return advance() as T
        error(errorMessage)
    }

    internal fun <T : Token> tryConsume(type: KClass<out T>): Boolean =
        if (check(type)) { advance(); true } else false

    internal inline fun <reified T : Token> tryConsume(): Boolean =
        if (check<T>()) { advance(); true } else false

    internal inline fun <T> inDelimited(block: () -> T): T {
        openDelimiters++
        try {
            return block()
        } finally {
            openDelimiters--
        }
    }

    fun parse(): AST {
        val nodes = mutableListOf<ASTNode>()

        skipInsignificant()
        if (check<Token.Package>()) {
            nodes += parsePackage()
        }

        while (true) {
            skipStatementSeparators()
            if (!check<Token.Import>()) break
            nodes += parseImportStmt()
        }

        while (true) {
            skipInsignificant()
            val decl = parseDeclaration() ?: break
            nodes += decl
        }

        return AST(nodes)
    }

    internal fun parseDeclaration(): ASTNode.TopLevelDeclaration? {
        skipStatementSeparators()
        val metadata = parseMetadata()
        skipStatementSeparators()
        val memberParser = MemberParser(this, metadata)
        return when (peek()) {
            is Token.Class -> memberParser.parseClassDeclaration()
            is Token.Interface -> memberParser.parseInterfaceDeclaration()
            is Token.Extends,
            is Token.Colon -> memberParser.parseExtensionDeclaration()
            is Token.Fn -> memberParser.parseFunction()
            is Token.Operator -> memberParser.parseOperatorDeclaration()
            is Token.At -> memberParser.parseDecoratorDeclaration()
            is Token.Var, is Token.Let -> memberParser.parseVariableDeclaration()
            is Token.Type -> parseTypeDef()
            else -> null
        }
    }

    internal fun parseQualifiedName(): ASTNode.QualifiedName {
        if (!check<Token.Identifier>() && !check<Token.OperatorSymbol>()) {
            error("Could not parse identifier")
        }

        val identifiers = mutableListOf<String>()
        while (check<Token.Identifier>() || check<Token.OperatorSymbol>()) {
            identifiers += advance().value
            if (!tryConsume<Token.Dot>()) break
        }

        return ASTNode.QualifiedName(identifiers)
    }

    internal fun parsePackage(): ASTNode.Package {
        consume<Token.Package>()
        return ASTNode.Package(parseQualifiedName())
    }

    internal fun parseImportStmt(): ASTNode.Import {
        consume<Token.Import>()
        val qualifiedName = parseQualifiedName()
        val alias = if (tryConsume<Token.As>()) consume<Token.Identifier>().value else null
        return ASTNode.Import(qualifiedName, alias)
    }

    internal fun parseModifiers(): List<ASTNode.Modifier> {
        val modifiers = mutableListOf<ASTNode.Modifier>()
        while (true) {
            skipStatementSeparators()
            modifiers += when (peek()) {
                is Token.Final -> { advance(); ASTNode.Modifier.Final }
                is Token.Abstract -> { advance(); ASTNode.Modifier.Abstract }
                is Token.Static -> { advance(); ASTNode.Modifier.Static }
                is Token.Private -> { advance(); ASTNode.Modifier.Private }
                is Token.Public -> { advance(); ASTNode.Modifier.Public }
                else -> break
            }
        }
        return modifiers
    }

    internal data class Metadata(
        val decorators: List<ASTNode.Decorator>?,
        val modifiers: List<ASTNode.Modifier>?
    )

    internal fun parseMetadata(): Metadata {
        val decorators = mutableListOf<ASTNode.Decorator>()

        while (true) {
            skipStatementSeparators()
            if (!check<Token.At>() || checkNext<Token.Class>()) break
            advance()
            val identifier = parseQualifiedName()
            if (!tryConsume<Token.LParen>()) {
                decorators += ASTNode.Decorator(identifier)
            } else if (tryConsume<Token.RParen>()) {
                decorators += ASTNode.Decorator(identifier)
            } else {
                val args = mutableListOf<ASTNode.Expression>()
                do { args += parseExpression() } while (tryConsume<Token.Comma>())
                decorators += ASTNode.Decorator(identifier, args)
                consume<Token.RParen>()
            }
        }

        val modifiers = parseModifiers()
        return Metadata(decorators.ifEmpty { null }, modifiers.ifEmpty { null })
    }

    internal fun parseMember(): ASTNode.MemberDeclaration? {
        skipStatementSeparators()
        val memberParser = MemberParser(this, parseMetadata())
        skipStatementSeparators()
        return when (peek()) {
            is Token.Fn -> memberParser.parseFunction()
            is Token.Colon, is Token.Extends -> memberParser.parseExtensionDeclaration()
            is Token.At -> memberParser.parseDecoratorDeclaration()
            is Token.Operator -> memberParser.parseOperatorDeclaration()
            is Token.Var, is Token.Let -> memberParser.parseVariableDeclaration()
            is Token.Init -> memberParser.parseConstructor()
            else -> null
        }
    }
}
