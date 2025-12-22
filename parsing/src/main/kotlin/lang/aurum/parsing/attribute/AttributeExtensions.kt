package lang.aurum.parsing.attribute

import lang.aurum.model.Attribute
import kotlin.reflect.KClass

fun Iterable<Attribute>.get(attributeName: String): Attribute? {
    return this.find { attributeName == it.name() }
}

@Suppress("UNCHECKED_CAST")
fun <T : Attribute> Iterable<Attribute>.get(attributeType: KClass<T>): T? {
    return this.find { attributeType.isInstance(it) } as T?
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Attribute> Iterable<Attribute>.get(): T? {
    return this.find { T::class.isInstance(it) } as T?
}

fun Array<Attribute>.get(attributeName: String): Attribute? {
    return this.find { attributeName == it.name() }
}

@Suppress("UNCHECKED_CAST")
fun <T : Attribute> Array<Attribute>.get(attributeType: KClass<T>): T? {
    return this.find { attributeType.isInstance(it) } as T?
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Attribute> Array<Attribute>.get(): T? {
    return this.find { T::class.isInstance(it) } as T?
}

