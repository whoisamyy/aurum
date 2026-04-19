package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.model.fnType
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.model.*

class TypeGetter(
    availableTypes: Set<Type>
) {
    private val availableTypes: Map<String, Type> = availableTypes.associateBy(Type::fullName)
    constructor(pkg: Package) : this(pkg.types().toSet())
    constructor(typeGetter: TypeGetter, types: Set<Type>)
            : this((types + typeGetter.availableTypes.values).toSet())

    fun getTypeOrNull(typeExpr: ASTNode.TypeExpr): Type? {
        when (typeExpr) {
            is ASTNode.PlainType -> {
                val type = getTypeOrNull(typeExpr.name.toString()) ?: return null
                if (typeExpr.suffix == null) return type

                return type.asArray(typeExpr.suffix.size)
            }
            is ASTNode.ParenthesizedType -> {
                val type = getTypeOrNull(typeExpr.type) ?: return null
                if (typeExpr.suffix == null) return type

                return type.asArray(typeExpr.suffix.size)
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

                return type.withTypeArguments(typeArgs.toTypedArray())
            }
            is ASTNode.LambdaType -> {
                val paramTypes = typeExpr.paramTypes?.map(::getType) ?: emptyList()
                val returnType = getType(typeExpr.returnType)

                return fnType(returnType, paramTypes)
            }
            is ASTNode.IntersectionType -> {
                return IntersectionType.ofTypeModels(typeExpr.types.map(::getType).toTypedArray())
            }
            is ASTNode.UnionType -> {
                return UnionType.ofTypeModels(typeExpr.types.map(::getType).toTypedArray())
            }
        }
    }

    fun getType(typeExpr: ASTNode.TypeExpr): Type {
        return getTypeOrNull(typeExpr) ?: error("Type not found: $typeExpr")
    }

    fun getType(fullName: String): Type {
        return getTypeOrNull(fullName) ?: error("Type not found: $fullName")
    }

    fun getTypeOrNull(fullName: String): Type? {
        return availableTypes[fullName] ?: getPrimitiveTypeOrNull(fullName)
    }

    fun getPrimitiveTypeOrNull(name: String): PrimitiveType? {
        return when (name) {
            "void" -> Types.VOID
            "boolean" -> Types.BOOLEAN
            "byte" -> Types.BYTE
            "short" -> Types.SHORT
            "char" -> Types.CHAR
            "int" -> Types.INT
            "float" -> Types.FLOAT
            "long" -> Types.LONG
            "double" -> Types.DOUBLE
            else -> null
        }
    }

    fun getPrimitiveType(name: String): PrimitiveType {
        return getPrimitiveTypeOrNull(name) ?: error("$name is not a primitive type")
    }
}
