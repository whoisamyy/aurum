package aurum.lang.compiler.frontend.stages.parsing

internal class MemberParser(
    private val parser: Parser,
    private val metadata: Parser.Metadata
) {
    fun parseDecoratorDeclaration(): ASTNode.DecoratorDeclaration {
        parser.consume<Token.At>()
        parser.consume<Token.Class>()
        return ASTNode.DecoratorDeclaration(
            metadata.decorators,
            metadata.modifiers,
            parser.consume<Token.Identifier>().value,
            parser.parseParameters()
        )
    }

    fun parseExtensionDeclaration(): ASTNode.ExtensionDeclaration {
        if (!parser.tryConsume<Token.Colon>()) parser.consume<Token.Extends>()
        val type = parser.parseTypeExpr()
        val members = mutableListOf<ASTNode.ExtensionMemberDeclaration>()

        parser.consume<Token.LCurlyBrace>()
        while (true) {
            parser.skipStatementSeparators()
            val member = parser.parseMember() ?: break
            if (member is ASTNode.ExtensionMemberDeclaration) members += member
            else error("Error at ${parser.peek().positionString}: unexpected token ${parser.peek()}")
        }
        parser.consume<Token.RCurlyBrace>()

        return ASTNode.ExtensionDeclaration(metadata.decorators, metadata.modifiers, type, members.toList())
    }

    fun parseClassDeclaration(): ASTNode.ClassDeclaration {
        parser.consume<Token.Class>()
        val name = parser.consume<Token.Identifier>().value
        val typeParams = parser.parseTypeParams()
        val defaultConstructorParams = parseDefaultConstructor()

        val extensions = mutableListOf<ASTNode.TypeExpr>()
        if (parser.tryConsume<Token.Colon>() || parser.tryConsume<Token.Extends>()) {
            do { extensions += parser.parseTypeExpr() } while (parser.tryConsume<Token.Comma>())
        }

        parser.consume<Token.LCurlyBrace>()
        val members = mutableListOf<ASTNode.MemberDeclaration>()
        while (true) {
            parser.skipStatementSeparators()
            members += parser.parseMember() ?: break
        }
        parser.consume<Token.RCurlyBrace>()

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
        parser.consume<Token.Interface>()
        val name = parser.consume<Token.Identifier>().value
        val typeParams = parser.parseTypeParams()

        val extensions = mutableListOf<ASTNode.TypeExpr>()
        if (parser.tryConsume<Token.Colon>() || parser.tryConsume<Token.Extends>()) {
            do { extensions += parser.parseTypeExpr() } while (parser.tryConsume<Token.Comma>())
        }

        parser.consume<Token.LCurlyBrace>()
        val members = mutableListOf<ASTNode.InterfaceMemberDeclaration>()
        while (true) {
            parser.skipStatementSeparators()
            members += (parser.parseMember() as ASTNode.InterfaceMemberDeclaration?) ?: break
        }
        parser.consume<Token.RCurlyBrace>()

        return ASTNode.InterfaceDeclaration(
            metadata.decorators,
            metadata.modifiers,
            name,
            typeParams,
            extensions.ifEmpty { null },
            members.ifEmpty { null }
        )
    }

    fun parseDefaultConstructor(): List<ASTNode.DefaultConstructorParameter>? {
        if (!parser.tryConsume<Token.LParen>()) return null
        val params = mutableListOf<ASTNode.DefaultConstructorParameter>()

        do {
            if (parser.check<Token.Let>() || parser.check<Token.Var>()) {
                params += MemberParser(parser, parser.parseMetadata())
                    .parseVariableDeclaration<ASTNode.SingleTypedVariable>()
                continue
            }
            val name = parser.consume<Token.Identifier>().value
            parser.consume<Token.Colon>()
            params += ASTNode.Parameter(
                name,
                parser.parseTypeExpr(),
                if (parser.tryConsume<Token.Equal>()) parser.parseExpression() else null
            )
        } while (parser.tryConsume<Token.Comma>())

        parser.consume<Token.RParen>()
        return params
    }

    fun parseFunction(): ASTNode.FunctionDeclaration {
        parser.consume<Token.Fn>()
        return ASTNode.FunctionDeclaration(
            metadata.decorators,
            metadata.modifiers,
            parser.consume<Token.Identifier>().value,
            parser.parseTypeParams(),
            parser.parseParameters() ?: listOf(),
            if (parser.tryConsume<Token.Colon>()) parser.parseTypeExpr() else null,
            parser.parseCodeBlock()
        )
    }

    fun parseOperatorDeclaration(): ASTNode.OperatorDeclaration {
        parser.consume<Token.Operator>()
        return ASTNode.OperatorDeclaration(
            metadata.decorators,
            metadata.modifiers,
            parser.consume<Token.OperatorSymbol>(),
            parser.parseTypeParams(),
            parser.parseParameters() ?: listOf(),
            if (parser.tryConsume<Token.Colon>()) parser.parseTypeExpr() else null,
            parser.parseCodeBlock()
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> parseVariableDeclaration(): T where T : ASTNode.VariableDeclaration, T : ASTNode.MemberDeclaration {
        if (!parser.tryConsume<Token.Let>()) parser.consume<Token.Var>()
        val vars = mutableListOf<ASTNode.Parameter>()

        do {
            val name = parser.consume<Token.Identifier>().value
            parser.consume<Token.Colon>()
            vars += ASTNode.Parameter(
                name,
                parser.parseTypeExpr(),
                if (parser.tryConsume<Token.Equal>()) parser.parseExpression() else null
            )
        } while (parser.peekNext() is Token.Identifier && parser.tryConsume<Token.Comma>())

        return if (vars.size == 1) {
            ASTNode.SingleTypedVariable(
                metadata.decorators,
                metadata.modifiers,
                vars.first().name,
                vars.first().type,
                vars.first().defaultValue
            ) as T
        } else {
            ASTNode.MultiVariableDeclaration(metadata.decorators, metadata.modifiers, vars) as T
        }
    }

    fun parseConstructor(): ASTNode.ConstructorDeclaration {
        parser.consume<Token.Init>()
        return ASTNode.ConstructorDeclaration(
            metadata.decorators,
            metadata.modifiers,
            parser.parseTypeParams(),
            parser.parseParameters(),
            parser.parseCodeBlock()!!
        )
    }
}
