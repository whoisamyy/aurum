package lang.aurum.parsing.stages.memberresolution

import lang.aurum.model.TemplateType
import lang.aurum.model.Type
import lang.aurum.model.TypeParameter
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.stages.FileContext

class GenericResolver (
    val fileContext: FileContext,
) {
    lateinit var typeResolver: TypeResolver

    val typeParameters = mutableMapOf<String, TemplateType>()

    constructor(genericResolver: GenericResolver) : this(genericResolver.fileContext) {
        this.typeResolver = if (genericResolver::typeResolver.isInitialized)
            genericResolver.typeResolver
        else throw IllegalStateException("todo")

        this.typeParameters += genericResolver.typeParameters
    }

    fun resolveGenericParameters(generics: AurumParser.GenericTypeListContext?): List<TypeParameter> {
        return generics?.genericType()?.map(this::resolveGenericParameter) ?: listOf()
    }

    fun resolveGenericParameter(generic: AurumParser.GenericTypeContext): TypeParameter {
        return when (generic) {
            is AurumParser.ParameterTypeContext -> {
                val typeParam = generic.typeParam()
                val bound = typeResolver.toUnresolvedType(typeParam.typeExpr())!!
                val name = typeParam.Identifier().text
                createTypeParameter(name, bound)
            }
            is AurumParser.RegularTypeContext -> {
                if (generic.typeArgList() != null)
                    throw IllegalStateException("todo")

                if (generic.primaryType().qualifiedName() == null)
                    throw IllegalStateException("todo")

                createTypeParameter(generic.primaryType().text)
            }
            is AurumParser.WildcardTypeContext -> throw IllegalStateException("todo")
            else -> throw IllegalStateException("todo")
        }
    }

    private fun createTypeParameter(name: String, bound: Type = Type.ofClass(Object::class.java)): TypeParameter {
        val tp = TypeParameter.of(name, bound)
        if (typeParameters.put(name, TemplateType.of(name)) != null) {
            throw IllegalStateException("todo") // type parameter with this name is already defined ...
        }

        return tp
    }
}