package lang.aurum.parsing.stages.coderesolution

import lang.aurum.attribute.ExtensionAttribute
import lang.aurum.ir.*
import lang.aurum.model.*
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.attribute.get
import lang.aurum.parsing.model.fnType
import lang.aurum.parsing.stages.FileContext
import lang.aurum.parsing.throwAurumError
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

val ParserRuleContext.positionString: String
    get() {
        val token = this.start
        return "${token.line}:${token.charPositionInLine}"
    }

val TerminalNode.positionString: String
    get() {
        val token = this.symbol
        return "${token.line}:${token.charPositionInLine}"
    }


class IRCompiler(
    val fileContext: FileContext,
    val method: Method
) {
    val instructions: MutableList<Instruction> = mutableListOf()
    val constantPool = fileContext.constantPool

    val dataTracker = DataTracker(this)
    val generator = InstructionGenerator(this)
    val expressionProcessor: ExpressionProcessor = ExpressionProcessor(this, method)
    val statementProcessor: StatementProcessor = StatementProcessor(this, method)

    var currentScope: AbstractScope = Scope(method.name())

    init {
        currentScope += method.parameters().map { Variable(it.name(), it.type()) }
    }

    fun getLValue(qualifiedName: AurumParser.QualifiedNameContext): Pair<RValue, LValue> {
        val identifier = qualifiedName.Identifier(0)
        var value = expressionProcessor.processStringIdentifier(identifier.text, identifier.positionString)
        if (qualifiedName.Identifier().size == 1)
            return NullRef to if (value.value is LValue) value.value else throwAurumError("Expected lvalue but got rvalue: ${value.value}", qualifiedName, fileContext)
        for (i in qualifiedName.Identifier().dropLast(1)) {
            value = if (i.text == "this")
                Value(
                    method.owner(),
                    Reference.This
                )
            else
                expressionProcessor.processMemberAccess(
                    value,
                    i.text,
                    i.positionString
                )
        }
        val rval = value.value
        val lastIdentifier = qualifiedName.Identifier().last()
        val field = value.type.findField(lastIdentifier.text)
        if (field.isPresent) {
            return rval to constantPool.getReference(field.get())
        }
        val lval = expressionProcessor.processMemberAccess(
            value,
            lastIdentifier.text,
            lastIdentifier.positionString
        )
        return rval to (lval.value as? LValue ?: throwAurumError("Expected lvalue but got rvalue: ${lval.value}", lastIdentifier, fileContext))
//
//        val text = qualifiedName.text
//        if (text in currentScope) {
//            val variable = currentScope[text]!!
//            variable.assignments++
//            return variable.toLValue()
//        }
//
//        val predicate: (Map.Entry<String, Field>) -> Boolean = { (_, f) ->
//            text == "${f.owner().fullName()}.${f.name()}"
//                    || text == "${f.owner().className()}.${f.name()}"
//        }
//        val fieldMap = fileContext.importMap.fieldMap
//        if (text in fieldMap) {
//            val field = fileContext.importMap.get<Field>(text)!!
//            return constantPool.getReference(field)
//        } else if (fieldMap.any(predicate)) {
//            val field = fieldMap.filter(predicate).values.first()
//            return constantPool.getReference(field)
//        }
//
//        val optionalField = method.owner().findField(text)
//        if (optionalField.isPresent) {
//            return constantPool.getReference(optionalField.get())
//        }
//
//        throw IllegalStateException("todo")
    }

    fun process(statement: AurumParser.StatementContext) {
        startScope(currentScope)
        statementProcessor.processStatement(statement)
        endScope()
    }

    fun process(expression: AurumParser.ExpressionContext) {
//        startScope(currentScope)
        expressionProcessor.processExpression(expression)
//        endScope()
    }

    fun process(block: AurumParser.BlockContext) {
//        startScope(currentScope)
        block.statement()?.forEach {
            statementProcessor.processStatement(it)
        }
//        endScope()
    }

    fun process(block: AurumParser.ExpressionBlockContext) {
//        startScope(currentScope)
        block.statement()?.forEach {
            statementProcessor.processStatement(it)
        }
        expressionProcessor.processExpression(block.expression())
//        endScope()
    }

    fun startScope(scopeName: String) {
        startScope(Scope(scopeName, currentScope))
    }

    fun startScope(scope: AbstractScope) {
        currentScope = scope
        generator.label(currentScope.startLabel)
    }

    fun endScope() {
        generator.label(currentScope.endLabel)
        currentScope = currentScope.parentScope ?: return
    }

    fun setVariable(variable: Variable, value: RValue) {
        generator.move(variable.toLValue(), value)
    }
    fun setVariable(variable: Variable, value: Value) {
        variable.type = value.type
        setVariable(variable, value.value)
    }

    fun setVariableIndexed(variable: Variable, index: RValue, value: RValue) {
        generator.arrayStore(variable.toReference(), index, value)
    }
    fun setVariableIndexed(variable: Variable, index: Value, value: Value) = setVariableIndexed(variable, index.value, value.value)
    fun setVariableIndexed(variable: Variable, index: Value, value: RValue) = setVariableIndexed(variable, index.value, value)
    fun setVariableIndexed(variable: Variable, index: RValue, value: Value) = setVariableIndexed(variable, index, value.value)

    fun toType(ctx: AurumParser.TypeExprContext?): Type {
        if (ctx == null) return Types.OBJECT
        var type = toType(ctx.unionType())

        val extensionAttribute = type.attributes().get<ExtensionAttribute>()
        if (extensionAttribute != null) {
            type = extensionAttribute.type
        }

        ctx.typeSuffix()?.text?.count { it == '[' }?.let {
            return type.asArray(it)
        }

        if (ctx.typeSuffix()?.text == "...")
            return type.asArray(1)

        return type
    }

    fun toType(ctx: AurumParser.UnionTypeContext): Type {
        if (ctx.intersectionType().size == 1)
            return toType(ctx.intersectionType(0))

        return UnionType.ofTypeModels(ctx.intersectionType().map(::toType).toTypedArray())
    }

    fun toType(ctx: AurumParser.IntersectionTypeContext): Type {
        if (ctx.genericType().size == 1)
            return toType(ctx.genericType(0))

        return IntersectionType.ofTypeModels(ctx.genericType().map(::toType).toTypedArray())
    }

    fun toType(ctx: AurumParser.GenericTypeContext): Type {
        return when (ctx) {
            is AurumParser.ParameterTypeContext -> {
                getType(ctx.typeParam().Identifier().text)
                    ?: throw IllegalStateException("Type not found")
            }
            is AurumParser.RegularTypeContext -> {
                val type = toType(ctx.primaryType())
                ctx.typeArgList()?.typeExpr()?.map(::toType)?.let {
                    return type.withTypeArguments(it.toTypedArray())
                }
                type
            }
            is AurumParser.WildcardTypeContext -> {
                throw IllegalStateException("Cannot use wildcard '?' as type")
            }

            else -> throw IllegalStateException()
        }
    }

    fun toType(ctx: AurumParser.PrimaryTypeContext): Type {
        return when {
            ctx.qualifiedName() != null -> {
                val typeName = ctx.qualifiedName().Identifier(0).text
                return getType(typeName)!!
            }
            ctx.lambdaType() != null -> {
                val ctx = ctx.lambdaType()
                return fnType(
                    toType(ctx.typeExpr()),
                    ctx.typeList()?.typeExpr()?.map(::toType) ?: listOf()
                )
            }
            ctx.typeExpr() != null -> {
                toType(ctx.typeExpr())
            }

            else -> throw IllegalStateException()
        }
    }

    private fun getType(typeName: String): Type? {
        return when {
            typeName in fileContext.typeDefs ->
                fileContext.typeDefs[typeName]

            typeName in fileContext.importMap.typeMap ->
                fileContext.importMap.typeMap[typeName]

            fileContext.classes.keys.any { it.className() == typeName } ->
                fileContext.classes.keys.find { it.className() == typeName }

            else -> throw IllegalStateException()
        }
    }

    val availableMethods: List<Method>
        get() {
            val list = mutableListOf<Method>()
            list += fileContext.importMap.methodMap.values.flatMap { it }
            fileContext.importMap.typeMap.values.flatMapTo(list) {
                it.methods().toList()
            }

            fileContext.classes.keys.flatMapTo(list) {
                it.methods().toList()
            }

            return list.toSet().toList()
        }
}