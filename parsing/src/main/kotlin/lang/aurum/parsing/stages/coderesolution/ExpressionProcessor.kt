package lang.aurum.parsing.stages.coderesolution

import lang.aurum.attribute.OperatorAttribute
import lang.aurum.ir.*
import lang.aurum.ir.Target
import lang.aurum.ir.Target.Companion.Target
import lang.aurum.model.*
import lang.aurum.model.impl.ParameterImpl
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType
import lang.aurum.parsing.model.getType
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.full.memberProperties

class ExpressionProcessor (
    val compiler: IRCompiler,
    val method: Method
) {
    val constantPool = compiler.constantPool
    val generator = compiler.generator

    fun processExpression(expr: AurumParser.ExpressionContext): Value {
        return when (expr) {
            is AurumParser.IfExprExprContext -> processIfExpr(expr.ifExpr())
            is AurumParser.MatchExprContext -> processMatchStatement(expr.matchStatement())
            is AurumParser.LoopStatementExprContext -> processLoopStatement(expr.loopStatement())
            is AurumParser.WhileStatementExprContext -> processWhileStatement(expr.whileStatement())
            is AurumParser.LambdaExprExprContext -> processLambdaExpr(expr.lambdaExpr())
            else -> throw IllegalStateException("todo")
        }
    }

    fun processIfExpr(expr: AurumParser.IfExprContext): Value {
        val elifBlocks = mutableListOf(
            { processExpression(expr.expression(0)).value } to { compiler.process(expr.expressionBlock(0)) },
        )
        elifBlocks.addAll(
            expr.KWelif()?.mapIndexed { i, _ ->
                { processExpression(expr.expression(i + 1)).value } to { compiler.process(expr.expressionBlock(i + 1)) }
            } ?: listOf()
        )

        val elseBlock = { compiler.process(expr.expressionBlock().last()) }

        ifElifElse(
            expr,
            elifBlocks,
            elseBlock
        )

        return Value(
            Type.ofClass(Object::class.java),
            NullRef
        )
    }

    fun ifElifElse(
        expr: AurumParser.IfExprContext,
        elifBlocks: List<Pair<() -> RValue, () -> Unit>> = listOf(),
        elseBlock: (() -> Unit)? = null
    ) {
        val elifScopes = mutableListOf(Scope("if$${expr.KWif().positionString}", compiler.currentScope)) +
                expr.KWelif().map { Scope("elif$${it.positionString}", compiler.currentScope) }
        val elseScope = expr.KWelse()?.let { Scope("else$${it.positionString}", compiler.currentScope) }
        val allScopes = (elifScopes + elseScope).filterNotNull().toList()
        ifElifElse(allScopes, elifBlocks, elseScope, elseBlock)
    }

    private fun ifElifElse(
        allScopes: List<Scope>,
        elifBlocks: List<Pair<() -> RValue, () -> Unit>>,
        elseScope: Scope? = null,
        elseBlock: (() -> Unit)? = null
    ) {
        val endLabel = allScopes.last().endLabel

        allScopes.zipWithNext().zip(elifBlocks).forEach { (it, block) ->
            val current = it.first
            val next = it.second

            val condVar = Variable("cond@${current.name}", PrimitiveType.BOOLEAN)
            compiler.startScope(current)

            generator.neg(condVar.toTarget(), block.first.invoke())

            generator.jumpIf(condVar.toReference(), next.startLabel)

            block.second.invoke()
            generator.jump(endLabel)
            compiler.endScope()
        }

        elseScope?.let {
            compiler.startScope(it)
            elseBlock?.invoke()
            compiler.endScope()
        }
    }

    fun processMatchStatement(expr: AurumParser.MatchStatementContext): Value {
        val matched = processExpression(expr.expression())

        if (expr.matchCaseStatement().count { it is AurumParser.DefaultCaseContext } > 1)
            throw IllegalStateException("todo")

        val default =
            expr.matchCaseStatement().find { it is AurumParser.DefaultCaseContext }!! as AurumParser.DefaultCaseContext
        val elseScope = Scope("default$${default.positionString}", compiler.currentScope)
        val elseBlock = default.expressionBlock()?.let {
            { compiler.process(it) }
        } ?: { compiler.process(default.block()) }

        ifElifElse(
            getScopes(expr.matchCaseStatement()) + elseScope,
            expr.matchCaseStatement().mapNotNull {
                if (it !is AurumParser.PatternCaseContext) return@mapNotNull null

                {
                    val pattern = it.pattern()
                    val condVar1 = Variable("cond@${it.positionString}_0", PrimitiveType.BOOLEAN)
                    val typePattern = pattern.typePattern()
                    if (typePattern != null) {
                        val type = compiler.toType(typePattern.typeExpr())
                        val typeRef = constantPool.getReference(type)
                        generator.instanceOf(condVar1.toTarget(), matched.value, typeRef)
                        compiler.currentScope += Variable(typePattern.Identifier().text, type)
                    } else {
                        val value = processExpression(pattern.expression(0))
                        generator.cmpEq(condVar1.toTarget(), matched.value, value.value)
                    }
                    val condVar2 = Variable("cond@${it.positionString}_1", PrimitiveType.BOOLEAN)
                    if (pattern.KWwhen() != null) {
                        val whenExpr = processExpression(pattern.expression().last())
                        generator.and(condVar2.toTarget(), condVar1.toReference(), whenExpr.value)
                    } else {
                        generator.move(condVar2.toTarget(), condVar1.toReference())
                    }

                    condVar2.toReference()
                } to { compiler.process(it.expressionBlock()) }
            },
            elseScope,
            elseBlock
        )
        return Value(Type.ofClass(Object::class.java), NullRef)
    }

    private fun getScopes(expr: List<AurumParser.MatchCaseStatementContext>): List<Scope> {
        return expr.mapNotNull { case ->
            when (case) {
                is AurumParser.PatternCaseContext ->
                    Scope("case$${case.positionString}", compiler.currentScope)
//                is AurumParser.DefaultCaseContext ->
//                    Scope("default$${case.positionString}", compiler.currentScope)
                else -> null
            }
        }
    }

    fun processLoopStatement(expr: AurumParser.LoopStatementContext): Value {
        val scope = Scope("loop$${expr.positionString}", compiler.currentScope)
        compiler.startScope(scope)
        compiler.process(expr.block())
        generator.jump(scope.startLabel)
        compiler.endScope()

        return Value(
            Type.ofClass(Object::class.java),
            NullRef
        )
    }

    fun processWhileStatement(expr: AurumParser.WhileStatementContext): Value {
        val scope = Scope("while$${expr.positionString}", compiler.currentScope)
        compiler.startScope(scope)

        val condValue = processExpression(expr.expression())
        val condVar = Variable("cond@while$${expr.positionString}", PrimitiveType.BOOLEAN)
        generator.neg(condVar.toTarget(), condValue.value)
        generator.jumpIf(condVar.toReference(), scope.endLabel)

        compiler.process(expr.block())
        generator.jump(scope.startLabel)

        compiler.endScope()

        return Value(
            Type.ofClass(Object::class.java),
            NullRef
        )
    }

    fun processLambdaExpr(expr: AurumParser.LambdaExprContext): Value {
        return when (expr) {
            is AurumParser.LambdaContext -> processLambda(expr)
            is AurumParser.BinaryContext -> processBinary(expr)
            else -> throw IllegalStateException("todo")
        }
    }

    fun processLambda(expr: AurumParser.LambdaContext): Value {
        val lambdaName = "lambda@${expr.positionString}"

        val lambdaParameters = expr.lambdaParamList()?.lambdaParam()?.map {
            Value(
                compiler.toType(it.typeExpr()),
                Reference(it.Identifier().text)
            )
        } ?: listOf()

        val lambdaDelegate = MutableMethod(
            method.owner(),
            lambdaName,
            parameters = lambdaParameters.map { ParameterImpl((it.value as Reference).name, it.type) }.toMutableList(),
            attributes = mutableListOf(CodeAttribute(mutableListOf()))
        )

        (method.owner() as MutableType).methods += lambdaDelegate

        val lambdaCompiler = IRCompiler(compiler.fileContext, lambdaDelegate)
        val lambdaScope = LambdaScope(lambdaDelegate, compiler.currentScope)
        lambdaCompiler.startScope(lambdaScope)
//        lambdaCompiler.currentScope = lambdaScope

        expr.block()?.let { lambdaCompiler.process(it) }
        expr.statement()?.let { lambdaCompiler.process(it) }
        expr.expression()?.let { lambdaCompiler.process(it) }
        expr.expressionBlock()?.let { lambdaCompiler.process(it) }
        lambdaCompiler.endScope()

        val captured = lambdaScope.capturedVariables.map { (k, v) ->
            ParameterImpl(k, v.type)
        }

        lambdaDelegate.parameters += captured

        return Value(
            lambdaDelegate.getType(),
            constantPool.getReference(lambdaDelegate)
        )
    }

    // might be wrong
    fun processBinary(expr: AurumParser.BinaryContext): Value {
        // 1. Получаем все операнды и операторы
        val values = expr.postfixExpr().map(::processPostfixExpr).toMutableList()
        val operators = expr.binaryOp().map { it.text }.toMutableList()
        // Сохраняем позиции для генерации уникальных имен временных переменных и отладки
        val positions = expr.binaryOp().map { it.positionString }.toMutableList()

        // 2. Итеративно сворачиваем выражение
        while (operators.isNotEmpty()) {
            var bestIndex = -1
            var bestPrecedence = -1
            var bestAssoc = Associativity.LEFT_TO_RIGHT
            var bestOp: Any? = null // Может быть BinaryOperator или Method

            // Проходим по всем текущим операторам, чтобы найти тот, который нужно выполнить следующим
            for (i in operators.indices) {
                val left = values[i]
                val right = values[i + 1]
                val symbol = operators[i]

                var foundOp: Any? = null
                var foundPrec = -1
                var foundAssoc = Associativity.LEFT_TO_RIGHT

                // А. Пытаемся найти примитивный оператор
                if (left.type is PrimitiveType && right.type is PrimitiveType) {
                    val binOp = BinaryOperator.entries.find { it.symbol == symbol }
                    if (binOp != null) {
                        foundOp = binOp
                        foundPrec = binOp.precedence
                        foundAssoc = binOp.associativity
                    }
                }

                // Б. Если примитивный не найден, ищем перегруженный оператор (метод)
                if (foundOp == null) {
                    val methods = compiler.availableMethods.filter { method ->
                        val attr = method.attributes().find { it is OperatorAttribute } as? OperatorAttribute
                        if (attr != null && attr.isBinary && attr.symbol == symbol) {
                            if (method.isStatic) {
                                // Статический метод: оба операнда передаются как аргументы
                                method.parameters().size == 2 &&
                                        left.type.isSubclassOf(method.parameters()[0].type()) &&
                                        right.type.isSubclassOf(method.parameters()[1].type())
                            } else {
                                // Виртуальный метод: левый операнд - this, правый - аргумент
                                method.parameters().size == 1 &&
                                        left.type.isSubclassOf(method.owner()) &&
                                        right.type.isSubclassOf(method.parameters()[0].type())
                            }
                        } else {
                            false
                        }
                    }

                    // Берем первый подходящий (в будущем здесь может быть более сложная логика разрешения перегрузок)
                    val method = methods.firstOrNull()
                    if (method != null) {
                        val attr = method.attributes().find { it is OperatorAttribute } as OperatorAttribute
                        foundOp = method
                        foundPrec = attr.precedence
                        foundAssoc = attr.associativity
                    }
                }

                if (foundOp == null) {
                    throw IllegalStateException("Operator '$symbol' not found for types ${left.type} and ${right.type} at ${positions[i]}")
                }

                // В. Проверяем приоритет
                val update = if (bestIndex == -1) true
                else if (foundPrec > bestPrecedence) true
                // Если приоритеты равны, решаем на основе ассоциативности
                else if (foundPrec == bestPrecedence && foundAssoc == Associativity.RIGHT_TO_LEFT) true
                else false

                if (update) {
                    bestIndex = i
                    bestPrecedence = foundPrec
                    bestOp = foundOp
                    bestAssoc = foundAssoc
                }
            }

            // 3. Выполняем операцию с наивысшим приоритетом
            val index = bestIndex
            val left = values[index]
            val right = values[index + 1]
            val op = bestOp!!
            val variable = Variable("tmp@binary$${positions[index]}")

            if (op is BinaryOperator) {
                if (op == BinaryOperator.IS) {
                    // Оператор IS возвращает Boolean и требует TypeRef справа
                    variable.type = Type.ofClass(Boolean::class.java)
                    if (right.value !is TypeRef) {
                        throw IllegalStateException("Operator 'is' expects a Type reference on the right side")
                    }
                    generator.instanceOf(variable.toTarget(), left.value, right.value)
                } else {
                    // Определяем тип результата (Boolean для сравнений, иначе тип левого операнда)
                    val isBool = op in setOf(
                        BinaryOperator.EQ, BinaryOperator.NEQ,
                        BinaryOperator.LT, BinaryOperator.LE,
                        BinaryOperator.GT, BinaryOperator.GE
                    )
                    variable.type = if (isBool) Type.ofClass(Boolean::class.java) else left.type
                    generator.binaryOp(variable.toTarget(), left.value, right.value, op)
                }
            } else if (op is Method) {
                variable.type = op.returnType()
                if (op.isStatic) {
                    generator.call(
                        variable.toTarget(),
                        constantPool.getReference(op),
                        listOf(left.value, right.value)
                    )
                } else {
                    generator.callVirtual(
                        variable.toTarget(),
                        left.value,
                        constantPool.getReference(op),
                        listOf(right.value)
                    )
                }
            }

            // 4. Обновляем списки: заменяем операнды результатом и удаляем оператор
            values[index] = variable.toValue()
            values.removeAt(index + 1)
            operators.removeAt(index)
            positions.removeAt(index)
        }

        return values.first()
    }

    fun processPostfixExpr(expr: AurumParser.PostfixExprContext): Value {
        return when (expr) {
            is AurumParser.PostfixWithPrefContext -> processPostfixWithPref(expr)
            is AurumParser.RecursivePostfixContext -> processRecursivePostfix(expr)
            else -> throw IllegalStateException("todo")
        }
    }

    fun processPostfixWithPref(expr: AurumParser.PostfixWithPrefContext) : Value {
        val prefix = processPrefixExpr(expr.prefixExpr())

        return when (val postfix = expr.postfixPart()) {
            is AurumParser.MemberAccessContext -> processMemberAccess(prefix, postfix)
            is AurumParser.CastContext -> processCast(prefix, postfix)
            is AurumParser.FunctionCallContext -> processFunctionCall(prefix, postfix)
            is AurumParser.IndexAccessContext -> processIndexAccess(prefix, postfix)
            is AurumParser.OperatorContext -> processOperator(prefix, postfix)
            else -> prefix
        }
    }
    fun processRecursivePostfix(expr: AurumParser.RecursivePostfixContext) : Value {
        val postfixValue = processPostfixExpr(expr.postfixExpr())

        return when (val postfix = expr.postfixPart()) {
            is AurumParser.MemberAccessContext -> processMemberAccess(postfixValue, postfix)
            is AurumParser.CastContext -> processCast(postfixValue, postfix)
            is AurumParser.FunctionCallContext -> processFunctionCall(postfixValue, postfix)
            is AurumParser.IndexAccessContext -> processIndexAccess(postfixValue, postfix)
            is AurumParser.OperatorContext -> processOperator(postfixValue, postfix)
            else -> postfixValue
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun processMemberAccess(value: Value, postfix: AurumParser.MemberAccessContext): Value {
        val owner = value.type
        val memberName = postfix.Identifier().text
        val variable = Variable("tmp@member$${postfix.positionString}")

        val members = owner.members().filter { it.name() == memberName }
        if (members.isEmpty())
            throw IllegalStateException("todo")

        val type = UnionType.ofTypeModels(
            members
                .map {
                    return@map when (it) {
                        is Method -> it.getType()
                        is Field -> it.type()
                        else -> throw IllegalStateException("todo")
                    }
                }
                .toTypedArray()
        )

        variable.type = type

        if (members.size > 1) {
            when {
                members.all { it is Method } -> {
                    val ref = MethodGroupRef(
                        members
                            .map { constantPool.getReference(it) as SingleMethodRef }
                    )
                    generator.getMethod(variable.toTarget(), value.value, ref)
                }
                members.all { it is Field } -> {
                    val ref = FieldGroupRef(
                        members
                            .map { constantPool.getReference(it) as FieldRef }
                    )
                    generator.getMember(variable.toTarget(), value.value, ref)
                }
                else -> {
                    val ref = MemberGroupRef(
                        members
                            .map { constantPool.getReference(it) as MemberRef }
                    )
                    generator.getMember(variable.toTarget(), value.value, ref)
                }
            }
        } else if (members.size == 1) {
            when (val member = members[0]) {
                is Field -> {
                    if (member.isStatic)
                        generator.getStatic(
                            variable.toTarget(),
                            constantPool.getReference(member)
                        )
                    else
                        generator.getField(
                            variable.toTarget(),
                            value.value,
                            constantPool.getReference(member)
                        )
                }
                is Method -> {
                    if (member.isStatic)
                        generator.getMethodStatic(
                            variable.toTarget(),
                            constantPool.getReference(member)
                        )
                    else
                        generator.getMethod(
                            variable.toTarget(),
                            value.value,
                            constantPool.getReference(member)
                        )
                }
            }
        } else {
            throw IllegalStateException("todo")
        }

        return variable.toValue()
    }

    fun processCast(value: Value, postfix: AurumParser.CastContext): Value {
        val initialType = value.type
        val newType = compiler.toType(postfix.typeExpr())

        val variable = Variable("tmp@cast$${postfix.positionString}")

        if (!newType.isSuperclassOf(initialType) && !newType.isSubclassOf(initialType))
            throw IllegalStateException("todo")

        variable.type = newType
        generator.cast(variable.toTarget(), value.value, constantPool.getReference(newType))

        return variable.toValue()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun processFunctionCall(value: Value, postfix: AurumParser.FunctionCallContext): Value {
        val args = postfix.argList()?.expression()?.map(::processExpression) ?: listOf()
        val argTypes = args.map(Value::type)
        val variable = Variable("tmp@call$${postfix.positionString}")

        when (val fn = value.value) {
            is SingleMethodRef -> {
                processSingleMethodRefCall(variable, value.value, fn, argTypes, args)
            }

            is MethodGroupRef -> {
                processMethodGroupRefCall(fn, argTypes, variable, value.value, args)
            }

            is FieldRef -> {
                processFieldRefCall(variable, fn, argTypes, args)
            }

            is MemberGroupRef -> {
                processMethodGroupRefCall(variable, value.value, fn, argTypes, args)
            }

            is Reference -> {
                processReferenceCall(variable, value, fn, argTypes, args)
            }

            is TypeRef -> {
                processTypeRefCall(fn, variable, args)
            }

            else -> throw IllegalStateException("todo")
        }

        return variable.toValue()
    }

    private fun processTypeRefCall(
        fn: TypeRef,
        variable: Variable,
        args: List<Value>
    ) {
        val type = constantPool.dereference<Type>(fn.ref)

        generator.new(variable.toTarget(), fn)
        generator.invokeConstructor(Target.Empty, variable.toReference(), args.map(Value::value))

        variable.type = type
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun processReferenceCall(
        variable: Variable,
        obj: Value,
        fn: Reference,
        argTypes: List<Type>,
        args: List<Value>
    ) {
        val method: Method =
            when (val value = compiler.dataTracker[fn]) {
                is MethodGroupRef -> {
                    getFittingMethodFrom(value, argTypes)
                }

                is SingleMethodRef -> constantPool.dereference<Method>(value.ref)

                is FieldGroupRef -> getFittingMethodFrom(value, argTypes)

                is FieldRef -> constantPool.dereference<Field>(value.ref).type()
                    .findMethod("invoke", argTypes.toTypedArray()).orElseThrow()

                is MemberGroupRef -> getFittingMethodFrom(value, argTypes)

                is GetMember -> {
                    val memberRef = GetMember::class.memberProperties
                        .find { it.name == "member" }!!.get(value) as MemberRef

                    when (memberRef) {
                        is SingleMethodRef -> {
                            constantPool.dereference(memberRef.ref)
                        }

                        is FieldRef -> {
                            constantPool.dereference<Field>(memberRef.ref).type()
                                .findMethod("invoke", argTypes.toTypedArray()).orElseThrow()
                        }

                        is FieldGroupRef -> {
                            getFittingMethodFrom(memberRef, argTypes)
                        }

                        is MethodGroupRef -> {
                            getFittingMethodFrom(memberRef, argTypes)
                        }

                        is MemberGroupRef -> {
                            getFittingMethodFrom(memberRef, argTypes)
                        }
                    }
                }

                is GetMethod -> {
                    val methodRef = GetMethod::class.memberProperties
                        .find { it.name == "method" }!!.get(value) as MethodRef

                    when (methodRef) {
                        is SingleMethodRef -> {
                            constantPool.dereference(methodRef.ref)
                        }

                        is MethodGroupRef -> {
                            getFittingMethodFrom(methodRef, argTypes)
                        }
                    }
                }

                is GetMethodStatic -> {
                    val methodRef = GetMethodStatic::class.memberProperties
                        .find { it.name == "method" }!!.getter.call(value) as MethodRef

                    when (methodRef) {
                        is MethodGroupRef -> {
                            getFittingMethodFrom(methodRef, argTypes)
                        }

                        is SingleMethodRef -> {
                            constantPool.dereference(methodRef.ref)
                        }
                    }
                }


                else -> obj.type.findMethod("invoke", argTypes.toTypedArray()).orElseThrow()
            }

//        method = obj.type.findMethod("invoke", argTypes.toTypedArray()).getOrNull()
//                ?: throw IllegalStateException("todo")
        // todo:
        // when (compiler.dataTracker[fn]) is GetMethod -> ... (find best fitting method and call it). do it for all other cases

        val methodRef = constantPool.getReference(method)

        variable.type = method.returnType()

        generator.callMethod(variable.toTarget(), obj.value, methodRef, args.map(Value::value))
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun getFittingMethodFrom(value: MethodGroupRef, argTypes: List<Type>): Method {
        val methods = value.refs.map { constantPool.dereference<Method>(it) }
            .filter {
                it.parameters().map(Parameter::type).zip(argTypes).all { (l, r) ->
                    r.isSubclassOf(l)
                }
            }
        return getFittingMethodFrom(methods, argTypes)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun getFittingMethodFrom(value: FieldGroupRef, argTypes: List<Type>): Method {
        val methods = value.refs.map { constantPool.dereference<Field>(it) }
            .map {
                it.type().findMethod("invoke", argTypes.toTypedArray()).orElseThrow()
            }
        return getFittingMethodFrom(methods, argTypes)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun getFittingMethodFrom(value: MemberGroupRef, argTypes: List<Type>): Method {
        val methods = constantPool.dereference<Member>(value).map {
            when (it) {
                is Field -> it.type().findMethod("invoke", argTypes.toTypedArray()).orElseThrow()
                is Method -> it
                else -> throw IllegalStateException("todo")
            }
        }
        return getFittingMethodFrom(methods, argTypes)
    }

    private fun getFittingMethodFrom(
        methods: List<Method>,
        argTypes: List<Type>
    ): Method {
        val methodsParameters = methods
            .map(Method::parameters)
            .map { it.map(Parameter::type) }
        val method = methodsParameters
            .filter {
                it.size == argTypes.size
            }
            .map {
                it.zip(argTypes).map { (l, r) ->
                    Type.Comparator.INSTANCE.compare(l, r)
                }
            }
            .zip(methods)
            .sortedWith { pair1, pair2 ->
                compareValues(pair1.first.sum(), pair2.first.sum())
            }
            .firstNotNullOf { it.second }
        return method
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun processMethodGroupRefCall(
        fn: MethodGroupRef,
        argTypes: List<Type>,
        variable: Variable,
        value: RValue,
        args: List<Value>
    ) {
        val members = fn.refs.map { constantPool.dereference<Method>(it) }
        val callableMembers = members.filter {
            it.parameters().filterIndexed { i, parameter ->
                argTypes[i].isSubclassOf(parameter.type())
            }.size == it.parameters().size
        }

        val ref = callableMembers.firstNotNullOf {
            constantPool.getReference(it)
        }

        generator.callMethod(
            variable.toTarget(),
            value,
            ref,
            args.map(Value::value)
        )
    }

    private fun processSingleMethodRefCall(
        variable: Variable,
        obj: RValue,
        fn: SingleMethodRef,
        argTypes: List<Type>,
        args: List<Value>
    ) {
        val method = constantPool.dereference<Method>(fn.ref)
        if (method.parameters().filterIndexed { i, parameter ->
                argTypes[i].isSubclassOf(parameter.type())
            }.size != method.parameters().size) {
            throw IllegalStateException("todo")
        }

        if (method.isStatic) {
            generator.call(variable.toTarget(), fn, args.map(Value::value))
        } else {
            generator.callMethod(
                variable.toTarget(),
                obj,
                fn,
                args.map(Value::value)
            )
        }

        variable.type = method.returnType()
    }

    private fun processFieldRefCall(
        variable: Variable,
        fn: FieldRef,
        argTypes: List<Type>,
        args: List<Value>
    ) {
        val field = constantPool.dereference<Field>(fn.ref)

        val method = field.type().findMethod("invoke", argTypes.toTypedArray()).getOrNull()
            ?: throw IllegalStateException("todo")

        generator.callVirtual(
            variable.toTarget(),
            fn,
            constantPool.getReference(method),
            args.map(Value::value)
        )

        variable.type = method.returnType()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun processMethodGroupRefCall(
        variable: Variable,
        obj: RValue,
        fn: MemberGroupRef,
        argTypes: List<Type>,
        args: List<Value>
    ) {
        val members = constantPool.dereference<Member>(fn)
        val callableMembers = members.filter {
            when (it) {
                is Method -> it.parameters().filterIndexed { i, parameter ->
                    argTypes[i].isSubclassOf(parameter.type())
                }.size == method.parameters().size

                is Field -> {
                    it.type().findMethod("invoke", argTypes.toTypedArray()).isPresent
                }

                else -> false
            }
        }

        val ref = callableMembers.firstNotNullOfOrNull {
            return@firstNotNullOfOrNull when (it) {
                is Method -> {
                    it
                }

                is Field -> {
                    // calling get on Optional without check is allowed because check was performed
                    // while constructing callableMethods
                    it.type().findMethod("invoke", argTypes.toTypedArray()).get()
                }

                else -> null
            }
        }

        generator.callMethod(
            variable.toTarget(),
            obj,
            constantPool.getReference(ref!!),
            args.map(Value::value)
        )

        variable.type = ref.returnType()
    }

    fun processIndexAccess(value: Value, postfix: AurumParser.IndexAccessContext): Value {
        val type = value.type
        val variable = Variable("tmp@index$${postfix.positionString}")

        if (type is ArrayType<*>) {
            var tmpValue = value
            postfix.indexAccessPart().argList().expression().forEachIndexed { i, expr ->
                val index = processExpression(expr)
                val tmpName = "tmp@index@$i$${expr.positionString}"
                generator.arrayLoad(Target(tmpName), tmpValue.value, index.value)
                tmpValue = Value((tmpValue.type as ArrayType<*>).componentType(), Reference(tmpName))
            }

            variable.type = tmpValue.type
        } else {
            val indices = postfix.indexAccessPart().argList().expression().map(::processExpression)

            val method = type.findMethod("get", indices.map(Value::type).toTypedArray())
                .orElseThrow { IllegalStateException("todo") }

            generator.callVirtual(
                variable.toTarget(),
                value.value,
                constantPool.getReference(method),
                indices.map(Value::value)
            )
        }

        return variable.toValue()
    }

    fun processOperator(value: Value, postfix: AurumParser.OperatorContext): Value {
        val type = value.type
        val variable = Variable("tmp@prefix$${postfix.positionString}")
        val operatorSymbol = postfix.OperatorSymbol().text

        if (type is PrimitiveType) {
            val operator = UnaryOperator.entries.find {
                it.symbol == operatorSymbol && it.associativity == Associativity.PREFIX
            }!!

            when (operator) {
                UnaryOperator.POST_INC -> generator.add(variable.toTarget(), value.value, constantPool.getConstant(1))
                UnaryOperator.POST_DEC -> generator.sub(variable.toTarget(), value.value, constantPool.getConstant(1))
                else -> {}
            }
            variable.type = type

            return variable.toValue()
        }

        val operatorMethods = compiler.availableMethods
            .filter { method ->
                method.attributes().any {
                    it is OperatorAttribute && !it.isBinary && it.associativity == Associativity.POSTFIX
                            && it.symbol == operatorSymbol
                }
                        && if (method.isStatic) { method.parameters()[0].type() == type }
                else if (method.parameters().size == 0) { method.owner().isSuperclassOf(type) }
                else { false }
            }

        val first = operatorMethods.first()
        if (first.isStatic) {
            generator.call(
                variable.toTarget(),
                constantPool.getReference(first),
                listOf(value.value)
            )
        } else {
            generator.callVirtual(
                variable.toTarget(),
                value.value,
                constantPool.getReference(first)
            )
        }

        variable.type = first.returnType()
        return variable.toValue()
    }

    fun processPrefixExpr(expr: AurumParser.PrefixExprContext): Value {
        val primary = processPrimaryExpr(expr.primaryExpr())
        if (expr.OperatorSymbol() == null)
            return primary

        val type = primary.type
        val variable = Variable("tmp@prefix$${expr.positionString}")
        val operatorSymbol = expr.OperatorSymbol().text

        if (type is PrimitiveType) {
            val operator = UnaryOperator.entries.find {
                it.symbol == operatorSymbol && it.associativity == Associativity.PREFIX
            }!!

            when (operator) {
                UnaryOperator.INC -> generator.add(variable.toTarget(), primary.value, constantPool.getConstant(1))
                UnaryOperator.DEC -> generator.sub(variable.toTarget(), primary.value, constantPool.getConstant(1))
                UnaryOperator.MINUS,
                UnaryOperator.NEG,
                UnaryOperator.COMPL-> generator.neg(variable.toTarget(), primary.value)
                else -> {}
            }
            variable.type = type

            return variable.toValue()
        }

        val operatorMethods = compiler.availableMethods
            .filter { method ->
                method.attributes().any {
                    it is OperatorAttribute && !it.isBinary && it.associativity == Associativity.PREFIX
                            && it.symbol == operatorSymbol
                }
                        && if (method.isStatic) { method.parameters()[0].type() == type }
                           else if (method.parameters().size == 0) { method.owner().isSuperclassOf(type) }
                           else { false }
            }

        val first = operatorMethods.first()
        if (first.isStatic) {
            generator.call(
                variable.toTarget(),
                constantPool.getReference(first),
                listOf(primary.value)
            )
        } else {
            generator.callVirtual(
                variable.toTarget(),
                primary.value,
                constantPool.getReference(first)
            )
        }

        variable.type = first.returnType()
        return variable.toValue()
    }

    fun processPrimaryExpr(expr: AurumParser.PrimaryExprContext): Value {
        return when (expr) {
            is AurumParser.IdentifierContext -> processIdentifier(expr)
            is AurumParser.LiteralContext -> processLiteral(expr)
            is AurumParser.ParenContext -> processParen(expr)
            is AurumParser.ArrayContext -> processArray(expr)
            else -> throw IllegalStateException("todo")
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun processIdentifier(expr: AurumParser.IdentifierContext): Value {
        val identifier = expr.text

        val imported = compiler.fileContext.importMap[identifier] as Any?
        return when {
            identifier == "this" -> {
                Value(
                    method.owner(),
                    Reference("this")
                )
            }

            identifier in method.parameters().map(Parameter::name) -> {
                Value(
                    method.parameters().find { it.name() == identifier }!!.type(),
                    Reference(identifier)
                )
            }

            identifier in compiler.currentScope -> {
                val variable = compiler.currentScope[identifier]!!
                variable.toReferenceValue()
            }

            imported != null -> {
                when (imported) {
                    is Method -> Value(
                        imported.getType(),
                        constantPool.getReference(imported)
                    )
                    is Field -> Value(
                        imported.type(),
                        constantPool.getReference(imported)
                    )
                    is Type -> Value(
                        imported,
                        constantPool.getReference(imported)
                    )
                    else -> throw IllegalStateException()
                }
            }

            compiler.fileContext.classes.keys.any { it.className() == identifier } -> {
                val type = compiler.fileContext.classes.keys.find { it.className() == identifier }!!
                Value(
                    type,
                    constantPool.getReference(type)
                )
            }

            method.owner().members().any { it.name() == identifier } -> {
                val filteredMembers = method.owner().members().filter { it.name() == identifier }
                if (filteredMembers.all { it is Method }) {
                    if (filteredMembers.size == 1)
                        return Value(
                            (filteredMembers.first() as Method).getType(),
                            constantPool.getReference(filteredMembers.first() as Method)
                        )

                    val methods = filteredMembers.map { it as Method }
                    val value = MethodGroupRef(
                        methods
                            .map(constantPool::getReference)
                    )

                    val type = UnionType.ofTypeModels(methods.map(Method::getType).toTypedArray())

                    return Value(type, value)
                }

                if (filteredMembers.size == 1)
                    return Value(
                        (filteredMembers.first() as Field).type(),
                        constantPool.getReference(filteredMembers.first() as Field)
                    )

                val value = MemberGroupRef(
                    filteredMembers.map {
                        when (it) {
                            is Method -> constantPool.getReference(it)
                            is Field -> constantPool.getReference(it)
                            else -> throw IllegalStateException()
                        }
                    }
                )
                val type = UnionType.ofTypeModels(
                    filteredMembers.map {
                        when (it) {
                            is Method -> it.getType()
                            is Field -> it.type()
                            else -> throw IllegalStateException()
                        }
                    }.toTypedArray()
                )

                return Value(type, value)
            }

            else -> throw IllegalStateException()
        }
    }

    fun processLiteral(expr: AurumParser.LiteralContext): Value {
        return when {
            expr.text == "null" -> Value(Type.ofClass(Object::class.java), NullRef)
            expr.text == "true" -> Value(
                Type.ofClass(Boolean::class.java),
                constantPool.getConstant(true)
            )
            expr.text == "false" -> Value(
                Type.ofClass(Boolean::class.java),
                constantPool.getConstant(false)
            )
            expr.text.startsWith('"') && expr.text.endsWith('"') ->
                Value(
                    Type.ofClass(String::class.java),
                    constantPool.getConstant(expr.text.drop(1).dropLast(1))
                )
            expr.text.startsWith('\'') && expr.text.endsWith('\'') ->
                Value(
                    Type.ofClass(String::class.java),
                    constantPool.getConstant(expr.text.drop(1).dropLast(1))
                )
            expr.text.endsWith("d", true) -> Value(
                Type.ofClass(Double::class.java),
                constantPool.getConstant(expr.text.dropLast(1).toDouble())
            )
            expr.text.endsWith("f", true) -> Value(
                Type.ofClass(Float::class.java),
                constantPool.getConstant(expr.text.dropLast(1).toFloat())
            )
            expr.text.endsWith("l", true) -> Value(
                Type.ofClass(Long::class.java),
                constantPool.getConstant(expr.text.dropLast(1).toLong())
            )
            else -> Value(
                Type.ofClass(Int::class.java),
                constantPool.getConstant(expr.text.toInt())
            )
        }
    }
    fun processParen(expr: AurumParser.ParenContext): Value = processExpression(expr.expression())
    fun processArray(expr: AurumParser.ArrayContext): Value {
        val variable = Variable("tmp@array$${expr.positionString}")

        val values = expr.expression().map(::processExpression)
        val type = UnionType.ofTypeModels(
            values.map(Value::type).toTypedArray()
        ).superClass()
        variable.type = type

        generator.newArray(
            variable.toTarget(),
            constantPool.getReference(variable.type),
            constantPool.getConstant(values.size)
        )

        for ((i, value) in values.withIndex()) {
            compiler.setVariableIndexed(variable, constantPool.getConstant(i), value)
        }

        return variable.toValue()
    }

}