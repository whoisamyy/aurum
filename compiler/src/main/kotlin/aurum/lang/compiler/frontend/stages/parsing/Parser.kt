package aurum.lang.compiler.frontend.stages.parsing

import aurum.lang.compiler.frontend.stages.parsing.ASTNode.TypeSuffix
import kotlin.reflect.KClass

class Parser(private val tokens: List<Token>) {
    private var current = 0

    internal fun isAtEnd(): Boolean = current >= tokens.size

    internal fun peek(): Token = tokens.getOrElse(current) { Token.EOF }

    internal fun previous(): Token = tokens[current - 1]

    internal fun peekNext(): Token? = tokens.getOrNull(current + 1)

    internal fun <T : Token> check(type: KClass<out T>): Boolean =
        !isAtEnd() && peek()::class == type

    private inline fun <reified T : Token> check(): Boolean =
        !isAtEnd() && peek() is T

    internal fun <T : Token> checkNext(type: KClass<out T>): Boolean =
        peekNext()?.let { it::class == type } ?: false

    private inline fun <reified T : Token> checkNext(): Boolean =
        peekNext() is T

    internal fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Token> consume(
        type: KClass<out T>,
        errorMessage: String = "Error at ${peek().positionString}: could not parse ${peek().value} as ${type.simpleName}"
    ): T {
        if (check(type)) return advance() as T
        error(errorMessage)
    }

    private inline fun <reified T : Token> consume(
        errorMessage: String = "Error at ${peek().positionString}: could not parse ${peek().value} as ${T::class.simpleName}"
    ): T {
        if (check<T>()) return advance() as T
        error(errorMessage)
    }

    internal fun <T : Token> tryConsume(type: KClass<out T>): Boolean =
        if (check(type)) { advance(); true } else false

    private inline fun <reified T : Token> tryConsume(): Boolean =
        if (check<T>()) { advance(); true } else false

    fun parse(): AST {
        val nodes = mutableListOf<ASTNode>()

        if (check<Token.Package>()) {
            nodes += parsePackage()
        }

        while (check<Token.Import>()) {
            nodes += parseImportStmt()
        }

        while (true) {
            val decl = parseDeclaration() ?: break
            nodes += decl
        }

        return AST(nodes)
    }

