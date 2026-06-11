package aurum.lang.compiler.frontend.stages

import kotlin.reflect.KClass

abstract class Stage {
    val inputs = mutableListOf<Property<*>>()
    val outputs = mutableListOf<Property<*>>()
    val dependsOn = mutableSetOf<KClass<out Stage>>()

    protected inline fun <reified T : Artifact> input(): Property<T> =
        Property(T::class).also { inputs.add(it) }

    protected inline fun <reified T : Artifact> output(): Property<T> =
        Property(T::class).also { outputs.add(it) }

    @Suppress("UNCHECKED_CAST")
    protected fun <T : Artifact> output(clazz: KClass<out T>): Property<T> {
        return Property(clazz as KClass<T>).also { outputs.add(it) }
    }

    protected inline fun <reified T : Stage> dependsOn() {
        dependsOn.add(T::class)
    }

    abstract fun execute()
}

interface Provider<out T : Artifact> {
    fun get(): T
}

open class Property<T : Artifact>(
    val type: KClass<T>
) : Provider<T> {
    private var value: T? = null
    internal var getter: (() -> T)? = null

    fun set(v: T) { this.value = v }
    fun set(v: () -> T) { this.getter = v }

    override fun get(): T = value ?: getter?.invoke() ?: error("Artifact of type $type is not ready yet")
}