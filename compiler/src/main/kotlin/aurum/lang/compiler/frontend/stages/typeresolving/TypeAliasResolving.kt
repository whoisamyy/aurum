package aurum.lang.compiler.frontend.stages.typeresolving

import aurum.lang.compiler.frontend.stages.AurumFile
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.model.Type

/**
 * Collects `type Identifier = ...` aliases declared in [file] and resolves them against
 * [availableTypes] (and any [baseAliases], e.g. import aliases) into a flat
 * identifier-to-type map.
 *
 * Aliases may reference each other or previously-resolved aliases, so resolution
 * iterates to a fixed point: each pass resolves every alias whose target is already
 * resolvable. If a pass makes no progress, a remaining alias references a type that
 * cannot be resolved, and an error is reported.
 */
internal fun resolveTypeAliases(
    file: AurumFile,
    availableTypes: Set<Type>,
    baseAliases: Map<String, Type> = emptyMap()
): Map<String, Type> {
    val typeDefs = file.ast.filterIsInstance<ASTNode.TypeDef>()
    if (typeDefs.isEmpty()) return emptyMap()

    val aliasNames = typeDefs.map(ASTNode.TypeDef::identifier).toSet()

    val resolved = baseAliases.toMutableMap()
    val pending = typeDefs.associateBy(ASTNode.TypeDef::identifier).toMutableMap()

    while (pending.isNotEmpty()) {
        val resolver = SimpleTypeResolver(availableTypes + resolved.values, resolved)

        val resolvedNow = mutableMapOf<String, Type>()
        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            val (name, typeDef) = iterator.next()
            resolver.getTypeOrNull(typeDef.type)?.let { type ->
                resolvedNow[name] = type
                iterator.remove()
            }
        }

        if (resolvedNow.isEmpty()) {
            val (name, typeDef) = pending.entries.first()
            error("Type not found for alias '$name': ${typeDef.type}")
        }

        resolved.putAll(resolvedNow)
    }

    return resolved.filterKeys { it in aliasNames }
}
