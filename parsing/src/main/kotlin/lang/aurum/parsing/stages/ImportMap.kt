package lang.aurum.parsing.stages

import lang.aurum.model.Field
import lang.aurum.model.Method
import lang.aurum.model.Type
import kotlin.reflect.full.isSubclassOf

class ImportMap(
    val typeMap: MutableMap<String, Type> = mutableMapOf(),
    val methodMap: MutableMap<String, Method> = mutableMapOf(),
    val fieldMap: MutableMap<String, Field> = mutableMapOf()
) {
    operator fun set(key: String, value: Type) {
        typeMap[key] = value
    }

    operator fun set(key: String, value: Method) {
        methodMap[key] = value
    }

    operator fun set(key: String, value: Field) {
        fieldMap[key] = value
    }

    @JvmName("plusAssignType")
    operator fun plusAssign(pair: Pair<String, Type>) {
        typeMap += pair
    }

    @JvmName("plusAssignMethod")
    operator fun plusAssign(pair: Pair<String, Method>) {
        methodMap += pair
    }

    @JvmName("plusAssignField")
    operator fun plusAssign(pair: Pair<String, Field>) {
        fieldMap += pair
    }

    inline operator fun <reified T> get(key: String): T? {
        return when {
            T::class.isSubclassOf(Type::class) -> typeMap[key] as T?
            T::class.isSubclassOf(Field::class) -> fieldMap[key] as T?
            T::class.isSubclassOf(Method::class) -> methodMap[key] as T?
            T::class == Any::class -> {
                when {
                    typeMap[key] != null -> typeMap[key]
                    fieldMap[key] != null -> fieldMap[key]
                    methodMap[key] != null -> methodMap[key]
                    else -> null
                } as T?
            }
            else -> null
        }
    }
}