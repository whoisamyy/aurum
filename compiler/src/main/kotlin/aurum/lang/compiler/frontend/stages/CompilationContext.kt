package aurum.lang.compiler.frontend.stages

import aurum.lang.compiler.frontend.Pipeline
import kotlin.reflect.KClass

interface Artifact

class CompilationContext {
    private val storage = mutableMapOf<KClass<out Artifact>, Artifact>()
    fun <T : Artifact> put(clazz: KClass<out T>, artifact: T) {
        storage[clazz] = artifact
    }

    fun <T : Artifact> put(artifact: T) {
        storage[artifact::class] = artifact
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Artifact> get(clazz: KClass<T>): T {
        return storage[clazz] as? T
            ?: throw IllegalStateException("Artifact ${clazz.simpleName} not found! Perhaps, stage didn't run?")
    }

    inline fun <reified T : Artifact> get(): T = get(T::class)

    fun registerContext() {
        Pipeline.registerStage(object : Stage() {
            override fun execute() {}

            init {
                for ((clazz, artifact) in storage) {
                    output(clazz).set(artifact)
                }
            }
        })
    }
}