package lang.aurum.parsing.stages.coderesolution

import lang.aurum.model.Method
import lang.aurum.model.PrimitiveType
import lang.aurum.model.Type
import lang.aurum.model.impl.TypeArgumentImpl
import lang.aurum.parsing.antlr.AurumParser

class StatementProcessor (
    val compiler: IRCompiler,
    val method: Method
) {
    val generator = compiler.generator
    val constantPool = compiler.constantPool

    fun processStatement(stmt: AurumParser.StatementContext) {
        when (stmt) {
            is AurumParser.DeclarationStmtContext -> processDeclaration(stmt.declaration())
            is AurumParser.AssignmentExpressionStmtContext -> processAssignmentExpression(stmt.assignmentExpression())
            is AurumParser.ReturnStatementStmtContext -> processReturnStatement(stmt.returnStatement())
            is AurumParser.MatchStatementStmtContext -> processMatchStatement(stmt.matchStatement())
            is AurumParser.IfStatementStmtContext -> processIfStatement(stmt.ifStatement())
            is AurumParser.LoopStatementStmtContext -> processLoopStatement(stmt.loopStatement())
            is AurumParser.WhileStatementStmtContext -> processWhileStatement(stmt.whileStatement())
            is AurumParser.ForStatementStmtContext -> processForStatement(stmt.forStatement())
            is AurumParser.ExpressionStmtContext -> processExpression(stmt.expression())
            is AurumParser.BreakStatementStmtContext -> processBreakStatement(stmt.breakStatement())
            is AurumParser.ContinueStatementStmtContext -> processContinueStatement(stmt.continueStatement())
        }
    }

    fun processDeclaration(ctx: AurumParser.DeclarationContext) {}
    fun processAssignmentExpression(ctx: AurumParser.AssignmentExpressionContext) {
        TODO()
    }
    fun processReturnStatement(ctx: AurumParser.ReturnStatementContext) {
        generator.return_(ctx.expression()?.let { compiler.expressionProcessor.processExpression(it) }?.value)
    }
    fun processMatchStatement(ctx: AurumParser.MatchStatementContext) {
        compiler.expressionProcessor.processMatchStatement(ctx)
    }
    fun processIfStatement(ctx: AurumParser.IfStatementContext) {}
    fun processLoopStatement(ctx: AurumParser.LoopStatementContext) {
        compiler.expressionProcessor.processLoopStatement(ctx)
    }
    fun processWhileStatement(ctx: AurumParser.WhileStatementContext) {
        compiler.expressionProcessor.processWhileStatement(ctx)
    }
    fun processForStatement(ctx: AurumParser.ForStatementContext) {
        val exprValue = compiler.expressionProcessor.processExpression(ctx.expression())

        val type = exprValue.type
        if (!type.isSubclassOf(Type.ofClass(Iterable::class.java)))
            throw IllegalStateException("todo")

        val iteratorMethod = type.findMethod(
            "iterator",
            Type.ofClass(Iterator::class.java)
        ).get()

        val iteratorType = iteratorMethod.returnType()
        val varType = iteratorType
            .typeArguments()
            .orElse(arrayOf(TypeArgumentImpl("T", Type.ofClass(Object::class.java))))[0].bound()
        val varName = ctx.varId(0).Identifier().text

        val iterator = Variable("iter@for$${ctx.positionString}", iteratorType)
        generator.callVirtual(
            iterator.toTarget(),
            exprValue.value,
            constantPool.getReference(iteratorMethod)
        )

        val nextMethod = iteratorType.findMethod("next").get()
        val hasNextMethod = iteratorType.findMethod("hasNext").get()

        val scope = Scope("for$${ctx.positionString}", compiler.currentScope)
        compiler.startScope(scope)
        val cond0 = Variable("cond@for$${ctx.positionString}_0", PrimitiveType.BOOLEAN)
        val cond1 = Variable("cond@for$${ctx.positionString}_1", PrimitiveType.BOOLEAN)
        generator.callVirtual(
            cond0.toTarget(),
            iterator.toReference(),
            constantPool.getReference(hasNextMethod)
        )
        generator.neg(cond1.toTarget(), cond0.toReference())

        generator.jumpIf(cond1.toReference(), scope.endLabel)

        val iter = Variable(varName, varType)
        generator.callVirtual(
            iter.toTarget(),
            iterator.toReference(),
            constantPool.getReference(nextMethod)
        )
        compiler.currentScope += iter

        compiler.process(ctx.block())

        generator.jump(scope.startLabel)

        compiler.endScope()
    }
    fun processExpression(ctx: AurumParser.ExpressionContext) {
        compiler.expressionProcessor.processExpression(ctx)
    }
    fun processBreakStatement(ctx: AurumParser.BreakStatementContext) {
        generator.jump(compiler.currentScope.endLabel) // todo: maybe add expression handling here idk
    }
    fun processContinueStatement(ctx: AurumParser.ContinueStatementContext) {
        generator.jump(compiler.currentScope.startLabel)
    }
}