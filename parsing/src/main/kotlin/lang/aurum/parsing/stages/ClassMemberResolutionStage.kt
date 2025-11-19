package lang.aurum.parsing.stages

import lang.aurum.model.*
import lang.aurum.model.impl.ParameterImpl
import lang.aurum.model.impl.TypeParameterImpl
import lang.aurum.model.impl.Utils
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.attribute.ExtensionAttributeImpl
import lang.aurum.parsing.model.*

class ClassMemberResolutionStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute() {
        parsingContext.files.forEach {
            it.classes.forEach { pair ->
                process(ClassMemberResolutionContext(it, pair.key to pair.value))
            }
            process(
                ClassMemberResolutionContext(
                    it,
                    it.fileClass to FileClassDeclCtx(it.ctx.declaration())
                )
            )
        }
    }

    private fun process(context: ClassMemberResolutionContext) {
        val type = context.typeAndCtx.first as MutableType
        val fileCtx = context.fileContext
        val declCtx = context.typeAndCtx.second
        when (val ctx = declCtx.ctx) {
            is AurumParser.ClassDeclContext -> {
                type.resolveGenerics(fileCtx, ctx.genericTypeList())

                type.interfaces = ctx.qualifiedNameList()?.qualifiedName()
                    ?.map { it.toUnresolvedType(fileCtx) }
                    ?.toMutableList() ?: mutableListOf()

                ctx.memberDecl().forEach {
                    when (val child = it.getChild(0)) {
                        is AurumParser.VarDeclContext -> type.resolveMember(fileCtx, child)
                        is AurumParser.FuncDeclContext -> type.resolveMember(fileCtx, child)
                        is AurumParser.OperatorDeclContext -> type.resolveMember(fileCtx, child)
                    }
                }
            }

            is AurumParser.InterfaceDeclContext -> {
                type.resolveGenerics(fileCtx, ctx.genericTypeList())

                type.interfaces = ctx.qualifiedNameList()?.qualifiedName()
                    ?.map { it.toUnresolvedType(fileCtx) }
                    ?.toMutableList() ?: mutableListOf()


                ctx.funcSign().forEach {
                    type.resolveMember(fileCtx, it)
                }
            }

            is AurumParser.DecoratorDeclContext -> {
                ctx.funcDecl().forEach {
                    type.resolveMember(fileCtx, it)
                }
            }

            is List<*> -> {
                ctx.forEach {
                    type.resolveMember(fileCtx, (it as AurumParser.DeclarationContext))
                }
            }

            is AurumParser.ExtensionDeclContext -> {
                if (type.attributes.any { it.values().containsKey("typeCtx") }) {
                    val extensionAttr = type.attributes.find { it.values().containsKey("typeCtx") } as ExtensionAttributeImpl
                    type.superClass = extensionAttr.typeCtx.toUnresolvedType(fileCtx)
                }

                val key = fileCtx.typeImportMap.filter { it.value == type.superClass }.keys.find { true }
                if (key != null)
                    fileCtx.typeImportMap[key] = type

                ctx.extensionMember().forEach {
                    type.resolveMember(fileCtx, it)
                }
            }
        }
    }
}

private val methodBlocks: MutableMap<MutableMethod, AurumParser.BlockContext?> = mutableMapOf()

val MutableMethod.block: AurumParser.BlockContext?
    get() {
        return methodBlocks[this]
    }

private fun MutableType.resolveGenerics(fileContext: FileContext, genericDecl: AurumParser.GenericTypeListContext?) {
    if (genericDecl == null)
        return

    this.typeArguments = this.withTypeArguments(genericDecl.genericType()?.map {
        it.toUnresolvedType(fileContext)
    }?.toTypedArray() ?: arrayOf()).typeArguments
}

private fun MutableMethod.resolveGenerics(fileContext: FileContext, genericDecl: AurumParser.GenericTypeListContext?) {
    if (genericDecl == null)
        return

    this.typeParameters = genericDecl.genericType()?.map {
        it.toTypeParameter(fileContext)
    }?.toMutableList()

    this.typeArguments = this.withTypeArguments(genericDecl.genericType()?.map {
        it.toUnresolvedType(fileContext)
    }?.toTypedArray() ?: arrayOf()).typeArguments
}

