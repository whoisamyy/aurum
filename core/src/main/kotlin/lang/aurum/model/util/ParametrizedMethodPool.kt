package lang.aurum.model.util

import lang.aurum.model.Method
import lang.aurum.util.MutableBiMap
import lang.aurum.util.mutableBiMapOf

object ParametrizedMethodPool {
    private val pool: MutableBiMap<Method, MutableSet<Method>> = mutableBiMapOf()

    @JvmStatic
    fun getParametrizedMethods(method: Method): Set<Method> {
        if (method.typeArguments().isNotEmpty())
            return pool.computeIfAbsent(method.withDefaultTypeArguments()) { mutableSetOf(method) }
        return pool.computeIfAbsent(method.withDefaultTypeArguments()) { mutableSetOf() }
    }

    @JvmStatic
    fun getBaseMethod(method: Method): Method {
        if (method.typeArguments().isEmpty())
            return method

        return pool.inverse.filter { (set, _) -> method in set }.values.first()
    }

    @JvmStatic
    fun addMethod(base: Method, parametrized: Method) {
        pool.computeIfAbsent(base) { mutableSetOf() }.add(parametrized)
    }

    @JvmStatic
    fun addMethods(base: Method, parametrized: Iterable<Method>) {
        pool.computeIfAbsent(base) { mutableSetOf() }.addAll(parametrized)
    }
}