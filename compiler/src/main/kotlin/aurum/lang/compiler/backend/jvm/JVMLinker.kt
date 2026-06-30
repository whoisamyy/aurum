package aurum.lang.compiler.backend.jvm

import aurum.lang.compiler.frontend.stages.linking.AbstractLinker
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.model.Field
import aurum.lang.model.Method
import aurum.lang.model.Type
import aurum.lang.model.Types
import kotlin.jvm.optionals.getOrNull

class JVMLinker : AbstractLinker() {
    override fun linkTypeOrNull(qualifiedName: ASTNode.QualifiedName?): Type? {
        if (qualifiedName == null) return null

        val clazz = loadClass(qualifiedName.identifiers) ?: return null
        return typeOf(clazz)
    }

    override fun linkMethodOrNull(qualifiedName: ASTNode.QualifiedName?): List<Method>? {
        if (qualifiedName == null || qualifiedName.identifiers.size < 2) return null

        val identifiers = qualifiedName.identifiers
        val methodName = identifiers.last()
        val ownerType = getOwnerType(identifiers) ?: return null

        val methods = ownerType.getMethods(methodName)
        return methods.takeIf { it.isNotEmpty() }?.toList()
    }

    private fun getOwnerType(identifiers: List<String>): Type? {
        val loadedClass = loadClass(identifiers.dropLast(1))
        val ownerType = if (loadedClass != null)
            typeOf(loadedClass)
        else typeOf(loadClass(identifiers.dropLast(2)) ?: return null)
            .findField(identifiers.reversed()[1])
            .filter(Field::isStatic)
            .map(Field::type)
            .getOrNull()
            ?: return null
        return ownerType
    }

    override fun linkFieldOrNull(qualifiedName: ASTNode.QualifiedName?): Field? {
        if (qualifiedName == null || qualifiedName.identifiers.size < 2) return null

        val identifiers = qualifiedName.identifiers
        val fieldName = identifiers.last()
        val ownerType = getOwnerType(identifiers) ?: return null

        return ownerType.findField(fieldName).orElse(null)
    }

    private fun typeOf(clazz: Class<*>): Type {
        if (!clazz.isArray) {
            when (clazz) {
                Void.TYPE -> return Types.VOID
                java.lang.Boolean.TYPE -> return Types.BOOLEAN
                java.lang.Byte.TYPE -> return Types.BYTE
                java.lang.Short.TYPE -> return Types.SHORT
                Character.TYPE -> return Types.CHAR
                Integer.TYPE -> return Types.INT
                java.lang.Float.TYPE -> return Types.FLOAT
                java.lang.Long.TYPE -> return Types.LONG
                java.lang.Double.TYPE -> return Types.DOUBLE
                java.lang.String::class.java -> return Types.STRING
                Object::class.java -> return Types.OBJECT
            }
        }
        return Type.ofClass(clazz)
    }

    private fun loadClass(identifiers: List<String>): Class<*>? {
        if (identifiers.isEmpty()) return null

        loadClassOrNull(identifiers.joinToString("."))?.let { return it }

        if (identifiers.size < 2) return null

        for (splitAt in identifiers.lastIndex downTo 1) {
            val outer = identifiers.subList(0, splitAt).joinToString(".")
            val inner = identifiers.subList(splitAt, identifiers.size).joinToString("$")
            loadClassOrNull("$outer$$inner")?.let { return it }
        }

        return null
    }

    private fun loadClassOrNull(name: String): Class<*>? =
        try {
            Class.forName(name)
        } catch (_: ClassNotFoundException) {
            null
        }
}