private fun getParameters(
    params: AurumParser.ParamListContext?,
    fileContext: FileContext
): MutableList<Parameter> = params?.param()
    ?.map {
        it.Identifier().text to it.typeExpr().toUnresolvedType(fileContext)
    }
    ?.map {
        ParameterImpl(
            it.first,
            it.second,
            Utils.EMPTY_ATTRIBUTES
        )
    }?.toMutableList() ?: mutableListOf()

fun MutableType.resolveMember(fileContext: FileContext, member: AurumParser.ExtensionMemberContext) {
    when (val member = member.getChild(0)) {
        is AurumParser.VarDeclContext -> resolveMember(fileContext, member)
        is AurumParser.FuncDeclContext -> resolveMember(fileContext, member)
        is AurumParser.OperatorDeclContext -> resolveMember(fileContext, member)
    }
}

fun MutableType.resolveMember(fileContext: FileContext, member: AurumParser.DeclarationContext) {
    when (val member = member.getChild(0)) {
        is AurumParser.TypeDefContext -> {
            fileContext.classes[member.typeExpr().toUnresolvedType(fileContext)] = TypeDefDeclCtx(member)
        }
        is AurumParser.FuncDeclContext -> this.resolveMember(fileContext, member)
        is AurumParser.VarDeclContext -> this.resolveMember(fileContext, member)
    }
}

fun MutableType.resolveMember(fileContext: FileContext, member: AurumParser.VarDeclContext) {
    when (member) {
        is AurumParser.SingleDeclContext -> resolveMember(fileContext, member)
        is AurumParser.UnpackDeclContext -> resolveMember(fileContext, member)
        is AurumParser.MultiDeclContext -> resolveMember(fileContext, member)
    }
}
fun MutableType.resolveMember(fileContext: FileContext, member: AurumParser.SingleDeclContext) {
    val accessFlags = member.modifier().toAccessFlags()
    val fieldName = member.Identifier().text
    val type: Type? = member.typeExpr()?.toUnresolvedType(fileContext)

    this.fields.add(MutableField(this, fieldName, type, accessFlags = accessFlags))
}
fun MutableType.resolveMember(fileContext: FileContext, member: AurumParser.UnpackDeclContext) {
    val accessFlags = member.modifier().toAccessFlags()
    val vars = member.varId().map { it.Identifier().text to it.typeExpr()?.toUnresolvedType(fileContext) }

    this.fields.addAll(
        vars.map { MutableField(
            this,
            it.first,
            it.second,
            accessFlags = accessFlags
        ) }
    )
}
fun MutableType.resolveMember(fileContext: FileContext, member: AurumParser.MultiDeclContext) {
    val accessFlags = member.modifier().toAccessFlags()
    val vars = member.varIdAssignment().map {
        val varId = it.varId()
        varId.Identifier().text to varId.typeExpr()?.toUnresolvedType(fileContext)
    }

    this.fields.addAll(
        vars.map { MutableField(
            this,
            it.first,
            it.second,
            accessFlags = accessFlags
        ) }
    )
}

fun MutableType.resolveMember(fileContext: FileContext, member: AurumParser.FuncSignContext): MutableMethod {
    val accessFlags = member.modifier().toAccessFlags()
    val name = member.Identifier().text
    val parameters: MutableList<Parameter> = getParameters(member.paramList(), fileContext)

    val returnType = member.returnType()?.typeExpr()?.toUnresolvedType(fileContext)
        ?: PrimitiveType.VOID

    val method = MutableMethod(
        this,
        name,
        returnType,
        parameters,
        accessFlags = accessFlags
    )
    method.resolveGenerics(fileContext, member.genericTypeList())
    this.methods.add(method)
    methodBlocks[method] = null
    return method
}

fun MutableType.resolveMember(fileContext: FileContext, member: AurumParser.FuncDeclContext): MutableMethod {
    val m = resolveMember(fileContext, member.funcSign())
    methodBlocks[m] = member.block()
    return m
}

fun MutableType.resolveMember(fileContext: FileContext, member: AurumParser.OperatorDeclContext): MutableMethod {
    val accessFlags = member.modifier().toAccessFlags()
    val name = member.OperatorSymbol().text
    val parameters = getParameters(member.paramList(), fileContext)
    val returnType = member.typeExpr().toUnresolvedType(fileContext)

    val method = MutableMethod(
        this,
        name,
        returnType,
        parameters,
        accessFlags = accessFlags
    )
    method.resolveGenerics(fileContext, member.genericTypeList())

    this.methods.add(method)
    methodBlocks[method] = member.block()
    return method
}

