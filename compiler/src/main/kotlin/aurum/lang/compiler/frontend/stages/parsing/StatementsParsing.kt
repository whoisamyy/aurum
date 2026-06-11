package aurum.lang.compiler.frontend.stages.parsing

internal fun Parser.parseCodeBlock(): ASTNode.CodeBlock? = when {
    tryConsume<Token.DoubleArrow>() -> parseStatement()
    tryConsume<Token.LCurlyBrace>() -> {
        val stmts = mutableListOf<ASTNode.Statement>()
        while (!check<Token.RCurlyBrace>()) {
            skipStatementSeparators()
            if (check<Token.RCurlyBrace>()) break
            stmts += parseStatement()
        }
        consume<Token.RCurlyBrace>()
//        if (stmts.isNotEmpty() && stmts.last() is ASTNode.Expression) {
//            ASTNode.ExpressionBlock(stmts.dropLast(1), stmts.last() as ASTNode.Expression)
        if (stmts.size == 1 && stmts[0] is ASTNode.Expression) {
            stmts[0]
        } else {
            ASTNode.PlainBlock(stmts.ifEmpty { null })
        }
    }
    else -> null
}

internal fun Parser.parseStatement(): ASTNode.Statement {
    skipInsignificant()
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
            skipInsignificant()
            if (!hasPrimaryExpression()) ASTNode.Return(null) else ASTNode.Return(parseExpression())
        }
        is Token.Break -> {
            advance()
            skipInsignificant()
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
                elseIfs += ASTNode.ElseIf(
                    parseExpression(),
                    parseCodeBlock() ?: error("Expected code block.")
                )
            }
            val elseBlock = if (tryConsume<Token.Else>()) {
                parseCodeBlock() ?: error("Expected code block.")
            } else null
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
                parseCodeBlock() ?: error("Expected code block.")
            )
        }
        is Token.Match -> {
            advance()
            val what = parseExpression() as ASTNode.RValueExpression
            val cases = mutableListOf<ASTNode.MatchCase>()
            consume<Token.LCurlyBrace>()
            do {
                cases += ASTNode.MatchCase(
                    parseMatchCasePattern(),
                    parseCodeBlock() ?: error("Expected code block.")
                )
                skipStatementSeparators()
            } while (hasMatchCasePattern())
            consume<Token.RCurlyBrace>()
            ASTNode.Match(what, cases)
        }
        else -> {
            val expr = parseExpression()
            skipInsignificant()
            if (expr is ASTNode.LValueExpression &&
                (tryConsume<Token.Equal>() ||
                        (check<Token.OperatorSymbol>() && consume<Token.OperatorSymbol>().value.endsWith("=")))
            ) {
                ASTNode.Assignment(expr, parseExpression() as ASTNode.RValueExpression)
            } else {
                expr
            }
        }
    }
}

internal fun Parser.hasMatchCasePattern(): Boolean {
    skipInsignificant()
    return peek() is Token.Default || peek() is Token.Identifier || hasPrimaryExpression()
}

internal fun Parser.parseMatchCasePattern(): ASTNode.MatchCasePattern {
    skipStatementSeparators()
    return when (val token = advance()) {
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
            current--
            val expr = parseExpression()
            val whens = mutableListOf<ASTNode.Expression>()
            while (tryConsume<Token.When>()) whens += parseExpression()
            ASTNode.ExpressionPattern(expr, whens)
        }
    }
}