package lang.aurum.model.util

import lang.aurum.model.Type
import lang.aurum.util.MutableBiMap
import lang.aurum.util.mutableBiMapOf

object ParametrizedTypePool {
    private val pool: MutableBiMap<Type, MutableSet<Type>> = mutableBiMapOf()

    @JvmStatic
    fun getParametrizedTypes(type: Type): Set<Type> {
        if (type.typeArguments().isNotEmpty())
            return pool.computeIfAbsent(type.withDefaultTypeArguments()) { mutableSetOf(type) }
        return pool.computeIfAbsent(type.withDefaultTypeArguments()) { mutableSetOf() }
    }

    @JvmStatic
    fun getBaseType(type: Type): Type {
        if (type.typeArguments().isEmpty())
            return type

        return pool.inverse.filter { (set, _) -> type in set }.values.first()
    }

    @JvmStatic
    fun addType(base: Type, parametrized: Type) {
        pool.computeIfAbsent(base) { mutableSetOf() }.add(parametrized)
    }

    @JvmStatic
    fun addTypes(base: Type, parametrized: Iterable<Type>) {
        pool.computeIfAbsent(base) { mutableSetOf() }.addAll(parametrized)
    }
}