private fun AurumParser.GenericTypeContext.toUnresolvedType(fileContext: FileContext): Type {
    return when (this) {
        is AurumParser.WildcardTypeContext -> {
            Type.ofClass(Object::class.java)
        }
        is AurumParser.RegularTypeContext -> {
            val type = this.primaryType().toUnresolvedType(fileContext)
            if (type.isPrimitive)
                return type
            if (this.typeArgList() == null)
                return type

            val withTypeArguments = type.withTypeArguments(
                this.typeArgList().typeExpr()
                    .map { it.toUnresolvedType(fileContext) }
                    .toTypedArray()
            )
            withTypeArguments.toMutable()
        }
        else -> {
            Type.ofClass(Object::class.java)
        }
    }
}

private fun AurumParser.GenericTypeContext.toTypeParameter(fileContext: FileContext): TypeParameter {
    when (this) {
        is AurumParser.ParameterTypeContext -> {
            val bounds = this.typeParam().typeExpr().map { it.toUnresolvedType(fileContext) }
            val newBound: Type = if (bounds.size == 1)
                bounds[0]
            else
                IntersectionType.ofTypeModels(bounds.toTypedArray())
            return TypeParameterImpl(this.typeParam().Identifier().text, newBound)
        }
        is AurumParser.RegularTypeContext -> {
            val bound = Type.ofClass(Object::class.java)
            return TypeParameterImpl(this.primaryType().text, bound)
        }
        else -> throw IllegalStateException("Must be type parameter")
    }
}

private fun AurumParser.TypeExprContext.toUnresolvedType(fileContext: FileContext): Type {
    val baseType = this.unionType().toUnresolvedType(fileContext)

    var arrayDimensions = 0
    if (this.typeSuffix() != null) {
        arrayDimensions = this.typeSuffix().text.count { it == '[' }
        if (arrayDimensions == 0)
            arrayDimensions = 1
    }
    return baseType.asArray(arrayDimensions)
}
private fun AurumParser.UnionTypeContext.toUnresolvedType(fileContext: FileContext): Type {
    if (this.intersectionType().size == 1)
        return this.intersectionType(0).toUnresolvedType(fileContext)

    return MutableUnionType(
        this.intersectionType().map { it.toUnresolvedType(fileContext) }.toMutableList()
    )
}
private fun AurumParser.IntersectionTypeContext.toUnresolvedType(fileContext: FileContext): Type {
    if (this.genericType().size == 1)
        return this.genericType(0).toUnresolvedType(fileContext)

    return MutableIntersectionType(
        this.genericType().map { it.toUnresolvedType(fileContext) }.toMutableList()
    )
}
private fun AurumParser.PrimaryTypeContext.toUnresolvedType(fileContext: FileContext): Type {
    when (val child = this.getChild(0)) {
        is AurumParser.QualifiedNameContext -> {
            val unresolvedType = child.toUnresolvedType(fileContext)
            if (unresolvedType.isPrimitive)
                return unresolvedType.toImmutable()
            return unresolvedType
        }
        is AurumParser.TypeExprContext -> {
            return child.toUnresolvedType(fileContext)
        }
        is AurumParser.LambdaTypeContext -> {
            val argTypes = child.typeList()?.typeExpr()?.map { it.toUnresolvedType(fileContext) } ?: listOf()
            val returnType = child.typeExpr().toUnresolvedType(fileContext)

            val fnType = fnType(returnType, argTypes)
            return fnType // todo: replace with Type.ofClass(aurum.lang.Fn::class.java)
        }
        else -> return Type.ofClass(Object::class.java).toMutable()
    }
}

private fun AurumParser.QualifiedNameContext.toUnresolvedType(
    fileContext: FileContext
): MutableType {
    val qName = this.Identifier().last().text
    val qPkg = this.Identifier().dropLast(1).joinToString(".") { it.text }
    val type = fileContext.typeImportMap[qName]

    if (type != null)
        return type.toMutable()

    return MutableTypePool.get(
        qName,
        qPkg.ifEmpty { fileContext.pkg },
        primitive = false
    )
}

data class ClassMemberResolutionContext (
    val fileContext: FileContext,
    val typeAndCtx: Pair<Type, TypeDeclCtx<*>>,
) : AbstractParsingContext()