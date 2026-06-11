package aurum.lang.compiler.frontend.stages.linking

import aurum.lang.compiler.frontend.stages.analyzing.ImportMap
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.model.Field
import aurum.lang.model.Member
import aurum.lang.model.Method
import aurum.lang.model.Type

abstract class AbstractLinker {
    @Suppress("UNCHECKED_CAST")
    fun link(importMap: ImportMap) {
        importMap.types = mutableMapOf()
        importMap.members = mutableMapOf()

        val linkedSymbols = importMap.mapValues { (_, qualifiedName) ->
            linkSymbol(qualifiedName)
        }

        importMap.members.putAll(
            linkedSymbols
                .filterValues { it is List<*> } as Map<String, List<Member>>
        )

        importMap.members.putAll(
            linkedSymbols
                .filterValues { it is Field }
                .mapValues { (_, v) -> listOf(v) } as Map<String, List<Member>>
        )

        importMap.types.putAll(
            linkedSymbols
                .filterValues { it is Type } as Map<String, Type>
        )
    }

    abstract fun linkTypeOrNull(qualifiedName: ASTNode.QualifiedName?): Type?
    abstract fun linkMethodOrNull(qualifiedName: ASTNode.QualifiedName?): List<Method>?
    abstract fun linkFieldOrNull(qualifiedName: ASTNode.QualifiedName?): Field?
    fun linkSymbolOrNull(qualifiedName: ASTNode.QualifiedName?): Any? {
        return linkTypeOrNull(qualifiedName)
            ?: linkMethodOrNull(qualifiedName)
            ?: linkFieldOrNull(qualifiedName)
    }

    fun linkType(qualifiedName: ASTNode.QualifiedName?): Type {
        return linkTypeOrNull(qualifiedName) ?: error("Didn't find anything for $qualifiedName")
    }
    fun linkMethod(qualifiedName: ASTNode.QualifiedName?): List<Method> {
        return linkMethodOrNull(qualifiedName) ?: error("Didn't find anything for $qualifiedName")
    }
    fun linkField(qualifiedName: ASTNode.QualifiedName?): Field {
        return linkFieldOrNull(qualifiedName) ?: error("Didn't find anything for $qualifiedName")
    }
    fun linkSymbol(qualifiedName: ASTNode.QualifiedName?): Any {
        return linkSymbolOrNull(qualifiedName) ?: error("Didn't find anything for $qualifiedName")
    }
}