    internal fun parseDeclaration(): ASTNode.TopLevelDeclaration? {
        val metadata = parseMetadata()
        val memberParser = MemberParser(metadata)
        return when (peek()) {
            is Token.Class -> memberParser.parseClassDeclaration()
            is Token.Interface -> memberParser.parseInterfaceDeclaration()
            is Token.Extends,
            is Token.Colon -> memberParser.parseExtensionDeclaration()
            is Token.Fn -> memberParser.parseFunction()
            is Token.Operator -> memberParser.parseOperatorDeclaration()
            is Token.At -> memberParser.parseDecoratorDeclaration()
            is Token.Var, is Token.Let -> memberParser.parseVariableDeclaration()
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

    internal fun parseExpression(): ASTNode.Expression {
        var expr = parsePrimaryExpression()
        while (hasValidPostfix()) {
            expr = parsePostfixExpression(expr)
        }
        return expr
    }

    internal fun hasPrimaryExpression(): Boolean {
        val peeked = peek()
        return peeked is Token.Identifier ||
                peeked is Token.Null ||
                peeked is Token.String ||
                peeked is Token.Number ||
                peeked is Token.Boolean ||
                peeked is Token.LParen ||
                peeked is Token.LBracket ||
                (peeked is Token.OperatorSymbol && peeked.value == "[]")
    }

    internal fun parsePrimaryExpression(): ASTNode.Expression =
        when (val token = peek()) {
            is Token.Identifier -> {
                advance()
                ASTNode.IdentifierExpression(token.value)
            }
            is Token.Null -> {
                advance()
                ASTNode.Literal.Null
            }
            is Token.String -> {
                advance()
                ASTNode.Literal.String(token.value)
            }
            is Token.Number -> {
                advance()
                ASTNode.Literal.Number(parseNumber(token.value))
            }
            is Token.Boolean -> {
                advance()
                if (token.value == "true") ASTNode.Literal.True else ASTNode.Literal.False
            }
            is Token.LParen -> {
                advance()
                parseGroupedOrLambdaExpression()
            }
            is Token.LBracket -> {
                advance()
                parseBracketExpression()
            }
            is Token.OperatorSymbol -> {
                advance()
                if (token.value == "[]") ASTNode.ArrayExpression(null) else error("Error at ${token.positionString}: unexpected token $token")
            }
            is Token.If -> {
                parseStatement() as ASTNode.If
            }
            is Token.Match -> parseStatement() as ASTNode.Match
            else -> error("Error at ${token.positionString}: unexpected token $token")
        }

    internal fun parseGroupedOrLambdaExpression(): ASTNode.Expression {
        if (check<Token.Identifier>()) {
            val checkpoint = current
            try {
                val lambdaParams = mutableListOf<ASTNode.LambdaParameter>()
                do {
                    val name = consume<Token.Identifier>().value
                    val type = if (tryConsume<Token.Colon>()) parseTypeExpr() else null
                    lambdaParams += ASTNode.LambdaParameter(name, type)
                } while (tryConsume<Token.Comma>())

                consume<Token.RParen>()
                if (check<Token.DoubleArrow>()) {
                    if (peekNext() is Token.LCurlyBrace)
                        advance()
                }
                val codeBlock = parseCodeBlock() ?: error("Expected code block.")
                return ASTNode.LambdaExpression(lambdaParams, codeBlock)
            } catch (e: Throwable) {
                current = checkpoint
            }

            try {
                val pairs = mutableListOf<Pair<String, ASTNode.Expression>>()
                do {
                    val name = consume<Token.Identifier>().value
                    val expr = if (tryConsume<Token.Colon>()) parseExpression() else error("Expected expression after `:` in named tuple. Got `${peek().value}`")
                    pairs += name to expr
                } while (tryConsume<Token.Comma>())

                return ASTNode.NamedTupleExpression(pairs)
            } catch (e: Throwable) {
                current = checkpoint
            }
        }

        if (tryConsume<Token.RParen>()) {
            consume<Token.DoubleArrow>()
            val codeBlock = parseCodeBlock() ?: error("Expected code block.")
            return ASTNode.LambdaExpression(null, codeBlock)
        }

        val expressions = mutableListOf<ASTNode.Expression>()
        do {
            expressions += parseExpression()
        } while (tryConsume<Token.Comma>())
        consume<Token.RParen>()

        return if (expressions.size == 1) ASTNode.ParenthesizedExpression(expressions.first())
        else ASTNode.TupleExpression(expressions)
    }

    internal fun parseBracketExpression(): ASTNode.Expression {
        if (tryConsume<Token.Colon>()) {
            consume<Token.RBracket>()
            return ASTNode.MapExpression(null)
        }
        if (tryConsume<Token.RBracket>()) {
            return ASTNode.ArrayExpression(null)
        }

        val expr = parseExpression()
        if (tryConsume<Token.Colon>()) {
            val pairs = mutableListOf(expr to parseExpression())
            while (tryConsume<Token.Comma>()) {
                val e1 = parseExpression()
                consume<Token.Colon>()
                pairs += e1 to parseExpression()
            }
            consume<Token.RBracket>()
            return ASTNode.MapExpression(pairs)
        }

        val exprs = mutableListOf(expr)
        while (tryConsume<Token.Comma>()) {
            exprs += parseExpression()
        }
        consume<Token.RBracket>()

        return ASTNode.ArrayExpression(exprs)
    }

    internal fun hasValidPostfix(): Boolean =
        check<Token.Dot>() || check<Token.As>() || check<Token.OperatorSymbol>() ||
                check<Token.LParen>() || check<Token.LBracket>()

    internal fun parsePostfixExpression(expr: ASTNode.Expression): ASTNode.Expression =
        when {
            tryConsume<Token.Dot>() -> {
                ASTNode.MemberAccess(expr, consume<Token.Identifier>().value)
            }
            tryConsume<Token.As>() || (check<Token.OperatorSymbol>() && peek().value == "as") -> {
                if (check<Token.OperatorSymbol>() && previous() !is Token.As) consume<Token.OperatorSymbol>()
                ASTNode.Cast(expr, parseTypeExpr())
            }
            tryConsume<Token.LParen>() -> {
                if (tryConsume<Token.RParen>()) {
                    ASTNode.FunctionCall(expr, null)
                } else {
                    val args = mutableListOf<ASTNode.Expression>()
                    do { args += parseExpression() } while (tryConsume<Token.Comma>())
                    consume<Token.RParen>()
                    ASTNode.FunctionCall(expr, args.ifEmpty { null })
                }
            }
            check<Token.OperatorSymbol>() && peek().value == "()" -> {
                consume<Token.OperatorSymbol>()
                ASTNode.FunctionCall(expr, null)
            }
            tryConsume<Token.LBracket>() -> {
                val args = mutableListOf<ASTNode.Expression>()
                do { args += parseExpression() } while (tryConsume<Token.Comma>())
                consume<Token.RBracket>()
                ASTNode.IndexAccess(expr, args.ifEmpty { error("Error at ${peek().positionString}: unexpected token ${peek()}") })
            }
            check<Token.OperatorSymbol>() -> {
                val operators = mutableListOf<Token.OperatorSymbol>()
                val expressions = mutableListOf(expr)

                while (check<Token.OperatorSymbol>()) {
                    val operator = consume<Token.OperatorSymbol>()
                    operators += operator
                    expressions += if (operator.value !in listOf("as", "is")) parseExpression() else parseTypeExpr()
                }
                ASTNode.BinaryExpression(expressions, operators)
            }
            else -> expr
        }

    internal fun parseNumber(value: String): Number =
        when {
            value.startsWith("0x") -> if (value.endsWith('l', true)) value.toLong(16) else value.toInt(16)
            value.startsWith("0b") -> if (value.endsWith('l', true)) value.toLong(2) else value.toInt(2)
            value.contains(".") -> if (value.endsWith('f', true)) value.toFloat() else value.toDouble()
            value.endsWith('f', true) -> value.toFloat()
            value.endsWith('d', true) -> value.toDouble()
            value.endsWith('l', true) -> value.toLong()
            value.matches(Regex("0\\d+[lL]?")) -> if (value.endsWith('l', true)) value.toLong(8) else value.toInt(8)
            else -> value.toInt()
        }

    internal fun parseTypeExpr(): ASTNode.TypeExpr {
        if (check<Token.LParen>()) {
            val mark = current
            try {
                val params = parseParameterTypeList()
                if (tryConsume<Token.Arrow>()) {
                    return ASTNode.LambdaType(params, parseTypeExpr())
                }
                current = mark
            } catch (e: Exception) {
                current = mark
            }
        }

        val node = parseUnionType()
        if (tryConsume<Token.Arrow>()) {
            return ASTNode.LambdaType(listOf(node), parseTypeExpr())
        }
        return node
    }

    internal fun parseParameterTypeList(): List<ASTNode.TypeExpr> {
        consume<Token.LParen>("Expect '('")
        val list = mutableListOf<ASTNode.TypeExpr>()
        if (!check<Token.RParen>()) {
            do { list.add(parseTypeExpr()) } while (tryConsume<Token.Comma>())
        }
        consume<Token.RParen>("Expect ')'")
        return list
    }

    internal fun parseUnionType(): ASTNode.TypeExpr {
        var node = parseIntersectionType()
        if (check<Token.Bar>()) {
            val types = mutableListOf(node)
            while (tryConsume<Token.Bar>()) {
                types.add(parseIntersectionType())
            }
            node = ASTNode.UnionType(types)
        }
        return node
    }

    internal fun parseIntersectionType(): ASTNode.TypeExpr {
        var node = parseBaseTypeWithSuffix()
        if (check<Token.Ampersand>()) {
            val types = mutableListOf(node)
            while (tryConsume<Token.Ampersand>()) {
                types.add(parseBaseTypeWithSuffix())
            }
            node = ASTNode.IntersectionType(types)
        }
        return node
    }

    internal fun parseBaseTypeWithSuffix(): ASTNode.TypeExpr {
        val node = parsePrimaryType()
        val suffixes = mutableListOf<TypeSuffix>()

        while (true) {
            when {
                tryConsume<Token.LBracket>() && tryConsume<Token.RBracket>() -> suffixes.add(TypeSuffix.Array)
                check<Token.OperatorSymbol>() && peek().value == "[]" -> {
                    consume<Token.OperatorSymbol>()
                    suffixes.add(TypeSuffix.Array)
                }
                tryConsume<Token.VarargSuffix>() -> suffixes.add(TypeSuffix.Vararg)
                else -> break
            }
        }

        return if (suffixes.isEmpty()) node else when (node) {
            is ASTNode.PlainType -> ASTNode.PlainType(node.name, suffixes)
            is ASTNode.ParametrizedType -> ASTNode.ParametrizedType(node.name, node.typeArgs, suffixes)
            else -> node
        }
    }

    internal fun parsePrimaryType(): ASTNode.TypeExpr {
        if (tryConsume<Token.LParen>()) {
            val type = parseTypeExpr()
            consume<Token.RParen>("Expect ')' after type.")
            return ASTNode.ParenthesizedType(type)
        }

        val qName = parseQualifiedName()
        if (tryConsume<Token.LBracket>()) {
            val args = parseTypeArgs()
            consume<Token.RBracket>("Expect ']' after type arguments.")
            return ASTNode.ParametrizedType(qName, args)
        }

        return ASTNode.PlainType(qName)
    }

    internal fun parseTypeArgs(): MutableList<ASTNode.TypeArg> {
        val args = mutableListOf<ASTNode.TypeArg>()
        do { args.add(parseTypeArg()) } while (tryConsume<Token.Comma>())
        return args
    }

    internal fun parseTypeArg(): ASTNode.TypeArg {
        if (!tryConsume<Token.QuestionMark>()) return parseTypeExpr()
        if (tryConsume<Token.Extends>() || tryConsume<Token.Colon>()) return ASTNode.TypeArg.Wildcard(parseTypeExpr())
        return ASTNode.TypeArg.Wildcard(null)
    }

    internal fun parseTypeDef(): ASTNode.TypeDef {
        consume<Token.Type>()
        val identifier = consume<Token.Identifier>().value
        consume<Token.Equal>()
        return ASTNode.TypeDef(identifier, parseTypeExpr())
    }

    internal data class Metadata(
        val decorators: List<ASTNode.Decorator>?,
        val modifiers: List<ASTNode.Modifier>?
    )

    internal fun parseMetadata(): Metadata {
        val decorators = mutableListOf<ASTNode.Decorator>()

        while (check<Token.At>() && !checkNext<Token.Class>()) {
            advance()
            val identifier = parseQualifiedName()
//            if (!tryConsume<Token.LParen>() || previous() is Token.RParen) { // previous is checked if tryConsume LParen succeeds but immediately followed by RParen (handled differently below, simplified here)
//                // Adjusted decorator parsing to keep original structure safe
//            }
//            // Better logic:
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

    internal fun parseCodeBlock(): ASTNode.CodeBlock? = when {
        tryConsume<Token.DoubleArrow>() -> parseStatement()
        tryConsume<Token.LCurlyBrace>() -> {
            val stmts = mutableListOf<ASTNode.Statement>()
            while (!tryConsume<Token.RCurlyBrace>()) {
                stmts += parseStatement()
            }
            if (stmts.isNotEmpty() && stmts.last() is ASTNode.Expression) {
                ASTNode.ExpressionBlock(stmts.dropLast(1), stmts.last() as ASTNode.Expression)
            } else {
                ASTNode.PlainBlock(stmts.ifEmpty { null })
            }
        }
        else -> null
    }

    internal fun parseStatement(): ASTNode.Statement {
        return when (peek()) {
            is Token.Let, is Token.Var -> {
                advance()
                if (!tryConsume<Token.LParen>()) {
                    val vars = mutableListOf<ASTNode.VariableDeclaration>()
                    do {
                        val name = consume<Token.Identifier>().value
                        val type = if (tryConsume<Token.Colon>()) parseTypeExpr() else null
                        val value = if (tryConsume<Token.Equal>()) parseExpression() else null

                        vars += if (type == null) ASTNode.SingleUntypedVariable(name, value)
                        else ASTNode.SingleTypedVariable(null, null, name, type, value)
                    } while (tryConsume<Token.Comma>())

                    if (vars.size != 1) ASTNode.MixedVariableDeclarations(vars) else vars.first()
                } else {
                    val pairs = mutableListOf<Pair<String, ASTNode.TypeExpr?>>()
                    do {
                        val name = consume<Token.Identifier>().value
                        val type = if (tryConsume<Token.Colon>()) parseTypeExpr() else null
                        pairs += name to type
                    } while (tryConsume<Token.Comma>())

                    consume<Token.RParen>()
                    consume<Token.Equal>()
                    ASTNode.UnpackingVariableDeclaration(
                        null,
                        null,
                        pairs,
                        parseExpression()
                    )
                }
            }
            is Token.Continue -> { advance(); ASTNode.Continue }
            is Token.Loop -> { advance(); ASTNode.Loop(parseCodeBlock() ?: error("Expected code block.")) }
            is Token.Return -> {
                advance()
                if (!hasPrimaryExpression()) ASTNode.Return(null) else ASTNode.Return(parseExpression())
            }
            is Token.Break -> {
                advance()
                if (!hasPrimaryExpression()) ASTNode.Break(null) else ASTNode.Break(parseExpression())
            }
            is Token.While -> {
                advance()
                ASTNode.While(parseExpression(), parseCodeBlock() ?: error("Expected code block."))
            }
            is Token.Do -> {
                advance()
                val codeBlock = parseCodeBlock() ?: error("Expected code block.")
                consume<Token.While>()
                ASTNode.DoWhile(parseExpression(), codeBlock)
            }
            is Token.If -> {
                advance()
                val condition = parseExpression()
                val block = parseCodeBlock() ?: error("Expected code block.")
                val elseIfs = mutableListOf<ASTNode.ElseIf>()
                while (tryConsume<Token.Elif>()) {
                    elseIfs += ASTNode.ElseIf(parseExpression(), parseCodeBlock()
                        ?: error("Expected code block."))
                }
                val elseBlock = if (tryConsume<Token.Else>()) parseCodeBlock() ?: error("Expected code block.") else null
                ASTNode.If(condition, block, elseIfs.ifEmpty { null }, elseBlock)
            }
            is Token.For -> {
                advance()
                val forParameters = mutableListOf<ASTNode.ForParameter>()
                do {
                    val identifier = consume<Token.Identifier>().value
                    val type = if (tryConsume<Token.Colon>()) parseTypeExpr() else null
                    forParameters += ASTNode.ForParameter(identifier, type)
                } while (tryConsume<Token.Comma>())

                consume<Token.In>()
                val collection = parseExpression()
                ASTNode.For(
                    forParameters,
                    collection,
                    parseCodeBlock()
                        ?: error("Expected code block.")
                )
            }
            is Token.Match -> {
                advance()
                val what = parseExpression() as ASTNode.RValueExpression
                val cases = mutableListOf<ASTNode.MatchCase>()
                consume<Token.LCurlyBrace>()
                do {
                    cases += ASTNode.MatchCase(parseMatchCasePattern(), parseCodeBlock()
                        ?: error("Expected code block."))
                } while (hasMatchCasePattern())
                consume<Token.RCurlyBrace>()
                ASTNode.Match(what, cases)
            }
            else -> {
                val expr = parseExpression()
                if (expr is ASTNode.LValueExpression &&
                    (tryConsume<Token.Equal>()|| (check<Token.OperatorSymbol>() && consume<Token.OperatorSymbol>().value.endsWith("=")))) {
                    ASTNode.Assignment(expr, parseExpression() as ASTNode.RValueExpression)
                } else {
                    expr
                }
            }
        }
    }

    internal fun hasMatchCasePattern(): Boolean =
        peek() is Token.Default || peek() is Token.Identifier || hasPrimaryExpression()

    internal fun parseMatchCasePattern(): ASTNode.MatchCasePattern =
        when (val token = advance()) {
            is Token.Default -> {
                val whens = mutableListOf<ASTNode.Expression>()
                while (tryConsume<Token.When>()) whens += parseExpression()
                ASTNode.DefaultPattern(whens.ifEmpty { null })
            }
            is Token.Identifier -> {
                consume<Token.Colon>()
                val type = parseTypeExpr()
                val whens = mutableListOf<ASTNode.Expression>()
                while (tryConsume<Token.When>()) whens += parseExpression()
                ASTNode.TypePattern(token.value, type, whens.ifEmpty { null })
            }
            else -> {
                current-- // Re-evaluate primary expression
                val expr = parseExpression()
                val whens = mutableListOf<ASTNode.Expression>()
                while (tryConsume<Token.When>()) whens += parseExpression()
                ASTNode.ExpressionPattern(expr, whens)
            }
        }

    internal fun parseParameters(): List<ASTNode.Parameter>? {
        if (check<Token.OperatorSymbol>() && peek().value == "()") {
            advance()
            return null
        }

        consume<Token.LParen>()
        val parameters = mutableListOf<ASTNode.Parameter>()

        if (check<Token.Identifier>()) {
            do {
                val name = consume<Token.Identifier>().value
                consume<Token.Colon>()
                val type = parseTypeExpr()
                val defaultValue = if (tryConsume<Token.Equal>()) parseExpression() else null
                parameters += ASTNode.Parameter(name, type, defaultValue)
            } while (tryConsume<Token.Comma>())
        }
        consume<Token.RParen>()
        return parameters.ifEmpty { null }
    }

    internal fun parseTypeParams(): List<ASTNode.TypeParam>? {
        if (!tryConsume<Token.LBracket>()) return null

        val typeParams = mutableListOf<ASTNode.TypeParam>()
        do {
            val name = consume<Token.Identifier>().value
            val bound = if (tryConsume<Token.Extends>() || tryConsume<Token.Colon>()) parseTypeExpr() else null
            typeParams += ASTNode.TypeParam(name, bound)
        } while (tryConsume<Token.Comma>())

        consume<Token.RBracket>()
        return typeParams.ifEmpty { null }
    }

    internal fun parseMember(): ASTNode.MemberDeclaration? {
        val memberParser = MemberParser(parseMetadata())
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

    private inner class MemberParser(val metadata: Metadata) {
        fun parseDecoratorDeclaration(): ASTNode.DecoratorDeclaration {
            consume<Token.At>()
            consume<Token.Class>()
            return ASTNode.DecoratorDeclaration(
                metadata.decorators,
                metadata.modifiers,
                consume<Token.Identifier>().value,
                parseParameters()
            )
        }

        fun parseExtensionDeclaration(): ASTNode.ExtensionDeclaration {
            if (!tryConsume<Token.Colon>()) consume<Token.Extends>()
            val type = parseTypeExpr()
            val members = mutableListOf<ASTNode.ExtensionMemberDeclaration>()

            consume<Token.LCurlyBrace>()
            while (true) {
                val member = parseMember() ?: break
                if (member is ASTNode.ExtensionMemberDeclaration) members += member
                else error("Error at ${peek().positionString}: unexpected token ${peek()}")
            }
            consume<Token.RCurlyBrace>()

            return ASTNode.ExtensionDeclaration(metadata.decorators, metadata.modifiers, type, members.toList())
        }

        fun parseClassDeclaration(): ASTNode.ClassDeclaration {
            consume<Token.Class>()
            val name = consume<Token.Identifier>().value
            val typeParams = parseTypeParams()
            val defaultConstructorParams = parseDefaultConstructor()

            val extensions = mutableListOf<ASTNode.TypeExpr>()
            if (tryConsume<Token.Colon>() || tryConsume<Token.Extends>()) {
                do { extensions += parseTypeExpr() } while (tryConsume<Token.Comma>())
            }

            consume<Token.LCurlyBrace>()
            val members = mutableListOf<ASTNode.MemberDeclaration>()
            while (true) {
                members += parseMember() ?: break
            }
            consume<Token.RCurlyBrace>()

            return ASTNode.ClassDeclaration(
                metadata.decorators,
                metadata.modifiers,
                name,
                typeParams,
                defaultConstructorParams,
                extensions.ifEmpty { null },
                members.ifEmpty { null }
            )
        }

        fun parseInterfaceDeclaration(): ASTNode.InterfaceDeclaration {
            consume<Token.Interface>()
            val name = consume<Token.Identifier>().value
            val typeParams = parseTypeParams()

            val extensions = mutableListOf<ASTNode.TypeExpr>()
            if (tryConsume<Token.Colon>() || tryConsume<Token.Extends>()) {
                do { extensions += parseTypeExpr() } while (tryConsume<Token.Comma>())
            }

            consume<Token.LCurlyBrace>()
            val members = mutableListOf<ASTNode.InterfaceMemberDeclaration>()
            while (true) {
                members += (parseMember() as ASTNode.InterfaceMemberDeclaration?) ?: break
            }
            consume<Token.RCurlyBrace>()

            return ASTNode.InterfaceDeclaration(metadata.decorators, metadata.modifiers, name, typeParams, extensions.ifEmpty { null }, members.ifEmpty { null })
        }

        fun parseDefaultConstructor(): List<ASTNode.DefaultConstructorParameter>? {
            if (!tryConsume<Token.LParen>()) return null
            val params = mutableListOf<ASTNode.DefaultConstructorParameter>()

            do {
                if (check<Token.Let>() || check<Token.Var>()) {
                    params += MemberParser(parseMetadata()).parseVariableDeclaration<ASTNode.SingleTypedVariable>()
                    continue
                }
                val name = consume<Token.Identifier>().value
                consume<Token.Colon>()
                params += ASTNode.Parameter(name, parseTypeExpr(), if (tryConsume<Token.Equal>()) parseExpression() else null)
            } while (tryConsume<Token.Comma>())

            consume<Token.RParen>()
            return params
        }

        fun parseFunction(): ASTNode.FunctionDeclaration {
            consume<Token.Fn>()
            return ASTNode.FunctionDeclaration(
                metadata.decorators, metadata.modifiers, consume<Token.Identifier>().value,
                parseTypeParams(), parseParameters() ?: listOf(),
                if (tryConsume<Token.Colon>()) parseTypeExpr() else null,
                parseCodeBlock()
            )
        }

        fun parseOperatorDeclaration(): ASTNode.OperatorDeclaration {
            consume<Token.Operator>()
            return ASTNode.OperatorDeclaration(
                metadata.decorators, metadata.modifiers, consume<Token.OperatorSymbol>(),
                parseTypeParams(), parseParameters() ?: listOf(),
                if (tryConsume<Token.Colon>()) parseTypeExpr() else null,
                parseCodeBlock()
            )
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> parseVariableDeclaration(): T where T : ASTNode.VariableDeclaration, T : ASTNode.MemberDeclaration {
            if (!tryConsume<Token.Let>()) consume<Token.Var>()
            val vars = mutableListOf<ASTNode.Parameter>()

            do {
                val name = consume<Token.Identifier>().value
                consume<Token.Colon>()
                vars += ASTNode.Parameter(name, parseTypeExpr(), if (tryConsume<Token.Equal>()) parseExpression() else null)
            } while (peekNext() is Token.Identifier && tryConsume<Token.Comma>())

            return if (vars.size == 1) {
                ASTNode.SingleTypedVariable(metadata.decorators, metadata.modifiers, vars.first().name, vars.first().type, vars.first().defaultValue) as T
            } else {
                ASTNode.MultiVariableDeclaration(metadata.decorators, metadata.modifiers, vars) as T
            }
        }

        fun parseConstructor(): ASTNode.ConstructorDeclaration {
            consume<Token.Init>()
            return ASTNode.ConstructorDeclaration(metadata.decorators, metadata.modifiers, parseTypeParams(), parseParameters(), parseCodeBlock()!!)
        }
    }
}