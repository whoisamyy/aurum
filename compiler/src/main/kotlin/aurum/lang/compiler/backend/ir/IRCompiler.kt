package aurum.lang.compiler.backend.ir

import aurum.lang.Tuple
import aurum.lang.compiler.frontend.attribute.contains
import aurum.lang.compiler.frontend.attribute.get
import aurum.lang.compiler.frontend.model.MutableMethod
import aurum.lang.compiler.frontend.model.MutableType
import aurum.lang.compiler.frontend.model.getType
import aurum.lang.compiler.frontend.stages.analyzing.ImportMap
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.compiler.frontend.stages.parsing.Token
import aurum.lang.compiler.frontend.stages.typeresolving.AbstractTypeResolver
import aurum.lang.ir.*
import aurum.lang.model.*
import aurum.lang.model.attribute.*
import java.lang.reflect.AccessFlag
import kotlin.jvm.optionals.getOrElse

class IRCompiler(
    private val method: Method,
    internal val typeResolver: AbstractTypeResolver,
    internal val cp: ConstantPool,
    internal val importMap: ImportMap
) {
    private val thisType: Type =
        if (method.owner().attributes().contains<ExtensionAttribute>())
            method.owner().attributes().get<ExtensionAttribute>()!!.type
        else
            method.owner()

    private val instructions = mutableListOf<Instruction>()
    internal val irBuilder = IRBuilder(instructions)
    internal val instructionList get() = instructions
    internal var currentScope = Scope(method.name())

    private val ir get() = irBuilder
    private val controlFlow = ControlFlowCompiler(this)

    init {
        method.parameters().forEach { createVariable(it.name(), it.type()) }
    }

    private fun variable(name: String): Variable? = currentScope[name]

    internal fun createVariable(name: String, type: Type): Variable {
        val v = Variable(name, type)
        currentScope[name] = v
        return v
    }

    internal fun materializeToVariable(value: Value, name: String): Variable =
        when (value) {
            is Variable -> value
            else -> createVariable(name, value.type).also {
                ir += Move(it.reference, value.ref())
            }
        }

    internal fun enterScope(scope: Scope) {
        currentScope = scope
        ir += LabelInst(scope.startLabel)
    }

    internal fun newScope(name: String, parent: Scope? = null): Scope =
        Scope(name, parent ?: currentScope)

    internal fun exitScope() {
        ir += LabelInst(currentScope.endLabel)
        currentScope = currentScope.parent ?: error("Cannot exit scope with no parent")
    }

    private fun markEnclosingLambdaAsInstance() {
        if (method.attributes().contains<LambdaMethodAttribute>()) {
            (method as MutableMethod).accessFlags.remove(AccessFlag.STATIC)
        }
    }

    internal fun astCodeBlockType(block: ASTNode.CodeBlock): Type =
        when (block) {
            is ASTNode.ExpressionBlock -> astExpressionType(block.expression)
            is ASTNode.PlainBlock -> Types.VOID
            is ASTNode.Expression -> astExpressionType(block)
            is ASTNode.Statement -> Types.VOID
        }

    internal fun astExpressionType(expr: ASTNode.Expression): Type =
        when (expr) {
            is ASTNode.Literal.Null -> Types.OBJECT
            is ASTNode.Literal.True, is ASTNode.Literal.False -> Types.BOOLEAN
            is ASTNode.Literal.Number -> Type.ofClass(expr.value::class.javaPrimitiveType)
            is ASTNode.Literal.String -> Types.STRING
            is ASTNode.ParenthesizedExpression -> astExpressionType(expr.expression)
            is ASTNode.IdentifierExpression -> variable(expr.identifier)?.type ?: Types.OBJECT
            is ASTNode.TypeExpr -> typeResolver.getType(expr)
            else -> Types.OBJECT
        }

    fun compile(codeBlock: ASTNode.CodeBlock): CodeAttribute {
        compileCodeBlock(codeBlock)
        return CodeAttribute(instructions)
    }

    internal fun compileCodeBlock(codeBlock: ASTNode.CodeBlock) {
        when (codeBlock) {
            is ASTNode.Expression -> compileExpression(codeBlock)
            is ASTNode.Statement -> compileStatement(codeBlock)
            is ASTNode.PlainBlock -> codeBlock.statements?.forEach(::compileStatement)
            is ASTNode.ExpressionBlock -> {
                codeBlock.statements?.forEach(::compileStatement)
                val value = compileExpression(codeBlock.expression)
                if (currentScope.parent == null || currentScope is LambdaScope) {
                    if (value.type != Types.VOID)
                        returnValue(value)
                    else
                        ir += Return()
                }
            }
        }
    }

    internal fun compileExpression(expr: ASTNode.Expression): Value =
        when (expr) {
            is ASTNode.ParenthesizedExpression -> compileExpression(expr.expression)

            is ASTNode.Literal.Null -> Value(NullRef, Types.OBJECT)
            is ASTNode.Literal.True -> Value(this.cp.getConstant(1), Types.BOOLEAN)
            is ASTNode.Literal.False -> Value(this.cp.getConstant(0), Types.BOOLEAN)
            is ASTNode.Literal.Number -> Value(this.cp.getConstant(expr.value), Type.ofClass(expr.value::class.javaPrimitiveType))
            is ASTNode.Literal.String -> Value(this.cp.getConstant(expr.value), Types.STRING)

            is ASTNode.ArrayExpression -> compileArrayExpression(expr)
            is ASTNode.TupleExpression -> compileTupleExpression(expr)
            is ASTNode.NamedTupleExpression -> TODO("Needs dynamic class generation")

            is ASTNode.IdentifierExpression -> compileIdentifierExpression(expr)
            is ASTNode.IndexAccess -> compileIndexAccessExpression(expr)
            is ASTNode.MemberAccess -> compileMemberAccess(expr)
            is ASTNode.Cast -> compileCastExpression(expr)
            is ASTNode.BinaryExpression -> compileBinaryExpression(expr)
            is ASTNode.FunctionCall -> compileFunctionCall(expr)
            is ASTNode.LambdaExpression -> compileLambdaExpression(expr)
            is ASTNode.MapExpression -> compileMapExpression(expr)
            is ASTNode.PrefixOperatorExpression -> compilePrefixOperatorExpression(expr)
            is ASTNode.TypeExpr -> {
                val type = typeResolver.getType(expr)
                Value(cp.getReference(type), type)
            }

            else -> Value.DISCARD
        }

    private fun compileLambdaExpression(expr: ASTNode.LambdaExpression): Value {
        val lambdaMethod = MutableMethod(
            method.owner(),
            "lambda#${hashStringOf(expr)}",
            accessFlags = mutableListOf(AccessFlag.STATIC)
        )

        val params = expr.parameters?.map { (name, type) ->
            Parameter.of(name, typeResolver.getTypeOrNull(type) ?: Types.OBJECT)
        }?.toMutableList() ?: mutableListOf()

        lambdaMethod.parameters = params

        val lambdaCompiler = IRCompiler(
            lambdaMethod,
            typeResolver,
            cp,
            importMap
        )

        val lambdaScope = LambdaScope(
            lambdaMethod.name,
            currentScope
        )

        lambdaMethod.parameters.forEach {
            lambdaScope[it.name()] = Variable(it.name(), it.type())
        }

        lambdaCompiler.currentScope = lambdaScope

        lambdaMethod.attributes += LambdaMethodAttribute(lambdaMethod.getType())

        lambdaMethod.attributes += lambdaCompiler.compile(expr.codeBlock)

        lambdaMethod.parameters += lambdaScope.captures.map { (name, variable) ->
            Parameter.of(name, variable.type)
        }

        lambdaMethod.attributes.removeIf { it is LambdaMethodAttribute }
        lambdaMethod.attributes += LambdaMethodAttribute(lambdaMethod.getType())

        (thisType as MutableType).methods += lambdaMethod

        return Value(
            cp.getReference(lambdaMethod),
            lambdaMethod.getType()
        )
    }

    private data class BinaryOperatorResolution(
        val operator: Operator,
        val method: Method? = null,
    )

    private fun findBinaryOperator(
        left: Value,
        right: Value,
        opSymbol: String,
    ): BinaryOperatorResolution {
        BinaryOperator.entries.find { it.symbol == opSymbol }
            ?.takeIf { left.type.isPrimitive && right.type.isPrimitive }
            ?.let { return BinaryOperatorResolution(it) }

        var method: Method? = (typeResolver.filter { it.attributes().get<ExtensionAttribute>()?.type == left.type }
            .flatMap { it.methods().toList() } + left.type.methods())
            .filter { it.name() == opSymbol }
            .minByOrNull {
                val (s, avg) = if (it.isStatic)
                    it.getFitDegree(arrayOf(left.type, right.type))
                else
                    it.getFitDegree(arrayOf(right.type))

                s + avg
            }

        var custom: CustomOperator? = method?.attributes()?.get<CustomOperator>()

        if (custom == null) {
            left.type.findMethodExact(opSymbol, arrayOf(left.type, right.type))
                .filter { it.isStatic }
                .ifPresent { m ->
                    m.attributes().get<CustomOperator>()
                        ?.takeIf { it.isBinary }
                        ?.let { op ->
                            method = m
                            custom = op
                        }
                }
        }

        if (method?.owner()?.attributes()?.contains<ExtensionAttribute>() == true) {
            left.producer = GetMethod(
                Reference.Empty,
                left.ref(),
                cp.getReference(method)
            )
        }

        return BinaryOperatorResolution(
            custom ?: error("No binary operator `$opSymbol` found for ${left.type.toUsageString()} and ${right.type.toUsageString()}"),
            method,
        )
    }

    private fun getBinaryOperatorMetadata(opSymbol: String, leftType: Type): Pair<Int, Associativity> {
        BinaryOperator.entries.find { it.symbol == opSymbol }
            ?.let { return it.precedence to it.associativity }

        for (method in leftType.methods()) {
            if (method.name() != opSymbol) continue
            val custom = method.attributes().get<CustomOperator>() ?: continue
            if (!custom.isBinary) continue
            return custom.precedence to custom.associativity
        }

        return Operator.DEFAULT_PRECEDENCE to Associativity.LEFT_TO_RIGHT
    }

    private fun compileBinaryExpression(expr: ASTNode.BinaryExpression): Value {
        if (expr.operators.isEmpty())
            return compileExpression(expr.expressions[0])

        return compileBinaryWithPrecedence(expr.expressions, expr.operators, 0, 0).first
    }

    /**
     * Pratt-style parsing over the flat binary-expression lists from the parser.
     * @param opStart index of the left operand in [expressions] (also the first operator index to consider)
     * @param minPrec minimum precedence to bind at this level
     * @return compiled value and the index of the first operator *not* consumed
     */
    private fun compileBinaryWithPrecedence(
        expressions: List<ASTNode.Expression>,
        operators: List<Token.OperatorSymbol>,
        opStart: Int,
        minPrec: Int,
    ): Pair<Value, Int> {
        var left = compileExpression(expressions[opStart])
        var i = opStart

        while (i < operators.size) {
            val opSymbol = operators[i].value
            val (prec, assoc) = getBinaryOperatorMetadata(opSymbol, left.type)
            if (prec < minPrec)
                return left to i

            val nextMinPrec = if (assoc == Associativity.RIGHT_TO_LEFT) prec else prec + 1
            val (right, nextI) = compileBinaryWithPrecedence(expressions, operators, i + 1, nextMinPrec)
            left = compileBinaryOperation(left, expressions[i + 1], opSymbol, right)
            i = nextI
        }

        return left to i
    }

    internal fun compileBinaryOperation(
        left: Value,
        rightExpr: ASTNode.Expression,
        opSymbol: String,
        right: Value? = null,
    ): Value {
        if (opSymbol == "is" && rightExpr is ASTNode.TypeExpr) {
            val type = typeResolver.getType(rightExpr)
            val variable = createVariable(
                "is#${hashStringOf(rightExpr)}",
                Types.BOOLEAN,
            )
            val instanceOf = InstanceOf(
                variable.reference,
                left.ref(),
                cp.getReference(type),
            )
            ir.emit(instanceOf, variable)
            return variable
        }

        val rightValue = right ?: compileExpression(rightExpr)
        val resolution = findBinaryOperator(left, rightValue, opSymbol)

        return when (val operator = resolution.operator) {
            is BinaryOperator -> compilePrimitiveBinaryOperation(left, rightValue, operator)
            is CustomOperator -> compileCustomBinaryOperation(left, rightValue, resolution.method!!)
            else -> error("Unknown operator: $operator")
        }
    }

    private fun binaryResultType(operator: BinaryOperator, left: Type): Type =
        when (operator) {
            BinaryOperator.EQ, BinaryOperator.NEQ, BinaryOperator.LT, BinaryOperator.LE,
            BinaryOperator.GT, BinaryOperator.GE, BinaryOperator.AND, BinaryOperator.OR,
            BinaryOperator.IS,
            -> Types.BOOLEAN
            else -> left
        }

    private fun compilePrimitiveBinaryOperation(
        left: Value,
        right: Value,
        operator: BinaryOperator,
    ): Value {
        val resultType = binaryResultType(operator, left.type)
        val variable = createVariable(
            "binop#${hashStringOf(left)}",
            resultType,
        )

        when (operator) {
            BinaryOperator.AND, BinaryOperator.OR -> {
                if (left.type == Types.BOOLEAN && right.type == Types.BOOLEAN) {
                    compileShortCircuitLogical(variable, left, right, operator)
                } else {
                    val binaryOp = BinaryOp(
                        variable.reference,
                        left.ref(),
                        right.ref(),
                        operator,
                    )
                    ir.emit(binaryOp, variable)
                }
                return variable
            }
            else -> {
                val binaryOp = BinaryOp(
                    variable.reference,
                    left.ref(),
                    right.ref(),
                    operator,
                )
                ir.emit(binaryOp, variable)
                return variable
            }
        }
    }

    private fun compileShortCircuitLogical(
        variable: Variable,
        left: Value,
        right: Value,
        operator: BinaryOperator,
    ) {
        val trueConst = cp.getConstant(1)
        val falseConst = cp.getConstant(0)
        val endLabel = Label("binop#${variable.name}#end")

        when (operator) {
            BinaryOperator.AND -> {
                val falseLabel = Label("binop#${variable.name}#false")
                ir += JumpIfN(left.ref(), falseLabel)
                ir += JumpIfN(right.ref(), falseLabel)
                ir += Move(variable.reference, trueConst)
                ir += Jump(endLabel)
                ir += LabelInst(falseLabel)
                ir += Move(variable.reference, falseConst)
                ir += LabelInst(endLabel)
            }

            BinaryOperator.OR -> {
                val trueLabel = Label("binop#${variable.name}#true")
                ir += JumpIf(left.ref(), trueLabel)
                ir += JumpIf(right.ref(), trueLabel)
                ir += Move(variable.reference, falseConst)
                ir += Jump(endLabel)
                ir += LabelInst(trueLabel)
                ir += Move(variable.reference, trueConst)
                ir += LabelInst(endLabel)
            }

            else -> error("Not a logical operator: $operator")
        }
    }

    private fun compileCustomBinaryOperation(
        left: Value,
        right: Value,
        method: Method,
    ): Value {
        val variable = createVariable(
            "binop#${hashStringOf(left to right)}",
            method.returnType(),
        )
        return compileMethodInvocation(method, variable, listOf(right), left)
    }

    private fun compilePrefixOperatorExpression(expr: ASTNode.PrefixOperatorExpression): Value {
        val exprValue = compileExpression(expr.expression)

        var method: Method? = null
        val operator = UnaryOperator.entries.find { it.symbol == expr.prefix.value }
            ?.takeIf { exprValue.type.isPrimitive }
            ?:  // first try getting static method for operator and then instance method
                exprValue.type.findMethodExact(expr.prefix.value, arrayOf(exprValue.type))
                    .map { it.attributes().get<CustomOperator>()!!.also { _ -> method = it } }
                    .filter { !it.isBinary && it.associativity == Associativity.PREFIX }
                    .orElseGet {
                        exprValue.type.findMethodExact(expr.prefix.value, arrayOf<Type>())
                            .map { it.attributes().get<CustomOperator>()!!.also { _ -> method = it } }
                            .filter { !it.isBinary && it.associativity == Associativity.PREFIX }
                            .orElseGet { error("No unary operator ${expr.prefix.value} found for ${exprValue.type.toUsageString()}") }
                    }

        val variable = createVariable(
            "operator#${hashStringOf(expr)}",
            if (operator is UnaryOperator)
                exprValue.type
            else method?.returnType()
                ?: error("Cannot infer type of: $expr")
        )

        if (operator is UnaryOperator) {
            when (operator) {
                UnaryOperator.MINUS -> {
                    val binaryOp = BinaryOp(
                        variable.reference,
                        exprValue.ref(),
                        cp.getConstant(-1),
                        BinaryOperator.MUL
                    )
                    ir.emit(binaryOp, variable)
                    return variable
                }

                UnaryOperator.NEG,
                UnaryOperator.COMPL -> {
                    val neg = Neg(
                        variable.reference,
                        exprValue.ref()
                    )
                    ir.emit(neg, variable)
                    return variable
                }

                UnaryOperator.DEC -> {
                    val operand = exprValue.ref()
                    @Suppress("USELESS_IS_CHECK")
                    if (operand !is LValue && operand !is RValue)
                        error("Cannot use `--` on non-lvalues")
                    ir += BinaryOp(
                        operand as LValue,
                        operand,
                        cp.getConstant(1),
                        BinaryOperator.SUB
                    )

                    return Value(
                        operand,
                        exprValue.type
                    )
                }

                UnaryOperator.INC -> {
                    val operand = exprValue.ref()
                    @Suppress("USELESS_IS_CHECK")
                    if (operand !is LValue && operand !is RValue)
                        error("Cannot use `++` on non-lvalues")
                    ir += BinaryOp(
                        operand as LValue,
                        operand,
                        cp.getConstant(1),
                        BinaryOperator.ADD
                    )

                    return Value(operand, exprValue.type)
                }

                else -> {}
            }
        } else if (operator is CustomOperator && method != null) {
            return if (method.isStatic)
                compileMethodInvocation(method, variable, listOf(exprValue), exprValue)
            else
                compileMethodInvocation(method, variable, listOf(), exprValue)
        }
        error("Could not find operator nor method `${expr.prefix.value}`")
    }

    private fun compileMapExpression(expr: ASTNode.MapExpression): Variable {
        val keyValues = expr.keyValues
            ?.map { (k, v) -> compileExpression(k) to compileExpression(v) }

        val kvMap = keyValues?.toMap()
        val kType = UnionType.ofTypeModels(
            kvMap?.keys
                ?.map(Value::type)
                ?.toSet()?.toTypedArray()
                ?: arrayOf(Types.OBJECT)
        ).superClass()

        val vType = UnionType.ofTypeModels(
            kvMap?.values
                ?.map(Value::type)
                ?.toSet()?.toTypedArray()
                ?: arrayOf(Types.OBJECT)
        ).superClass()

        val mapType = Type.ofClass(Map::class.javaObjectType).withTypeArguments(kType, vType)
        val variable = createVariable(
            "map#${hashStringOf(expr)}",
            mapType
        )

        val newMap = New(
            variable.reference,
            cp.getReference(mapType)
        )

        ir.emit(newMap, variable)

        val putMethod = mapType.findMethod("put").get()
        keyValues?.forEach { (k, v) ->
            ir += CallVirtual(
                Reference.Empty,
                variable.reference,
                cp.getReference(putMethod),
                listOf(k, v).refs()
            )
        }

        return variable
    }

    private fun compileTupleExpression(expr: ASTNode.TupleExpression): Variable {
        val elements = expr.expressions?.map(::compileExpression)
            ?: run {
                val tupleType = Type.ofClass(Tuple::class.java)
                val method = tupleType.findMethod("getEmptyTuple").get()

                val variable = createVariable(
                    "tuple#empty#${hashStringOf(expr)}",
                    tupleType
                )

                ir.emit(
                    Call(
                        variable.reference,
                        cp.getReference(method),
                        listOf()
                    ),
                    variable
                )

                return variable
            }

        val tupleType = generateTupleOf(*elements.map { it.type }.toTypedArray())

        val variable = createVariable(
            "tuple#${hashStringOf(expr)}",
            tupleType
        )

        val newTuple = New(
            variable.reference,
            cp.getReference(tupleType)
        )

        ir.emit(newTuple, variable)

        val invokeConstructor = InvokeConstructor(
            variable.reference,
            elements.refs()
        )

        ir += invokeConstructor

        return variable
    }

    private fun compileFunctionCall(expr: ASTNode.FunctionCall): Value {
        val callable = compileExpression(expr.expression)
        val args = expr.arguments?.map(::compileExpression) ?: listOf()

        val argTypes = args.map(Value::type)

        return when (val value = callable.ref()) {
            is Reference.Named -> {
                compileFunctionCallOnNamedReference(callable, expr, args, argTypes)
            }

            is SingleMethodRef -> {
                compileFunctionCallOnSingleMethodRef(value, expr, argTypes, args, callable)
            }

            is MethodGroupRef -> {
                compileFunctionCallOnMethodGroupRef(value, argTypes, expr, args, callable)
            }

            is MemberGroupRef ->
                invokeFromMemberGroup(value, expr, argTypes, args, callable)

            is FieldRef ->
                compileFunctionCallOnFieldRef(value, argTypes, expr, args, callable)

            is TypeRef ->
                compileConstructorCall(value, argTypes, expr, args)

            Reference.This -> {
                if (this.method.name() == "<init>") {
                    this.thisType.getMethods("<init>").toMutableList()
                        .also { it.remove(this.method) }
                        .find { it.getFitDegree(argTypes.toTypedArray()).first != Int.MAX_VALUE }
                        ?: error("No suitable constructor found")

                    ir += InvokeConstructor(Reference.This, args.map { it.ref() })
                    Value.DISCARD
                } else {
                    error("Cannot use this() in current context")
                }
            }

            Reference.Super -> {
                if (this.method.name() == "<init>") {
                    this.thisType.superClass()?.getMethods("<init>")?.toMutableList()
                        ?.also { it.remove(this.method) }
                        ?.find { it.getFitDegree(argTypes.toTypedArray()).first != Int.MAX_VALUE }
                        ?: error("No suitable constructor found")

                    ir += InvokeConstructor(Reference.Super, args.map { it.ref() })
                    Value.DISCARD
                } else {
                    error("Cannot use this() in current context")
                }
            }

            else -> error("Unmatched case: $value of type ${value.javaClass}")
        }
    }

    private fun compileConstructorCall(
        value: TypeRef,
        argTypes: List<Type>,
        expr: ASTNode.FunctionCall,
        args: List<Value>
    ): Variable {
        val type = cp.dereference(value)

        type.findMethod("<init>", argTypes.toTypedArray()).orElseThrow()

        val variable = createVariable(
            "new#${hashStringOf(expr)}",
            type
        )

        val new = New(
            variable.reference,
            value
        )
        ir.emit(new, variable)

        ir += InvokeConstructor(variable.reference, args.refs())

        return variable
    }

    private fun compileFunctionCallOnNamedReference(
        callable: Value,
        expr: ASTNode.FunctionCall,
        args: List<Value>,
        argTypes: List<Type>
    ): Value = when (val producer = callable.producer) {
        is GetMethod -> when (val ref = producer.method) {
            is SingleMethodRef -> compileCall(expr, cp.dereference(ref), args, callable)
            is MethodGroupRef -> compileFunctionCallOnMethodGroupRef(ref, argTypes, expr, args, callable)
        }

        is GetMember -> when (val ref = producer.member) {
            is SingleMethodRef -> compileCall(expr, cp.dereference(ref), args, callable)
            is MethodGroupRef -> compileFunctionCallOnMethodGroupRef(ref, argTypes, expr, args, callable)
            is MemberGroupRef -> invokeFromMemberGroup(ref, expr, argTypes, args, callable)
            is FieldRef -> compileFunctionCallOnFieldRef(ref, argTypes, expr, args, callable)
            else -> TODO("${ref.javaClass}")
        }

        is New -> {
            val type = cp.dereference(producer.classRef)
            val method = getBestFittingMethod(callOrInvokeMethods(type), argTypes)
            val variable = createVariable("call#${hashStringOf(expr)}", type)
            compileMethodInvocation(method, variable, args, callable)
        }

        else -> {
            val method = getBestFittingMethod(callOrInvokeMethods(callable.type), argTypes)
            compileCall(expr, method, args, callable)
        }
    }

    private fun compileFunctionCallOnSingleMethodRef(
        value: SingleMethodRef,
        expr: ASTNode.FunctionCall,
        argTypes: List<Type>,
        args: List<Value>,
        callable: Value
    ): Value {
        val method = cp.dereference(value)
        validateCallSignature(method, argTypes)
        return compileCall(expr, method, args, callable)
    }

    private fun compileFunctionCallOnMethodGroupRef(
        value: MethodGroupRef,
        argTypes: List<Type>,
        expr: ASTNode.FunctionCall,
        args: List<Value>,
        callable: Value
    ): Value {
        val method = getBestFittingMethod(cp.dereference(value), argTypes)
        return compileCall(expr, method, args, callable)
    }

    private fun compileFunctionCallOnFieldRef(
        value: FieldRef,
        argTypes: List<Type>,
        expr: ASTNode.FunctionCall,
        args: List<Value>,
        callable: Value
    ): Value {
        val field = cp.dereference(value)
        val method = getBestFittingMethod(callOrInvokeMethods(field.type()), argTypes)
        return compileCall(expr, method, args, callable)
    }

    /** Pick the overload whose parameter types are closest to the actual arguments. */
    private fun getBestFittingMethod(
        methods: List<Method>,
        argTypes: List<Type>
    ): Method {
        val fitScores = methods.associateWith { it.getFitDegree(argTypes.toTypedArray()) }
        val minSum = fitScores.minOf { (_, score) -> score.first }

        return fitScores
            .filter { (_, score) -> score.first == minSum }
            .minBy { (_, score) -> score.second }
            .key
    }

    private fun compileMethodInvocation(
        method: Method,
        variable: Variable,
        args: List<Value>,
        callable: Value
    ): Value {
        val argTypes = args.map(Value::type)

        val typeParams = method.typeParameters()
        val typeArgsMap = mutableMapOf<String, MutableList<Int>>()

        method.parameters().forEachIndexed { i, it ->
            typeParams.find { tp ->
                tp.name() == (it.type() as? TemplateType)?.className()
            }?.let { p ->
                typeArgsMap.computeIfAbsent(p.name()) {
                    mutableListOf()
                } += i
            }
        }

        val typeArgs = typeArgsMap.map { (name, indices) ->
            TypeArgument.of(
                name,
                argTypes
                    .filterIndexed { index, _ -> index in indices }
                    .reduce(Type::lowestCommonAncestorWith)
            )
        }

        val method = method.withTypeArguments(typeArgs.toTypedArray())

        val value = cp.getReference(method.asGenericallyUntypedMember())
        variable.type = method.returnType()
        return if (method.isStatic && method.owner().attributes().contains<ExtensionAttribute>()) {
            val receiver = when (val producer = callable.producer) {
                is GetMethod -> producer.obj
                else -> error("Extension call requires bound receiver")
            }
            val receiverType = getRValueType(receiver)

            checkMethodSignature(method, listOf(receiverType) + argTypes)

            val call = Call(
                variable.reference,
                value,
                listOf(receiver) + args.refs()  // implicit `this` is first param
            )
            ir.emit(call, variable)
            variable
        } else if (method.isStatic) {
            checkMethodSignature(method, argTypes)

            val call = Call(
                variable.reference,
                value,
                args.refs()
            )
            ir.emit(call, variable)
            variable
        } else {
            val receiver = resolveMethodCallReceiver(callable)

            checkMethodSignature(method, argTypes)

            val call = if (method.isFinal || (callable.type.isFinal && callable.type.isPlainType)) {
                CallMethod(
                    variable.reference,
                    receiver,
                    value,
                    args.refs()
                )
            } else {
                CallVirtual(
                    variable.reference,
                    receiver,
                    value,
                    args.refs()
                )
            }

            ir.emit(call, variable)
            variable
        }
    }

    private fun getRValueType(receiver: RValue): Type = when (receiver) {
        is Reference.Named ->
            currentScope[receiver.name]!!.type

        is Reference.This -> thisType
        is Reference.Super -> thisType.superClass() ?: thisType.interfaces().first() // temp
        is FieldRef -> cp.dereference(receiver).type()
        is SingleMethodRef -> cp.dereference(receiver).getType()
        is ConstantPoolRef -> {
            when (val value = cp.dereference<Any>(receiver)) {
                is Int -> Types.INT
                is String -> Types.STRING
                is Boolean -> Types.BOOLEAN
                is Byte -> Types.BYTE
                is Short -> Types.SHORT
                is Char -> Types.CHAR
                is Float -> Types.FLOAT
                is Long -> Types.LONG
                is Double -> Types.DOUBLE
                else -> error("Couldn't infer type of $receiver")
            }
        }

        else -> error("Couldn't infer type of $receiver")
    }

    private fun checkMethodSignature(method: Method, argTypes: List<Type>) {
        val (sum, _) = method.getFitDegree(argTypes.toTypedArray())
        if (sum == Int.MAX_VALUE) {
            val expected = method.parameters().map(Parameter::type)
            error(buildString {
                append("Wrong argument types: got ")
                append(if (argTypes.isNotEmpty()) argTypes.joinToString(", ") else "none")
                append(" expected ")
                append(if (expected.isNotEmpty()) expected.joinToString(", ") else "none")
            })
        }
    }

    private fun resolveMethodCallReceiver(callable: Value): RValue =
        when (val producer = callable.producer) {
            is GetMethod -> producer.obj
            is GetMember -> producer.obj
            else -> when (val ref = callable.ref()) {
                is MethodGroupRef, is MemberGroupRef -> Reference.This
                else -> ref
            }
        }

    private fun compileCastExpression(expr: ASTNode.Cast): Value {
        val value = compileExpression(expr.expression)
        val ref = value.ref()
        return if (ref is Reference.Named) {
            val type = typeResolver.getType(expr.type)
            ir += Cast(ref, ref, cp.getReference(type))

            Value(ref, type)
        } else {
            val type = typeResolver.getType(expr.type)
            val typeRef = cp.getReference(type)
            val variable = createVariable(
                "cast#${hashStringOf(expr)}",
                type
            )
            val cast = Cast(
                variable.reference,
                ref,
                typeRef
            )
            ir.emit(cast, variable)

            variable
        }
    }

    private fun compileMemberAccess(expr: ASTNode.MemberAccess): Value {
        val owner = compileExpression(expr.expression) // this should be eliminated with DCE if members are static
        val suitingMembers = owner.type.members().filter { it.name() == expr.member } +
                typeResolver.filter { it.attributes().get<ExtensionAttribute>()?.type == owner.type }
                            .flatMap { it.members().toList() }
                            .filter { it.name() == expr.member }

        if (suitingMembers.size == 1) {
            return when (val member = suitingMembers[0]) {
                is Field -> {
                    if (member.isStatic) {
                        Value(
                            cp.getReference(member),
                            member.type()
                        )
                    } else {
                        val variable = createVariable(
                            "${member.name()}#${hashStringOf(expr)}",
                            member.type()
                        )
                        val getField = GetField(
                            variable.reference,
                            owner.ref(),
                            cp.getReference(member)
                        )
                        ir.emit(getField, variable)
                        variable
                    }
                }

                is Method -> {
                    val isExtension = member.owner().attributes().contains<ExtensionAttribute>()
                    if (member.isStatic && !isExtension) {
                        Value(
                            cp.getReference(member),
                            member.getType()
                        )
                    } else if (member.isStatic) {
                        val ref = cp.getReference(member)
                        Value(
                            ref,
                            member.getType(),
                            GetMethod(Reference.Empty, owner.ref(), ref)
                        )
                    } else {
                        val variable = createVariable(
                            "${member.name()}#${hashStringOf(expr)}",
                            member.getType()
                        )
                        val getMethod = GetMethod(
                            variable.reference,
                            owner.ref(),
                            cp.getReference(member)
                        )
                        ir.emit(getMethod, variable)
                        variable
                    }
                }

                else -> error("This is not a member")
            }
        }

        return if (suitingMembers.all { it.isStatic }) {
            val members = cp.getReference(suitingMembers)
            Value(
                members,
                UnionType.ofTypeModels(
                    suitingMembers
                        .map {
                            when (it) {
                                is Field -> it.type()
                                is Method -> it.getType()
                                else -> error("This is not a member")
                            }
                        }
                        .toSet().toTypedArray())
            )
        } else {
            val type = UnionType.ofTypeModels(
                suitingMembers
                    .map {
                        when (it) {
                            is Field -> it.type()
                            is Method -> it.getType()
                            else -> error("This is not a member")
                        }
                    }
                    .toSet().toTypedArray())

            val members = cp.getReference(suitingMembers)
            val variable = createVariable(
                "${expr.member}#${hashStringOf(expr)}",
                type
            )
            val getMember = GetMember(
                variable.reference,
                owner.ref(),
                members
            )
            ir.emit(getMember, variable)
            variable
        }
    }

    private fun compileIndexAccessExpression(expr: ASTNode.IndexAccess): Variable {
        val array = compileExpression(expr.expression)

        return if (array.type.isArray) {
            var type: Type = (array.type as ArrayType<*>)
            val iterator = expr.arguments.iterator()
            var arrValue = array.ref()
            val variable = createVariable(
                "array@ia#${hashStringOf(expr)}",
                type
            )
            do {
                val arg = iterator.next()

                val arrayLoad = ArrayLoad(
                    variable.reference,
                    arrValue,
                    compileExpression(arg).ref()
                )
                ir.emit(arrayLoad, variable)

                arrValue = variable.reference
                type = (type as ArrayType<*>).componentType() // this typecheck is safe
            } while (type is ArrayType<*> && iterator.hasNext())

            variable
        } else {
            val type = array.type.rawType.withTypeArguments(*array.type.typeArguments())
            val args = expr.arguments.map(::compileExpression)
            val argTypes = args.map(Value::type).toTypedArray()
            val method = type
                .findMethod("get", argTypes)
                .getOrElse {
                    error(
                        "Found no `get` method with given signature: get${
                            argTypes.joinToString(", ", "(", ")") { it.toUsageString() }
                        }")
                }
            val variable = createVariable(
                "get@ia#${hashStringOf(expr)}",
                method.returnType()
            )
            val callVirtual = CallVirtual(
                variable.reference,
                array.ref(),
                cp.getReference(method),
                args.refs()
            )
            ir.emit(callVirtual, variable)
            variable
        }
    }

    private fun compileIdentifierExpression(expr: ASTNode.IdentifierExpression): Value {
        val name = expr.identifier

        when {
            name == "this" -> return Value(Reference.This, thisType)
            name == "super" -> return Value(Reference.Super, thisType.superClass()!!)
            name in currentScope -> return currentScope[name]!!
        }

        typeResolver.getTypeOrNull(name)?.let { type ->
            return Value(cp.getReference(type), type)
        }

        thisType.fields().find { it.name() == name }?.let { field ->
            val fieldRef = cp.getReference(field)
            if (field.isStatic)
                return Value(fieldRef, field.type())

            markEnclosingLambdaAsInstance()
            val variable = createVariable("${field.name()}#${hashStringOf(expr)}", field.type())
            ir.emit(GetField(variable.reference, Reference.This, fieldRef), variable)
            return variable
        }

        if (thisType.findMethod(name).isPresent) {
            val methods = thisType.getMethods(name)
            if (methods.size == 1) {
                val m = methods[0]
                if (m.isStatic)
                    return Value(cp.getReference(m), m.getType())

                markEnclosingLambdaAsInstance()
                val variable = createVariable("${m.name()}#${hashStringOf(expr)}", m.getType())
                ir.emit(GetMethod(variable.reference, Reference.This, cp.getReference(m)), variable)
                return variable
            }

            if (methods.any { !it.isStatic })
                markEnclosingLambdaAsInstance()

            return Value(
                MethodGroupRef(methods.map { cp.getReference(it) }),
                UnionType.ofTypeModels(methods.map(Method::getType).toSet().toTypedArray()),
            )
        }

        if (name in importMap.members) {
            val members = importMap.members[name]!!
            if (members.all { it is Field }) {
                (members.first() as Field) // fields must have different names
                    .let {
                        if (it.isStatic) {
                            return Value(
                                cp.getReference(it),
                                it.type()
                            )
                        } else error("Cannot import non-static fields")
                    }
            }
            if (members.all { it is Method }) {
                val methods = members.map { it as Method }
                val qualifiedName = importMap[name]!!

                if (methods.size == 1) {
                    val m = methods[0]
                    if (m.isStatic)
                        return Value(cp.getReference(m), m.getType())

                    val boundField = resolveImportBoundField(qualifiedName, methods)
                        ?: error("Cannot import instance method `$name` without a static field receiver in the import path")

                    val methodRef = cp.getReference(m)
                    return Value(
                        methodRef,
                        m.getType(),
                        GetMethod(Reference.Empty, cp.getReference(boundField), methodRef),
                    )
                }

                val boundField = resolveImportBoundField(qualifiedName, methods)
                if (boundField != null) {
                    val methodRefs = cp.getReference(methods)
                    return Value(
                        methodRefs,
                        UnionType.ofTypeModels(methods.map(Method::getType).toSet().toTypedArray()),
                        GetMethod(Reference.Empty, cp.getReference(boundField), methodRefs),
                    )
                }

                if (methods.any { !it.isStatic })
                    error("Cannot import instance method `$name` without a static field receiver in the import path")

                return Value(
                    MethodGroupRef(methods.map { cp.getReference(it) }),
                    UnionType.ofTypeModels(methods.map(Method::getType).toSet().toTypedArray()),
                )
            }
        }

        error("Unknown identifier: $name")
    }

    private fun compileArrayExpression(expr: ASTNode.ArrayExpression): Variable {
        val expressions = expr.expressions?.map(::compileExpression)
        val elType =
            if (expressions != null)
                UnionType
                    .ofTypeModels(expressions.map(Value::type).toSet().toTypedArray())
                    .superClass()
            else
                Types.OBJECT

        val arr = createVariable(
            "array#${hashStringOf(expr)}",
            elType.asArray(),
        )
        val newArray = NewArray(
            arr.reference,
            cp.getReference(elType),
            cp.getConstant(expressions?.size ?: 0)
        )
        ir.emit(newArray, arr)

        expressions?.forEachIndexed { i, e ->
            ir += ArrayStore(
                arr.reference,
                cp.getConstant(i),
                e.ref()
            )
        }

        return arr
    }

    internal fun compileStatement(stmt: ASTNode.Statement) {
        when (stmt) {
            is ASTNode.If -> controlFlow.compileIfStatement(stmt)
            is ASTNode.Match -> controlFlow.compileMatchStatement(stmt)
            is ASTNode.Continue -> compileContinue()
            is ASTNode.Break -> compileBreak()
            is ASTNode.Loop -> compileLoop(stmt)
            is ASTNode.Return -> compileReturn(stmt)
            is ASTNode.While -> compileWhile(stmt)
            is ASTNode.DoWhile -> compileDoWhile(stmt)
            is ASTNode.Expression -> compileExpression(stmt)
            is ASTNode.VariableDeclaration -> compileVariableDeclaration(stmt)
            is ASTNode.Assignment -> compileAssignment(stmt)
            is ASTNode.For -> compileForStatement(stmt)
        }
    }

    private fun compileForStatement(stmt: ASTNode.For) {
        val iterable = compileExpression(stmt.expression)
        val type = iterable.type.rawType.withTypeArguments(*iterable.type.typeArguments())

        enterScope(
            newScope(
                "for#${hashStringOf(stmt)}",
                currentScope
            )
        )

        val method = type.findMethod("iterator", arrayOf<Type>())
        val iterator = method.map {
            val variable = createVariable(
                "call#${hashStringOf(stmt)}",
                it.returnType()
            )
            compileMethodInvocation(it, variable, listOf(), iterable)
        }.orElseGet {
            if (type.isSubclassOf(Type.ofClass(Iterator::class.javaObjectType)))
                iterable
            else {
                compileForOnArray(type, stmt, iterable)
                return@orElseGet null
            }
        } ?: return

        if (stmt.parameters.size > 1)
            error("Not implemented yet")

        val variableType = iterator.type.typeArguments()[0].bound()

        val forParam = stmt.parameters[0]
        val paramVariable = createVariable(
            forParam.identifier,
            forParam.type
                ?.let { typeResolver.getType(it) }
                ?: variableType
        )

        val startLabel = Label("block#${currentScope.name}")
        val endLabel = currentScope.endLabel

        val hasNext = createVariable(
            "hasNext#${hashStringOf(stmt)}",
            Types.BOOLEAN
        )

        ir += LabelInst(startLabel)

        compileMethodInvocation(
            iterator.type.findMethod("hasNext").orElseThrow(),
            hasNext,
            listOf(),
            iterator
        )

        ir += JumpIfN(hasNext.reference, endLabel)

        compileMethodInvocation(
            iterator.type.findMethod("next").orElseThrow(),
            paramVariable,
            listOf(),
            iterator
        )

        compileCodeBlock(stmt.block)
        ir += Jump(startLabel)
        exitScope()
    }

    private fun compileForOnArray(
        type: Type,
        stmt: ASTNode.For,
        iterable: Value
    ) {
        if (!type.isArray)
            error("type is not array")

        type as ArrayType<*>
        val forParam = stmt.parameters[0]

        val arrayLenField = type.findField("length").get()
        val arrayLenVariable = createVariable(
            "array_length#${hashStringOf(stmt)}",
            Types.INT
        )
        val getField = GetField(
            arrayLenVariable.reference,
            iterable.ref(),
            cp.getReference(arrayLenField)
        )
        ir.emit(getField, arrayLenVariable)

        val i = createVariable(
            "i#${hashStringOf(stmt)}",
            Types.INT
        )

        ir.emit(Move(i.reference, cp.getConstant(0)), i)

        val loopStartLabel = Label("block#${currentScope.name}")
        ir += LabelInst(loopStartLabel)

        val hasNext = createVariable(
            "hasNext#${hashStringOf(stmt)}",
            Types.BOOLEAN
        )

        val binaryOp = BinaryOp(
            hasNext.reference,
            i.reference,
            arrayLenVariable.reference,
            BinaryOperator.LT
        )
        ir.emit(binaryOp, hasNext)

        ir += JumpIfN(hasNext.reference, currentScope.endLabel)


        val paramVariable = createVariable(
            forParam.identifier,
            forParam.type
                ?.let { typeResolver.getType(it) }
                ?: type.componentType()
        )
        val arrayLoad = ArrayLoad(paramVariable.reference, iterable.ref(), i.ref())
        ir.emit(arrayLoad, paramVariable)

        compileCodeBlock(stmt.block)

        ir.emit(
            BinaryOp(
                i.reference,
                i.ref(),
                cp.getConstant(1),
                BinaryOperator.ADD
            ),
            i
        )
        ir += Jump(loopStartLabel)

        exitScope()
    }

    private fun compileVariableDeclaration(stmt: ASTNode.VariableDeclaration) {
        when (stmt) {
            is ASTNode.SingleTypedVariable -> {
                compileSingleVariable(stmt.name, stmt.type, stmt.defaultValue)
            }

            is ASTNode.SingleUntypedVariable -> {
                compileSingleVariable(stmt.name, null, stmt.defaultValue!!)
            }

            is ASTNode.MultiVariableDeclaration -> {
                stmt.variables.forEach {
                    compileSingleVariable(it.name, it.type, it.defaultValue)
                }
            }

            is ASTNode.MixedVariableDeclarations -> {
                stmt.declarations.forEach(::compileVariableDeclaration)
            }

            is ASTNode.UnpackingVariableDeclaration -> {
                val expr = compileExpression(stmt.expression)

                if (!expr.type.isArray) {
                    stmt.variables.forEachIndexed { i, pair ->
                        val (name, type) = pair

                        val field = expr.type.findField("component${i + 1}")
                            .orElseThrow()

                        val variable = createVariable(
                            name,
                            typeResolver.getTypeOrNull(type) ?: field.type()
                        )

                        if (field.isStatic) { // *very* unlikely but still possible
                            val move = Move(
                                variable.reference,
                                cp.getReference(field)
                            )
                            ir.emit(move, variable)
                        } else {
                            val getField = GetField(
                                variable.reference,
                                expr.ref(),
                                cp.getReference(field)
                            )
                            ir.emit(getField, variable)
                        }
                    }
                } else {
                    stmt.variables.forEachIndexed { i, pair ->
                        val (name, type) = pair

                        val variable = createVariable(
                            name,
                            typeResolver.getTypeOrNull(type) ?: (expr.type as ArrayType<*>).componentType()
                        )

                        ir.emit(
                            ArrayLoad(
                                variable.reference,
                                expr.ref(),
                                cp.getConstant(i)
                            ),
                            variable
                        )
                    }
                }
            }
        }
    }

    private fun compileSingleVariable(name: String, type: ASTNode.TypeExpr?, defaultExpr: ASTNode.Expression?) {
        val defaultValue = defaultExpr?.let(::compileExpression)

        when (defaultExpr) {
            is ASTNode.LambdaExpression -> {
                if (type != null) {
                    val method = cp.dereference(defaultValue!!.ref() as SingleMethodRef) as MutableMethod
                    val attr = method
                        .attributes.get<LambdaMethodAttribute>()!!
                    method.attributes.remove(attr)
                    method.attributes += LambdaMethodAttribute(typeResolver.getType(type))
                }
            }

            else -> {}
        }

        val variable = createVariable(
            name,
            type
                ?.let { typeResolver.getType(type) }
                ?: defaultValue?.type
                ?: error("Provide a value or type")
        )

        defaultValue?.let {
            ir += Move(variable.reference, it.ref())
        }
    }

    private fun compileAssignment(stmt: ASTNode.Assignment) {
        val leftExpr = stmt.lvalue
        if (leftExpr is ASTNode.IndexAccess) {
            compileIndexAccessAssignment(stmt, leftExpr)
            return
        }

        val lvalue = compileExpression(leftExpr)
        val left = lvalue.ref()

        val rvalue = when (val rExpr = stmt.expression) {
            is ASTNode.Match -> controlFlow.compileMatchExpression(rExpr)
            is ASTNode.If -> controlFlow.compileIfExpression(rExpr)
            else -> compileExpression(stmt.expression)
        }

        val right = rvalue.ref()

        if (left !is LValue)
            error("Cannot assign to rvalue")

        val producer = lvalue.producer
        if (producer is GetField) {
            val field = cp.dereference(producer.field)
            ir += if (field.isStatic)
                Move(left, right)
            else {
                PutField(
                    producer.obj,
                    producer.field,
                    right
                )
            }
            return
        }

        val variable = when (left) {
            is Reference.Named -> variable(left.name) ?: error("Variable not found: ${left.name}")
            Reference.Empty -> Value.DISCARD
            else -> error("Invalid lvalue")
        }

        val move = Move(variable.reference, right)
        ir.emit(move, variable)
    }

    private fun compileIndexAccessAssignment(
        stmt: ASTNode.Assignment,
        leftExpr: ASTNode.IndexAccess
    ) {
        val rvalue = compileExpression(stmt.expression)
        val right = rvalue.ref()
        val array = compileExpression(leftExpr.expression)

        if (array.type.isArray) {
            var type = (array.type as ArrayType<*>).componentType()
            var arrValue = array.ref()
            var variable = createVariable(
                "array@ia#${hashStringOf(leftExpr)}",
                type
            )
            val arguments = leftExpr.arguments
            for (i in 0..<arguments.size - 1) {
                if (type !is ArrayType<*>) break

                val arg = arguments[i]

                val arrayLoad = ArrayLoad(
                    variable.reference,
                    arrValue,
                    compileExpression(arg).ref()
                )
                ir.emit(arrayLoad, variable)

                arrValue = variable.reference
                type = type.componentType()
                variable = createVariable(
                    "array@ia#${hashStringOf(leftExpr)}",
                    type
                )
            }

            val arrayStore = ArrayStore(
                variable.reference,
                compileExpression(arguments.last()).ref(),
                right
            )
            ir += arrayStore
        } else {
            val type = array.type
            val args = leftExpr.arguments.map(::compileExpression) + rvalue
            val argTypes = args.map(Value::type).toTypedArray()
            val method = type
                .findMethod("set", argTypes)
                .getOrElse {
                    error(
                        "Found no `set` method with given signature: get${
                            argTypes.joinToString(", ", "(", ")") { it.toUsageString() }
                        }")
                }
            val callVirtual = CallVirtual(
                Value.DISCARD.reference,
                array.ref(),
                cp.getReference(method),
                args.refs(),
            )
            ir += callVirtual
        }
    }

    private fun compileBreak() {
        ir += Jump(currentScope.endLabel) // TODO: add break-return (break value)
    }

    private fun compileLoop(stmt: ASTNode.Loop) {
        enterScope(newScope("loop#${hashStringOf(stmt)}"))
        compileCodeBlock(stmt.block)
        exitScope()
    }

    private fun compileReturn(stmt: ASTNode.Return) {
        stmt.value
            ?.let {
                val expr = compileExpression(it)
                returnValue(expr)
            }
            ?: run { ir += Return() }
    }

    private fun returnValue(expr: Value) {
        if (this.method.attributes().contains<LambdaMethodAttribute>()) {
            val returnType = this.method.returnType()
            if (returnType != Types.VOID)
                (this.method as MutableMethod).returnType = returnType.lowestCommonAncestorWith(expr.type)
            else (this.method as MutableMethod).returnType = expr.type
        }

        ir += Return(expr.ref())
    }

    private fun compileWhile(stmt: ASTNode.While) {
        enterScope(newScope("while#${hashStringOf(stmt)}"))
        ir += JumpIfN(compileExpression(stmt.condition).ref(), currentScope.endLabel)
        compileCodeBlock(stmt.block)
        compileContinue()
        exitScope()
    }

    private fun compileContinue() {
        ir += Jump(currentScope.startLabel)
    }

    private fun compileDoWhile(stmt: ASTNode.DoWhile) {
        enterScope(newScope("while#${hashStringOf(stmt)}"))
        compileCodeBlock(stmt.block)
        ir += JumpIf(compileExpression(stmt.condition).ref(), currentScope.startLabel)
        exitScope()
    }

    private fun compileCall(
        expr: ASTNode.FunctionCall,
        method: Method,
        args: List<Value>,
        receiver: Value,
    ): Value {
        val variable = createVariable("call#${hashStringOf(expr)}", method.returnType())
        return compileMethodInvocation(method, variable, args, receiver)
    }

    private fun invokeFromMemberGroup(
        ref: MemberGroupRef,
        expr: ASTNode.FunctionCall,
        argTypes: List<Type>,
        args: List<Value>,
        receiver: Value,
    ): Value {
        val method = getBestFittingMethod(resolveMemberGroupCallables(ref), argTypes)
        return compileCall(expr, method, args, receiver)
    }

    private fun resolveMemberGroupCallables(ref: MemberGroupRef): List<Method> =
        cp.dereference<Member>(ref).flatMap { member ->
            when (member) {
                is Method -> listOf(member)
                is Field -> callOrInvokeMethods(member.type())
                else -> error("Unexpected member in group: $member")
            }
        }

    private fun callOrInvokeMethods(type: Type): List<Method> =
        (type.getMethods("call") + type.getMethods("invoke")).toList()

    private fun validateCallSignature(method: Method, argTypes: List<Type>) {
        val paramTypes = method.parameters().map(Parameter::type)
        val inheritanceDistances = paramTypes.zip(argTypes) { paramType, argType ->
            paramType.getInheritanceDistance(argType)
        }
        if (-1 in inheritanceDistances) {
            error(
                "No method found with given signature: " +
                    "${method.returnType().toUsageString()} ${method.name()}" +
                    argTypes.joinToString(", ", "(", ")") { it.toUsageString() },
            )
        }
    }

    private fun hashStringOf(node: Any): String =
        (node.hashCode() xor instructions.size).toHexString()

    private fun resolveImportBoundField(
        qualifiedName: ASTNode.QualifiedName,
        methods: List<Method>,
    ): Field? {
        if (methods.all { it.isStatic }) return null

        val identifiers = qualifiedName.identifiers
        if (identifiers.size < 3) return null

        val fieldName = identifiers[identifiers.size - 2]
        val ownerTypeIdentifiers = identifiers.dropLast(2)
        val receiverType = methods.first().owner()

        resolveOwnerType(ownerTypeIdentifiers)
            ?.findField(fieldName)
            ?.orElse(null)
            ?.takeIf { it.isStatic && it.type().fullName() == receiverType.fullName() }
            ?.let { return it }

        val ownerTypePath = ownerTypeIdentifiers.joinToString(".")
        return importMap.members.values
            .asSequence()
            .flatten()
            .filterIsInstance<Field>()
            .firstOrNull { field ->
                field.isStatic &&
                    field.name() == fieldName &&
                    field.owner().fullName() == ownerTypePath &&
                    field.type().fullName() == receiverType.fullName()
            }
    }

    private fun resolveOwnerType(identifiers: List<String>): Type? {
        if (identifiers.isEmpty()) return null

        val fullName = identifiers.joinToString(".")
        typeResolver.firstOrNull { it.fullName() == fullName }?.let { return it }
        importMap.types.values.firstOrNull { it.fullName() == fullName }?.let { return it }

        loadClass(identifiers)?.let { return Type.ofClass(it) }

        return typeResolver.getTypeOrNull(identifiers.last())
    }

    private fun loadClass(identifiers: List<String>): Class<*>? {
        if (identifiers.isEmpty()) return null

        loadClassOrNull(identifiers.joinToString("."))?.let { return it }

        if (identifiers.size < 2) return null

        for (splitAt in identifiers.lastIndex downTo 1) {
            val outer = identifiers.subList(0, splitAt).joinToString(".")
            val inner = identifiers.subList(splitAt, identifiers.size).joinToString("$")
            loadClassOrNull("$outer$$inner")?.let { return it }
        }

        return null
    }

    private fun loadClassOrNull(name: String): Class<*>? =
        try {
            Class.forName(name)
        } catch (_: ClassNotFoundException) {
            null
        }
}