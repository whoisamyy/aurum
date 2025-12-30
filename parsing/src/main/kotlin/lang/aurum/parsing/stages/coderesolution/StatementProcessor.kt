package lang.aurum.parsing.stages.coderesolution

import lang.aurum.ir.*
import lang.aurum.ir.Target
import lang.aurum.model.*
import lang.aurum.model.impl.TypeArgumentImpl
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.aurumError
import lang.aurum.parsing.throwAurumError
import kotlin.jvm.optionals.getOrNull

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

    fun processDeclaration(ctx: AurumParser.DeclarationContext) {
        when (val c = ctx.getChild(0)) {
            is AurumParser.TypeDefContext -> {
                compiler.currentScope.typeDecls += c.Identifier().text to compiler.toType(c.typeExpr())
            }
            is AurumParser.VarDeclContext -> {
                processVarDecl(c)
            }
            else -> throwAurumError("Unsupported declaration type: ${c.javaClass.simpleName}", ctx, compiler.fileContext)
        }
    }

    private fun processVarDecl(member: AurumParser.VarDeclContext) {
        when (member) {
            is AurumParser.SingleDeclContext -> processVarDecl(member)
            is AurumParser.UnpackDeclContext -> processVarDecl(member)
            is AurumParser.MultiDeclContext -> processVarDecl(member)
        }
    }
    private fun processVarDecl(member: AurumParser.SingleDeclContext) {
//        val accessFlags = member.modifier().toAccessFlags()
        val varName = member.Identifier().text
        val expr = member.expression()?.let {
            compiler.expressionProcessor.processExpression(it)
        }

        val type: Type = member.typeExpr()?.let {
            compiler.toType(it)
        } ?: expr?.type ?: Type.ofClass(Object::class.java)

        val variable = Variable(varName, type)

        generator.move(variable.toTarget(), expr?.value ?: NullRef)
        compiler.currentScope += variable
    }
    private fun processVarDecl(member: AurumParser.UnpackDeclContext) {
        val vars = member.varId().map {
            it.Identifier().text to it.typeExpr()?.let { t -> compiler.toType(t) } }

        val expr = member.expression()?.let {
            compiler.expressionProcessor.processExpression(it)
        }

        vars.forEachIndexed { i, (name, type) ->
            var newType = type
            if (type == null) {
                if (expr == null)
                    throwAurumError("Cannot infer type for unpack declaration: expression is required when type is not specified", member, compiler.fileContext)

                newType = expr.type.findField("component$i").getOrNull()?.type()
                    ?: Type.ofClass(Object::class.java)
            }

            val variable = Variable(name, newType)

            expr?.let {
                val field = it.type.findField("component$i").orElseThrow { aurumError("Type ${it.type.className()} does not have component$i field for unpack declaration", member, compiler.fileContext) }
                val ref = constantPool.getReference(field)

                generator.getField(variable.toTarget(), it.value, ref)
            }

            compiler.currentScope += variable
        }
    }
    private fun processVarDecl(member: AurumParser.MultiDeclContext) {
        member.varIdAssignment().forEach {
            val varId = it.varId()
            val name = varId.Identifier().text
            val expr = it.expression()?.let { e ->
                compiler.expressionProcessor.processExpression(e)
            }
            val type = varId.typeExpr()?.let { compiler.toType(varId.typeExpr()) }
                ?: expr?.type ?: Type.ofClass(Object::class.java)

            val variable = Variable(name, type)
            expr?.let { e ->
                generator.move(variable.toTarget(), e.value)
            }

            compiler.currentScope += variable
        }
    }

    fun processAssignmentExpression(ctx: AurumParser.AssignmentExpressionContext) {
        when (ctx) {
            is AurumParser.VarAssignmentContext -> {
                val (owner, lvalue) = compiler.getLValue(ctx.qualifiedName())
                when (lvalue) {
                    is Target.Empty -> {}
                    is Target -> {
                        val expr = compiler.expressionProcessor.processExpression(ctx.expression()).value
                        generator.move(lvalue, expr)
                    }
                    is FieldRef -> {
                        val expr = compiler.expressionProcessor.processExpression(ctx.expression()).value
                        val field = constantPool.dereference<Field>(lvalue)
                        if (field.isFinal)
                            throwAurumError("Cannot assign to final field '${field.name()}'", ctx, compiler.fileContext)

                        if (field.isStatic)
                            generator.putStatic(lvalue, expr)
                        else
                            generator.putField(
                                if (owner !is NullRef) owner else Reference("this"),
                                lvalue,
                                expr
                            )
                    }
                    is Reference -> {
                        val expr = compiler.expressionProcessor.processExpression(ctx.expression()).value
                        generator.move(
                            compiler.currentScope[lvalue.name]?.toTarget()
                                ?: throwAurumError("Variable '${lvalue.name}' not found in current scope", ctx, compiler.fileContext),
                            expr
                        )
                    }
                }
            }
            is AurumParser.ArrayAssignmentContext -> {
                val arr = compiler.expressionProcessor.processExpression(ctx.expression(0))
                val index = ctx.indexAccessPart()
                val expr = ctx.expression(1)

                var tmpValue = arr
                index.argList().expression().dropLast(1).forEachIndexed { i, expr ->
                    val index = compiler.expressionProcessor.processExpression(expr)
                    val tmpName = "tmp@index@$i$${expr.positionString}"
                    generator.arrayLoad(Target(tmpName), tmpValue.value, index.value)
                    tmpValue = Value((tmpValue.type as ArrayType<*>).componentType(), Reference(tmpName))
                }
                val lastExpr = index.argList().expression().last()
                val i = compiler.expressionProcessor.processExpression(lastExpr)
                compiler.setVariableIndexed(
                    Variable(
                        "array$${lastExpr.positionString}",
                        type = tmpValue.type,
                        value = tmpValue.value,
                    ),
                    i,
                    compiler.expressionProcessor.processExpression(expr)
                )
            }
        }
    }
    fun processReturnStatement(ctx: AurumParser.ReturnStatementContext) {
        generator.return_(ctx.expression()?.let { compiler.expressionProcessor.processExpression(it) }?.value)
    }
    fun processMatchStatement(ctx: AurumParser.MatchStatementContext) {
        compiler.expressionProcessor.processMatchStatement(ctx)
    }
    fun processIfStatement(ctx: AurumParser.IfStatementContext) {
        val elifBlocks = mutableListOf(
            { processExpression(ctx.expression(0)).value } to { compiler.process(ctx.block(0)) },
        )
        elifBlocks.addAll(
            ctx.KWelif()?.mapIndexed { i, _ ->
                { processExpression(ctx.expression(i + 1)).value } to { compiler.process(ctx.block(i + 1)) }
            } ?: listOf()
        )

        val elseBlock = { compiler.process(ctx.block().last()) }

        ifElifElse(
            ctx,
            elifBlocks,
            elseBlock
        )
    }

    fun ifElifElse(
        expr: AurumParser.IfStatementContext,
        elifBlocks: List<Pair<() -> RValue, () -> Unit>> = listOf(),
        elseBlock: (() -> Unit)? = null
    ) {
        val elifScopes = mutableListOf(Scope("if$${expr.KWif().positionString}", compiler.currentScope)) +
                expr.KWelif().map { Scope("elif$${it.positionString}", compiler.currentScope) }
        val elseScope = expr.KWelse()?.let { Scope("else$${it.positionString}", compiler.currentScope) }
        val allScopes = (elifScopes + elseScope).filterNotNull().toList()
        compiler.expressionProcessor.ifElifElse(allScopes, elifBlocks, elseScope, elseBlock)
    }

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
            throwAurumError("Type ${type.className()} is not iterable (does not implement Iterable)", ctx, compiler.fileContext)

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
    fun processExpression(ctx: AurumParser.ExpressionContext): Value {
        return compiler.expressionProcessor.processExpression(ctx)
    }
    fun processBreakStatement(ctx: AurumParser.BreakStatementContext) {
        generator.jump(compiler.currentScope.endLabel) // todo: maybe add expression handling here idk
    }
    fun processContinueStatement(ctx: AurumParser.ContinueStatementContext) {
        generator.jump(compiler.currentScope.startLabel)
    }
}