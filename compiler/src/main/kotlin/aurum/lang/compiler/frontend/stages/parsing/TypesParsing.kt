package aurum.lang.compiler.frontend.stages.parsing

import aurum.lang.compiler.frontend.stages.parsing.ASTNode.TypeSuffix

internal fun Parser.parseTypeExpr(): ASTNode.TypeExpr {
    if (check<Token.LParen>()) {
        val mark = current
        try {
            val params = parseParameterTypeList()
            if (tryConsume<Token.Arrow>()) {
                return ASTNode.LambdaType(params, parseTypeExpr())
            }
            current = mark
        } catch (_: Exception) {
            current = mark
        }
    }

    val node = parseUnionType()
    if (tryConsume<Token.Arrow>()) {
        return ASTNode.LambdaType(listOf(node), parseTypeExpr())
    }
    return node
}

internal fun Parser.parseParameterTypeList(): List<ASTNode.TypeExpr> {
    consume<Token.LParen>("Expect '('")
    val list = mutableListOf<ASTNode.TypeExpr>()
    if (!check<Token.RParen>()) {
        do { list.add(parseTypeExpr()) } while (tryConsume<Token.Comma>())
    }
    consume<Token.RParen>("Expect ')'")
    return list
}

internal fun Parser.parseUnionType(): ASTNode.TypeExpr {
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

internal fun Parser.parseIntersectionType(): ASTNode.TypeExpr {
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

internal fun Parser.parseBaseTypeWithSuffix(): ASTNode.TypeExpr {
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

internal fun Parser.parsePrimaryType(): ASTNode.TypeExpr {
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

internal fun Parser.parseTypeArgs(): MutableList<ASTNode.TypeArg> {
    val args = mutableListOf<ASTNode.TypeArg>()
    do { args.add(parseTypeArg()) } while (tryConsume<Token.Comma>())
    return args
}

internal fun Parser.parseTypeArg(): ASTNode.TypeArg {
    if (!tryConsume<Token.QuestionMark>()) return parseTypeExpr()
    if (tryConsume<Token.Extends>() || tryConsume<Token.Colon>()) return ASTNode.TypeArg.Wildcard(parseTypeExpr())
    return ASTNode.TypeArg.Wildcard(null)
}

internal fun Parser.parseTypeDef(): ASTNode.TypeDef {
    consume<Token.Type>()
    val identifier = consume<Token.Identifier>().value
    consume<Token.Equal>()
    return ASTNode.TypeDef(identifier, parseTypeExpr())
}

internal fun Parser.parseParameters(): List<ASTNode.Parameter>? {
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

internal fun Parser.parseTypeParams(): List<ASTNode.TypeParam>? {
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
