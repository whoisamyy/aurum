package aurum.lang.compiler.frontend.stages.parsing

internal fun exprAcceptsPostfixCall(expr: ASTNode.Expression): Boolean = when (expr) {
    is ASTNode.IdentifierExpression,
    is ASTNode.MemberAccess,
    is ASTNode.FunctionCall,
    is ASTNode.IndexAccess,
    is ASTNode.ParenthesizedExpression,
    is ASTNode.Cast,
    is ASTNode.ArrayExpression,
    is ASTNode.MapExpression -> true
    else -> false
}

internal fun Parser.skipInsignificant(expr: ASTNode.Expression? = null) {
    while (true) {
        when {
            check<Token.Comment>() -> advance()
            check<Token.EndLine>() && continuesExpressionOnNextLine(expr) -> advance()
            else -> break
        }
    }
}

internal fun Parser.skipStatementSeparators() {
    while (check<Token.Semicolon>() || check<Token.EndLine>()) advance()
}

internal fun Parser.continuesExpressionOnNextLine(expr: ASTNode.Expression? = null): Boolean {
    if (!check<Token.EndLine>()) return false

    var index = current + 1
    while (index < tokens.size && (tokens[index] is Token.EndLine || tokens[index] is Token.Comment)) {
        index++
    }
    if (index >= tokens.size) return false

    if (current > 0)
        when (previous()) {
            is Token.Return, is Token.Break -> return true
            else -> Unit
        }

    return when (tokens[index]) {
        is Token.Dot,
        is Token.Colon,
        is Token.Equal,
        is Token.As,
        is Token.OperatorSymbol -> true
        is Token.LParen, is Token.LBracket -> when (previous()) {
            is Token.OperatorSymbol -> !previous().value.endsWith("=")
            else -> expr != null && exprAcceptsPostfixCall(expr) && allowBrokenLinePostfixCall
        }
        else -> false
    }
}

internal fun Parser.isExpressionSeparator(expr: ASTNode.Expression? = null): Boolean =
    check<Token.Semicolon>() ||
            (check<Token.EndLine>() && openDelimiters == 0 && !continuesExpressionOnNextLine(expr))

internal fun Parser.matchExpressionSeparator(expr: ASTNode.Expression? = null): Boolean {
    if (tryConsume<Token.Semicolon>()) return true
    if (check<Token.EndLine>() && openDelimiters == 0 && !continuesExpressionOnNextLine(expr)) {
        advance()
        return true
    }
    return false
}
