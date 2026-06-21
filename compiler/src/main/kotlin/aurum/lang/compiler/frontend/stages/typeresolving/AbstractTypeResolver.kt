package aurum.lang.compiler.frontend.stages.typeresolving

import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.model.PrimitiveType
import aurum.lang.model.Type
import aurum.lang.model.Types

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
abstract class AbstractTypeResolver(
    availableTypes: Set<Type>,
    val aliases: Map<String, Type> = emptyMap()
) : Set<Type> by availableTypes {
    companion object {
        val defaultTypes = mapOf(
            "string" to Types.STRING,
            "object" to Types.OBJECT
        )
    }

    constructor(parentLinker: AbstractTypeResolver, types: Set<Type>)
            : this((types + parentLinker.availableTypes.values).toSet(), parentLinker.aliases)

    constructor(parentLinker: AbstractTypeResolver, types: Set<Type>, aliases: Map<String, Type>)
            : this((types + parentLinker.availableTypes.values).toSet(), parentLinker.aliases + aliases)

    val availableTypes: Map<String, Type> =
        availableTypes.associateBy(Type::className)
                      .filterKeys { it !in aliases } +
                            aliases +
                            defaultTypes

    abstract fun getTypeOrNull(typeExpr: ASTNode.TypeExpr?): Type?
    abstract fun getTypeOrNull(fullName: String?): Type?

    fun getType(typeExpr: ASTNode.TypeExpr): Type {
        return getTypeOrNull(typeExpr) ?: error("Type not found: $typeExpr")
    }

    fun getType(fullName: String): Type {
        return getTypeOrNull(fullName) ?: error("Type not found: $fullName")
    }

    fun getPrimitiveType(name: String): PrimitiveType {
        return getPrimitiveTypeOrNull(name) ?: error("$name is not a primitive type")
    }

    fun getPrimitiveTypeOrNull(name: String?): PrimitiveType? {
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

    operator fun get(name: String): Type? {
        return getTypeOrNull(name)
    }

    operator fun get(type: ASTNode.TypeExpr): Type? {
        return getTypeOrNull(type)
    }
}