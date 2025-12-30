package lang.aurum.parsing.stages.memberresolution

import lang.aurum.model.TemplateType
import lang.aurum.model.Type
import lang.aurum.model.TypeParameter
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.stages.FileContext
import lang.aurum.parsing.stages.coderesolution.positionString
import lang.aurum.parsing.throwAurumError
import org.antlr.v4.runtime.ParserRuleContext

class GenericResolver (
    val fileContext: FileContext,
) {
    lateinit var typeResolver: TypeResolver

    val typeParameters = mutableMapOf<String, TemplateType>()

    @Suppress("USELESS_CAST")
    constructor(genericResolver: GenericResolver) : this(genericResolver.fileContext) {
        this.typeResolver = if (genericResolver::typeResolver.isInitialized)
            genericResolver.typeResolver
        else throwAurumError("TypeResolver must be initialized before creating GenericResolver copy", null as? ParserRuleContext, fileContext)

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
                createTypeParameter(name, bound, typeParam.positionString)
            }
            is AurumParser.RegularTypeContext -> {
                if (generic.typeArgList() != null)
                    throwAurumError("Type arguments are not allowed in type parameter declaration", generic, fileContext)

                if (generic.primaryType().qualifiedName() == null)
                    throwAurumError("Type parameter must be a qualified name", generic, fileContext)

                createTypeParameter(generic.primaryType().text, positionString = generic.primaryType().positionString)
            }
            is AurumParser.WildcardTypeContext -> throwAurumError("Wildcard type '?' cannot be used as a type parameter", generic, fileContext)
            else -> throwAurumError("Unsupported generic type context: ${generic.javaClass.simpleName}", generic, fileContext)
        }
    }

    private fun createTypeParameter(name: String, bound: Type = Type.ofClass(Object::class.java), positionString: String): TypeParameter {
        val tp = TypeParameter.of(name, bound)
        if (typeParameters.put(name, TemplateType.of(name)) != null) {
            throwAurumError("Type parameter '$name' is already defined", positionString, fileContext)
        }

        return tp
    }
}