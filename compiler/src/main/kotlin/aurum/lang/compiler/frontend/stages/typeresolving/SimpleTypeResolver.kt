package aurum.lang.compiler.frontend.stages.typeresolving

import aurum.lang.compiler.frontend.model.fnType
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.model.*
import java.util.function.IntFunction

open class SimpleTypeResolver(
    availableTypes: Set<Type>,
    aliases: Map<String, Type> = emptyMap()
) : AbstractTypeResolver(availableTypes, aliases) {
    constructor(pkg: Package) : this(pkg.types().toSet())

    constructor(parentLinker: AbstractTypeResolver, types: Set<Type>)
            : this((types + parentLinker.availableTypes.values).toSet(), parentLinker.aliases)

    constructor(parentLinker: AbstractTypeResolver, types: Set<Type>, aliases: Map<String, Type>)
            : this((types + parentLinker.availableTypes.values).toSet(), parentLinker.aliases + aliases)


    override fun getTypeOrNull(typeExpr: ASTNode.TypeExpr?): Type? {
        return when (typeExpr) {
            is ASTNode.PlainType -> {
                val type = getTypeOrNull(typeExpr.name.toString()) ?: return null
                if (typeExpr.suffix == null) return type

                type.asArray(typeExpr.suffix.size)
            }
            is ASTNode.ParenthesizedType -> {
                val type = getTypeOrNull(typeExpr.type) ?: return null
                if (typeExpr.suffix == null) return type

                type.asArray(typeExpr.suffix.size)
            }
            is ASTNode.ParametrizedType -> {
                val type = getTypeOrNull(typeExpr.name.toString()) ?: return null
                val typeArgs = typeExpr.typeArgs.zip(type.typeParameters()).map { (it, param) ->
                    when (it) {
                        is ASTNode.TypeArg.Wildcard -> {
                            val bound =
                                if (it.extends == null) param.bound()
                                else getType(it.extends)

                            TypeArgument.of(param.name(), bound)
                        }
                        is ASTNode.TypeExpr -> TypeArgument.of(param.name(), getType(it))
                    }
                }

                type.withTypeArguments(*typeArgs.toTypedArray())
            }
            is ASTNode.LambdaType -> {
                val paramTypes = typeExpr.paramTypes?.map(::getType) ?: emptyList()
                val returnType = getType(typeExpr.returnType)

                fnType(returnType, paramTypes)
            }
            is ASTNode.IntersectionType -> {
                IntersectionType.ofTypeModels(typeExpr.types.map(::getType).toTypedArray())
            }
            is ASTNode.UnionType -> {
                UnionType.ofTypeModels(typeExpr.types.map(::getType).toTypedArray())
            }
            else -> null
        }
    }

    override fun getTypeOrNull(fullName: String?): Type? {
        return availableTypes[fullName] ?: aliases[fullName] ?: getPrimitiveTypeOrNull(fullName)
    }

    @Suppress("UNCHECKED_CAST", "OVERRIDE_DEPRECATION")
    override fun <T> toArray(generator: IntFunction<Array<out T?>?>): Array<out T?>? {
        return availableTypes.values.toTypedArray() as Array<out T?>?
    }
}
