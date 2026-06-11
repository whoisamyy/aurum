package aurum.lang.compiler.frontend.attribute

import aurum.lang.model.Attribute
import kotlin.reflect.KClass

fun Iterable<Attribute>.get(attributeName: String): Attribute? {
    return this.find { attributeName == it.name() }
}

fun Iterable<Attribute>.contains(attributeName: String): Boolean = this.any { attributeName == it.name() }

@Suppress("UNCHECKED_CAST")
fun <T : Attribute> Iterable<Attribute>.get(attributeType: KClass<T>): T? {
    return this.find { attributeType.isInstance(it) } as T?
}

@Suppress("UNCHECKED_CAST")
fun <T : Attribute> Iterable<Attribute>.contains(attributeType: KClass<T>): Boolean {
    return this.any { attributeType.isInstance(it) }
}

@Suppress("UNCHECKED_CAST")
fun <T : Attribute> Iterable<Attribute>.get(attributeType: Class<T>): T? {
    return this.find { attributeType.isInstance(it) } as T?
}

@Suppress("UNCHECKED_CAST")
fun <T : Attribute> Iterable<Attribute>.contains(attributeType: Class<T>): Boolean {
    return this.any { attributeType.isInstance(it) }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Attribute> Iterable<Attribute>.get(): T? {
    return this.find { T::class.isInstance(it) } as T?
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Attribute> Iterable<Attribute>.contains(): Boolean {
    return this.any { T::class.isInstance(it) }
}

fun Array<Attribute>.get(attributeName: String): Attribute? {
    return this.find { attributeName == it.name() }
}

fun Array<Attribute>.contains(attributeName: String): Boolean {
    return this.any { attributeName == it.name() }
}

@Suppress("UNCHECKED_CAST")
fun <T : Attribute> Array<Attribute>.get(attributeType: KClass<T>): T? {
    return this.find { attributeType.isInstance(it) } as T?
}


@Suppress("UNCHECKED_CAST")
fun <T : Attribute> Array<Attribute>.contains(attributeType: KClass<T>): Boolean {
    return this.any { attributeType.isInstance(it) }
}

@Suppress("UNCHECKED_CAST")
fun <T : Attribute> Array<Attribute>.get(attributeType: Class<T>): T? {
    return this.find { attributeType.isInstance(it) } as T?
}

@Suppress("UNCHECKED_CAST")
fun <T : Attribute> Array<Attribute>.contains(attributeType: Class<T>): Boolean {
    return this.any { attributeType.isInstance(it) }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Attribute> Array<Attribute>.get(): T? {
    return this.find { T::class.isInstance(it) } as T?
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Attribute> Array<Attribute>.contains(): Boolean {
    return this.any { T::class.isInstance(it) }
}

