package lang.aurum.parsing.stages

import lang.aurum.model.Field
import lang.aurum.model.Method
import lang.aurum.model.Type
import kotlin.reflect.full.isSubclassOf

class ImportMap(
    val typeMap: MutableMap<String, Type> = mutableMapOf(),
    val methodMap: MutableMap<String, MutableSet<Method>> = mutableMapOf(),
    val fieldMap: MutableMap<String, MutableSet<Field>> = mutableMapOf(),
    val symbolMap: MutableMap<String, String> = mutableMapOf(),
) {
    operator fun set(key: String, value: Type) {
        typeMap[key] = value
    }

//    operator fun set(key: String, value: Method) {
//        methodMap.putIfAbsent(key, mutableSetOf())
//        methodMap[key]!!.add(value)
//    }
//
//    operator fun set(key: String, value: Field) {
//        fieldMap.putIfAbsent(key, mutableSetOf())
//        fieldMap[key]!!.add(value)
//    }

    @JvmName("plusAssignType")
    operator fun plusAssign(pair: Pair<String, Type>) {
        typeMap += pair
    }

    @JvmName("plusAssignMethod")
    operator fun plusAssign(pair: Pair<String, Method>) {
        val (key, value) = pair
        methodMap.computeIfAbsent(key) { mutableSetOf() }.add(value)
    }

    @JvmName("plusAssignMethods")
    operator fun plusAssign(pair: Pair<String, Iterable<Method>>) {
        val (key, values) = pair
        methodMap.computeIfAbsent(key) { mutableSetOf() }.addAll(values)
    }

    @JvmName("plusAssignField")
    operator fun plusAssign(pair: Pair<String, Field>) {
        val (key, value) = pair
        fieldMap.computeIfAbsent(key) { mutableSetOf() }.add(value)
    }

    /**
     * @param pair Pair of Strings, containing key (alias) and value in this order.
     */
    @JvmName("plusAssignSymbol")
    operator fun plusAssign(pair: Pair<String, String>) {
        val (key, value) = pair
        symbolMap[key] = value
    }

    operator fun plusAssign(symbol: Symbol) {
        val (value, key) = symbol
        symbolMap[key] = value
    }

    inline operator fun <reified T> get(key: String): Any? {
        return when {
            T::class.isSubclassOf(Type::class) -> typeMap[key] as T?
            T::class.isSubclassOf(Field::class) -> fieldMap[key] as T?
            T::class.isSubclassOf(Method::class) -> methodMap[key] as T?
            T::class == String::class -> symbolMap[key] as T?
            T::class == Any::class -> {
                when {
                    typeMap[key] != null -> typeMap[key]
                    fieldMap[key] != null -> fieldMap[key]
                    methodMap[key] != null -> methodMap[key]
                    symbolMap[key] != null -> symbolMap[key]
                    else -> null
                } as T?
            }
            else -> null
        }
    }
}