package lang.aurum

import lang.aurum.util.ArgumentSet

object Arguments {
    val arguments: ArgumentSet = ArgumentSet()

    @Suppress("UNCHECKED_CAST")
    fun <T : Argument> get(argType: Class<T>): T? {
        return arguments.stream()
            .filter { obj: Argument? -> argType.isInstance(obj) }
            .findFirst()
            .orElse(null) as T?
    }

    fun <T : Argument> contains(argType: Class<T>): Boolean {
        return arguments.any { argType.isInstance(it) }
    }

    inline fun <reified T : Argument> get(): T? {
        return get(T::class.java)
    }

    inline fun <reified T : Argument> contains(): Boolean {
        return arguments.any { it is T }
    }
}