package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.model.MutableType
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
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
    private val availableTypes: Set<Type>,
) {
    private val rootGetter = TypeGetter(availableTypes)

    fun processType(type: MutableType, declaration: ASTNode.TypeDeclaration): Type =
        when (declaration) {
            is ASTNode.ClassDeclaration -> processClass(type, declaration)
            is ASTNode.InterfaceDeclaration -> processInterface(type, declaration)
            is ASTNode.ExtensionDeclaration -> processExtension(type, declaration)
            is ASTNode.DecoratorDeclaration -> processDecorator(type, declaration)
        }

    fun processClass(type: MutableType, declaration: ASTNode.ClassDeclaration): Type {
        type.typeParameters = buildTypeParameters(declaration.typeParameters)
        val getter = getterWithTypeParameters(declaration.typeParameters)
        applyClassExtensions(type, declaration.extensions, getter)
        return type
    }

    fun processInterface(type: MutableType, declaration: ASTNode.InterfaceDeclaration): Type {
        type.typeParameters = buildTypeParameters(declaration.typeParameters)
        val getter = getterWithTypeParameters(declaration.typeParameters)
        type.interfaces.clear()
        declaration.extensions?.mapTo(type.interfaces) { getter.getType(it) }
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

    fun processDecorator(type: MutableType, @Suppress("UNUSED_PARAMETER") declaration: ASTNode.DecoratorDeclaration): Type {
        // Annotation-like declarations: no generic parameters in the current AST; members processed later.
        return type
    }

    private fun buildTypeParameters(params: List<ASTNode.TypeParam>?): MutableList<TypeParameter> {
        if (params.isNullOrEmpty()) return mutableListOf()
        val templateTypes = params.map { TemplateType.of(it.name) }.toSet()
        val getter = TypeGetter(availableTypes + templateTypes)
        return params
            .map { p ->
                val bound = p.bound?.let { getter.getType(it) } ?: Types.OBJECT
                TypeParameter.of(p.name, bound)
            }
            .toMutableList()
    }

    private fun getterWithTypeParameters(params: List<ASTNode.TypeParam>?): TypeGetter {
        val extra = params.orEmpty().map { TemplateType.of(it.name) }.toSet()
        return TypeGetter(availableTypes + extra)
    }

    private fun applyClassExtensions(
        type: MutableType,
        extensions: List<ASTNode.TypeExpr>?,
        getter: TypeGetter,
    ) {
        if (extensions.isNullOrEmpty()) {
            type.superClass = Types.OBJECT
            return
        }
        val resolved = extensions.map { getter.getType(it) }
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
