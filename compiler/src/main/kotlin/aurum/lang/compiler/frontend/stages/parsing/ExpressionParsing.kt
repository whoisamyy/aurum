package aurum.lang.compiler.frontend.stages.parsing

internal fun Parser.parseExpression(): ASTNode.Expression {
    skipInsignificant()
    var expr = parsePrimaryExpression()
    while (true) {
        skipInsignificant(expr)
        if (matchExpressionSeparator(expr)) return expr
        if (!hasValidPostfix(expr)) break
        if (matchExpressionSeparator(expr)) return expr
        expr = parsePostfixExpression(expr)
    }
    return expr
}

internal fun Parser.hasPrimaryExpression(): Boolean {
    skipInsignificant()
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

internal fun Parser.parsePrimaryExpression(): ASTNode.Expression {
    skipInsignificant()
    return when (val token = peek()) {
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
            if (token.value == "[]") {
                ASTNode.ArrayExpression(null)
            } else if (hasPrimaryExpression()) {
                ASTNode.PrefixOperatorExpression(token, parsePrimaryExpression())
            } else if (token.value == "()") {
                if (check<Token.DoubleArrow>() || check<Token.LCurlyBrace>()) {
                    return ASTNode.LambdaExpression(null, parseCodeBlock() ?: error("Code block expected"))
                }

                ASTNode.TupleExpression(null)
            }
            else {
                error("Error at ${token.positionString}: unexpected token $token")
            }
        }
        is Token.If -> parseStatement() as ASTNode.If
        is Token.Match -> parseStatement() as ASTNode.Match
        else -> error("Error at ${token.positionString}: unexpected token $token")
    }
}

internal fun Parser.parseGroupedOrLambdaExpression(): ASTNode.Expression = inDelimited {
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
                if (peekNext() is Token.LCurlyBrace) advance()
            }
            val codeBlock = parseCodeBlock() ?: error("Expected code block.")
            return@inDelimited ASTNode.LambdaExpression(lambdaParams, codeBlock)
        } catch (_: Throwable) {
            current = checkpoint
        }

        try {
            val pairs = mutableListOf<Pair<String, ASTNode.Expression>>()
            do {
                val name = consume<Token.Identifier>().value
                val expr = if (tryConsume<Token.Colon>()) {
                    parseExpression()
                } else {
                    error("Expected expression after `:` in named tuple. Got `${peek().value}`")
                }
                pairs += name to expr
            } while (tryConsume<Token.Comma>())

            return@inDelimited ASTNode.NamedTupleExpression(pairs)
        } catch (_: Throwable) {
            current = checkpoint
        }
    }

    if (tryConsume<Token.RParen>()) {
        check<Token.DoubleArrow>()
        val codeBlock = parseCodeBlock() ?: error("Expected code block. ${peek().positionString}")
        return@inDelimited ASTNode.LambdaExpression(null, codeBlock)
    }

    val expressions = mutableListOf<ASTNode.Expression>()
    do {
        expressions += parseExpression()
    } while (tryConsume<Token.Comma>())
    consume<Token.RParen>()

    if (expressions.size == 1) ASTNode.ParenthesizedExpression(expressions.first())
    else ASTNode.TupleExpression(expressions)
}

internal fun Parser.parseBracketExpression(): ASTNode.Expression = inDelimited {
    if (tryConsume<Token.Colon>()) {
        consume<Token.RBracket>()
        return@inDelimited ASTNode.MapExpression(null)
    }
    if (tryConsume<Token.RBracket>()) {
        return@inDelimited ASTNode.ArrayExpression(null)
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
        return@inDelimited ASTNode.MapExpression(pairs)
    }

    val exprs = mutableListOf(expr)
    while (tryConsume<Token.Comma>()) {
        exprs += parseExpression()
    }
    consume<Token.RBracket>()
    ASTNode.ArrayExpression(exprs)
}

internal fun Parser.hasValidPostfix(expr: ASTNode.Expression): Boolean =
    !isExpressionSeparator(expr) && when {
        check<Token.Dot>() || check<Token.As>() || check<Token.OperatorSymbol>() -> true
        check<Token.LParen>() || check<Token.LBracket>() ->
            exprAcceptsPostfixCall(expr) && allowBrokenLinePostfixCall
        else -> false
    }

internal fun Parser.parsePostfixExpression(expr: ASTNode.Expression): ASTNode.Expression =
    when {
        tryConsume<Token.Dot>() -> {
            ASTNode.MemberAccess(expr, consume<Token.Identifier>().value)
        }
        tryConsume<Token.As>() || (check<Token.OperatorSymbol>() && peek().value == "as") -> {
            if (check<Token.OperatorSymbol>() && previous() !is Token.As) consume<Token.OperatorSymbol>()
            ASTNode.Cast(expr, parseTypeExpr())
        }
        tryConsume<Token.LParen>() -> inDelimited {
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
        tryConsume<Token.LBracket>() -> inDelimited {
            val args = mutableListOf<ASTNode.Expression>()
            do { args += parseExpression() } while (tryConsume<Token.Comma>())
            consume<Token.RBracket>()
            ASTNode.IndexAccess(
                expr,
                args.ifEmpty { error("Error at ${peek().positionString}: unexpected token ${peek()}") }
            )
        }
        check<Token.OperatorSymbol>() -> {
            val operators = mutableListOf<Token.OperatorSymbol>()
            var expressions = mutableListOf(expr)

            while (check<Token.OperatorSymbol>()) {
                val operator = consume<Token.OperatorSymbol>()
                operators += operator
                expressions += if (operator.value !in listOf("as", "is")) {
                    val allowSaved = allowBrokenLinePostfixCall
                    allowBrokenLinePostfixCall = false
                    try {
                        parseExpression()
                    } finally {
                        allowBrokenLinePostfixCall = allowSaved
                    }
                } else {
                    parseTypeExpr()
                }
            }
            expressions = expressions.flatMap {
                if (it is ASTNode.BinaryExpression) {
                    operators += it.operators
                    it.expressions
                } else listOf(it)
            }.toMutableList()
            ASTNode.BinaryExpression(expressions, operators)
        }
        else -> expr
    }

internal fun parseNumber(value: String): Number =
    when {
        value.startsWith("0x") -> if (value.endsWith('l', true)) value.drop(2).dropLast(1).toLong(16) else value.drop(2).toInt(16)
        value.startsWith("0b") -> if (value.endsWith('l', true)) value.drop(2).dropLast(1).toLong(2) else value.drop(2).toInt(2)
        value.contains(".")-> if (value.endsWith('f', true)) value.dropLast(1).toFloat() else value.toDouble()
        value.endsWith('f', true) -> value.dropLast(1).toFloat()
        value.endsWith('d', true) -> value.dropLast(1).toDouble()
        value.endsWith('l', true) -> value.dropLast(1).toLong()
        value.matches(Regex("0\\d+[lL]?")) -> if (value.endsWith('l', true)) value.drop(1).dropLast(1).toLong(8) else value.drop(1).toInt(8)
        else -> value.toInt()
    }
