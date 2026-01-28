package lang.aurum.parsing.stages.memberresolution

import lang.aurum.model.TemplateType
import lang.aurum.model.Type
import lang.aurum.model.Types
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.model.*
import lang.aurum.parsing.stages.FileContext

class TypeResolver(val fileContext: FileContext) {
    lateinit var genericResolver: GenericResolver

    constructor(genericResolver: GenericResolver) : this(genericResolver.fileContext) {
        this.genericResolver = genericResolver
    }
    constructor(typeResolver: TypeResolver) : this(typeResolver.fileContext) {
        if (typeResolver::genericResolver.isInitialized)
            this.genericResolver = typeResolver.genericResolver
    }

    fun toUnresolvedType(ctx: AurumParser.GenericTypeContext): Type {
        return when (ctx) {
            is AurumParser.WildcardTypeContext -> {
                Types.OBJECT
            }
            is AurumParser.RegularTypeContext -> {
                // map of current class(method) type parameters so that i can create TemplateTypes to replace them // yea
                val type = toUnresolvedType(ctx.primaryType())
                if (type.isPrimitive)
                    return type
                if (ctx.typeArgList() == null)
                    return type

                val withTypeArguments = type.withTypeArguments(
                    ctx.typeArgList().typeExpr()
                        .map { toUnresolvedType(it) }
                        .toTypedArray()
                )
                withTypeArguments.toMutable()
            }
            else -> {
                Types.OBJECT
            }
        }
    }

    fun toUnresolvedType(ctx: AurumParser.TypeExprContext?): Type? {
        if (ctx == null) return null
        val baseType = toUnresolvedType(ctx.unionType())

        var arrayDimensions = 0
        if (ctx.typeSuffix() != null) {
            arrayDimensions = ctx.typeSuffix().text.count { it == '[' }
            if (arrayDimensions == 0)
                arrayDimensions = 1
        }
        return baseType.asArray(arrayDimensions)
    }

    fun toUnresolvedType(ctx: AurumParser.UnionTypeContext): Type {
        if (ctx.intersectionType().size == 1)
            return toUnresolvedType(ctx.intersectionType(0))

        return MutableUnionType(
            ctx.intersectionType().map { toUnresolvedType(it) }.toMutableList()
        )
    }

    fun toUnresolvedType(ctx: AurumParser.IntersectionTypeContext): Type {
        if (ctx.genericType().size == 1)
            return toUnresolvedType(ctx.genericType(0))

        return MutableIntersectionType(
            ctx.genericType().map { toUnresolvedType(it) }.toMutableList()
        )
    }

    fun toUnresolvedType(ctx: AurumParser.PrimaryTypeContext): Type {
        when (val child = ctx.getChild(0)) {
            is AurumParser.QualifiedNameContext -> {
                val unresolvedType = toUnresolvedType(child)
                if (unresolvedType is MutableType && unresolvedType.isPrimitive)
                    return unresolvedType.toImmutable()
                return unresolvedType
            }
            is AurumParser.TypeExprContext -> {
                return toUnresolvedType(child)!!
            }
            is AurumParser.LambdaTypeContext -> {
                val argTypes = child.typeList()?.typeExpr()?.map { toUnresolvedType(it)!! } ?: listOf()
                val returnType = toUnresolvedType(child.typeExpr())!!

                val fnType = fnType(returnType, argTypes)
                return fnType
            }
            else -> return Types.OBJECT.toMutable()
        }
    }

    fun toUnresolvedType(ctx: AurumParser.QualifiedNameContext): Type {
        val qName = ctx.Identifier().last().text
        val qPkg = ctx.Identifier().dropLast(1).joinToString(".") { it.text }
        val type: Type? = fileContext.importMap.get<Type>(qName) as Type?

        if (type != null)
            return type.toMutable()

        if (qName in genericResolver.typeParameters)
            return TemplateType.of(qName)

        return MutableTypePool.get(
            qName,
            qPkg.ifEmpty { fileContext.pkg },
            primitive = false
        )
    }
}
