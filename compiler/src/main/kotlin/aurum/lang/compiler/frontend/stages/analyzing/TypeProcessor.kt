package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.model.MutableType
import aurum.lang.compiler.frontend.stages.TypeResolverFactory
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.compiler.frontend.stages.typeresolving.AbstractTypeResolver
import aurum.lang.model.TemplateType
import aurum.lang.model.Type
import aurum.lang.model.TypeParameter
import aurum.lang.model.Types
import aurum.lang.model.attribute.ExtensionAttribute

/**
 * Fills structural typing information on [MutableType] instances from their AST declarations:
 * type parameters, superclass / super-interfaces, and extension targets.
 */
class TypeProcessor(
    private val typeResolverFactory: TypeResolverFactory<*>,
    private val availableTypes: Set<Type>
) {
    private val rootGetter = typeResolverFactory(availableTypes)

    fun processType(type: MutableType, declaration: ASTNode.TypeDeclaration): Type =
        when (declaration) {
            is ASTNode.ClassDeclaration -> processClass(type, declaration)
            is ASTNode.InterfaceDeclaration -> processInterface(type, declaration)
            is ASTNode.ExtensionDeclaration -> processExtension(type, declaration)
            is ASTNode.DecoratorDeclaration -> processDecorator(type, declaration)
        }

    fun processClass(type: MutableType, declaration: ASTNode.ClassDeclaration): Type {
        type.typeParameters = buildTypeParameters(declaration.typeParameters)
        val typeResolver = typeResolverWithTypeParameters(declaration.typeParameters)
        applyClassExtensions(type, declaration.extensions, typeResolver)
        return type
    }

    fun processInterface(type: MutableType, declaration: ASTNode.InterfaceDeclaration): Type {
        type.typeParameters = buildTypeParameters(declaration.typeParameters)
        val typeResolver = typeResolverWithTypeParameters(declaration.typeParameters)
        type.interfaces.clear()
        declaration.extensions?.mapTo(type.interfaces) { typeResolver.getType(it) }
        return type
    }

    fun processExtension(type: MutableType, declaration: ASTNode.ExtensionDeclaration): Type {
        val extended = rootGetter.getType(declaration.type)
        val attr =
            object : ExtensionAttribute() {
                override fun values(): Map<String, Any?> = mutableMapOf()
            }
        attr.type = extended
        type.attributes += attr
        return type
    }

    fun processDecorator(type: MutableType, declaration: ASTNode.DecoratorDeclaration): Type {
        // Annotation-like declarations: no generic parameters in the current AST; members processed later.
        return type
    }

    private fun buildTypeParameters(params: List<ASTNode.TypeParam>?): MutableList<TypeParameter> {
        if (params.isNullOrEmpty()) return mutableListOf()
        val templateTypes = params.map { TemplateType.of(it.name) }.toSet()
        val typeResolver = typeResolverFactory(availableTypes + templateTypes)
        return params
            .map { p ->
                val bound = p.bound?.let { typeResolver.getType(it) } ?: Types.OBJECT
                TypeParameter.of(p.name, bound)
            }
            .toMutableList()
    }

    private fun typeResolverWithTypeParameters(params: List<ASTNode.TypeParam>?): AbstractTypeResolver {
        val extra = params.orEmpty().map { TemplateType.of(it.name) }.toSet()
        return typeResolverFactory(availableTypes + extra)
    }

    private fun applyClassExtensions(
        type: MutableType,
        extensions: List<ASTNode.TypeExpr>?,
        typeResolver: AbstractTypeResolver,
    ) {
        if (extensions.isNullOrEmpty()) {
            type.superClass = Types.OBJECT
            return
        }
        val resolved = extensions.map { typeResolver.getType(it) }
        val superIndex = resolved.indexOfFirst { !it.isInterface }
        if (superIndex < 0) {
            type.superClass = Types.OBJECT
            type.interfaces.clear()
            type.interfaces += resolved
        } else {
            type.superClass = resolved[superIndex]
            type.interfaces.clear()
            resolved.forEachIndexed { i, t ->
                if (i != superIndex) type.interfaces += t
            }
        }
    }
}
