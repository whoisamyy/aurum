package lang.aurum.parsing.stages.coderesolution

import lang.aurum.attribute.LambdaMethodAttribute
import lang.aurum.ir.*
import lang.aurum.model.*
import lang.aurum.model.impl.Utils
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.attribute.contains
import lang.aurum.parsing.aurumError
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.throwAurumError
import org.antlr.v4.runtime.tree.TerminalNode
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
        } ?: expr?.type ?: Types.OBJECT

        val variable = Variable(varName, type)

        generator.move(variable.toLValue(), expr?.value ?: NullRef)
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
                    ?: Types.OBJECT
            }

            val variable = Variable(name, newType)

            expr?.let {
                val field = it.type.findField("component$i").orElseThrow { aurumError("Type ${it.type.className()} does not have component$i field for unpack declaration", member, compiler.fileContext) }
                val ref = constantPool.getReference(field)

                generator.getField(variable.toLValue(), it.value, ref)
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
                ?: expr?.type ?: Types.OBJECT

            val variable = Variable(name, type)
            expr?.let { e ->
                generator.move(variable.toLValue(), e.value)
            }

            compiler.currentScope += variable
        }
    }

    fun processAssignmentExpression(ctx: AurumParser.AssignmentExpressionContext) {
        when (ctx) {
            is AurumParser.VarAssignmentContext -> {
                val (owner, lvalue) = compiler.getLValue(ctx.qualifiedName())
                when (lvalue) {
                    is Reference.Empty -> {}
                    is Reference -> {
                        val expr = compiler.expressionProcessor.processExpression(ctx.expression())
                        val target = (compiler.currentScope[lvalue.name]
                            ?: throwAurumError(
                                "Variable '${lvalue.name}' not found in current scope",
                                ctx,
                                compiler.fileContext
                            ))

                        val op = ctx.OperatorSymbol()
                        op?.let {
                            val type = target.type
                            processOperatorAssignment(type, expr, op, target)
                        } ?: generator.move(target.toLValue(), expr.value)
                    }
                    is FieldRef -> {
                        val expr = compiler.expressionProcessor.processExpression(ctx.expression())
                        val field = constantPool.dereference<Field>(lvalue)
                        if (field.isFinal)
                            throwAurumError("Cannot assign to final field '${field.name()}'", ctx, compiler.fileContext)

                        val op = ctx.OperatorSymbol()
                        op?.let {
                            val type = field.type()
                            processOperatorAssignment(type, expr, op, Variable(field.name(), field.type(), lvalue))
                        } ?: if (field.isStatic) generator.move(lvalue, expr.value)
                        else generator.putField(
                            if (owner !is NullRef) owner else Reference.This,
                            lvalue,
                            expr.value
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
                    generator.arrayLoad(Reference.Named(tmpName), tmpValue.value, index.value)
                    tmpValue = Value((tmpValue.type as ArrayType<*>).componentType(), Reference.Named(tmpName))
                }
                val lastExpr = index.argList().expression().last()
                val i = compiler.expressionProcessor.processExpression(lastExpr)
                val value = compiler.expressionProcessor.processExpression(expr)
                val array = Variable(
                    "array$${lastExpr.positionString}",
                    type = tmpValue.type,
                    value = tmpValue.value,
                )

                val arrayI = Variable(
                    "array@i$${lastExpr.positionString}",
                    type = (tmpValue.type as ArrayType<*>).componentType(),
                )

                generator.arrayLoad(arrayI.toLValue(), array.toReference(), i.value)

                val op = ctx.OperatorSymbol()
                if (op != null) op.let { op ->
                    val componentType = (arr.type as ArrayType<*>).componentType()
                    processOperatorAssignment(componentType, value, op, arrayI)
                } else {
                    generator.move(arrayI.toLValue(), value.value)
                }

                compiler.setVariableIndexed(
                    array,
                    i,
                    arrayI.toReference()
                )
            }
        }
    }

    private fun processOperatorAssignment(
        type: Type,
        expr: Value,
        op: TerminalNode,
        target: Variable
    ) {
        if (type.isPrimitive && expr.type.isPrimitive) {
            val binOp = BinaryOperator.entries.find { it.symbol == op.text }

            if (binOp != null) {
                generator.binaryOp(
                    target.toLValue(),
                    target.toReference(),
                    expr.value,
                    binOp
                )
                return
            }
        }

        val method = compiler.expressionProcessor.getOperatorMethods(op.text, target.type, expr.type).first()

        generator.callVirtual(
            target.toLValue(),
            target.toReference(),
            constantPool.getReference(method),
            listOf(expr.value)
        )
    }

    fun processReturnStatement(ctx: AurumParser.ReturnStatementContext) {
        generator.return_(ctx.expression()?.let {
            val expr = compiler.expressionProcessor.processExpression(it)
            if (!expr.type.isSubclassOf(method.returnType())) {
                if (!method.attributes().contains<LambdaMethodAttribute>())
                    throw IllegalStateException("todo")

                method as MutableMethod
                if (method.returnType == Types.VOID)
                    method.returnType = expr.type
                else
                    method.returnType = UnionType.ofTypeModels(arrayOf(method.returnType, expr.type)).superClass()
            }

            expr
        }?.value)
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
        if (type.isArray)
            processForOnArray(ctx, type, exprValue)
        else if (type.findMethod("iterator").isPresent)
            processForOnIterable(type, ctx, exprValue)
        else
            throwAurumError("Type ${type.toUsageString()} is not iterable (does not implement Iterable) nor is array type", ctx, compiler.fileContext)
    }

    private fun processForOnArray(
        ctx: AurumParser.ForStatementContext,
        type: Type,
        exprValue: Value
    ) {
        val varName = ctx.varId(0).Identifier().text
        val varType = (type as ArrayType<*>).componentType()
        val array = exprValue.value

        val arrayLen = Variable("len@for$${ctx.positionString}", Types.INT)
        generator.getField(
            arrayLen.toLValue(),
            array,
            constantPool.getReference(type.findField("length").get())
        )

        val i = Reference.Named("i@for$${ctx.positionString}")
        generator.move(i, constantPool.getReference(0))

        val scope = Scope("for$${ctx.positionString}", compiler.currentScope)
        compiler.startScope(scope)
        val cond = Variable("cond@for$${ctx.positionString}", Types.BOOLEAN)
        generator.cmpLt(
            cond.toLValue(),
            i,
            arrayLen.toReference()
        )
        generator.neg(cond.toLValue(), cond.toReference())

        generator.jumpIf(cond.toReference(), scope.endLabel)

        val iter = Variable(varName, varType)
        generator.arrayLoad(
            iter.toLValue(),
            array,
            i
        )
        compiler.currentScope += iter

        compiler.process(ctx.block())

        generator.add(i, i, constantPool.getReference(1))

        generator.jump(scope.startLabel)

        compiler.endScope()
    }

    private fun processForOnIterable(
        type: Type,
        ctx: AurumParser.ForStatementContext,
        exprValue: Value
    ) {
        val iteratorMethod = type.findMethod(
            "iterator"
        ).get()

        val iteratorType = iteratorMethod.returnType()
        val nextMethod = iteratorType.findMethod("next").get()
        val hasNextMethod = iteratorType.findMethod("hasNext").get()
        val varType = iteratorType
            .typeArguments()
            .orElse(
                iteratorType.allInterfaces.find { it.fullName() == "java.util.Iterator" }
                    ?.typeArguments()
                    ?.getOrNull() ?: Utils.EMPTY_TYPE_ARGUMENTS
            ).ifEmpty {
                val typeCtx = ctx.varId(0).typeExpr()
                if (typeCtx != null)
                    arrayOf(TypeArgument.of("E", compiler.toType(typeCtx)))
                arrayOf(TypeArgument.of("E", nextMethod.returnType()))
            }[0].bound()
        val varName = ctx.varId(0).Identifier().text

        val iterator = Variable("iter@for$${ctx.positionString}", iteratorType)
        generator.callVirtual(
            iterator.toLValue(),
            exprValue.value,
            constantPool.getReference(iteratorMethod)
        )


        val scope = Scope("for$${ctx.positionString}", compiler.currentScope)
        compiler.startScope(scope)
        val cond0 = Variable("cond@for$${ctx.positionString}_0", Types.BOOLEAN)
        val cond1 = Variable("cond@for$${ctx.positionString}_1", Types.BOOLEAN)
        generator.callVirtual(
            cond0.toLValue(),
            iterator.toReference(),
            constantPool.getReference(hasNextMethod)
        )
        generator.neg(cond1.toLValue(), cond0.toReference())

        generator.jumpIf(cond1.toReference(), scope.endLabel)

        val iter = Variable(varName, varType)
        generator.callVirtual(
            iter.toLValue(),
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