package aurum.lang.cli.args

import aurum.lang.cli.args.util.ArgumentSet

object Arguments {
    @JvmStatic
    val arguments: ArgumentSet = ArgumentSet()

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : Argument> get(argType: Class<T>): T? {
        return arguments.stream()
            .filter { obj: Argument? -> argType.isInstance(obj) }
            .findFirst()
            .orElse(null) as T?
    }

    @JvmStatic
    fun <T : Argument> getOrDefault(argType: Class<T>, default: T): T =
        get(argType) ?: default

    @JvmStatic
    fun <T : Argument> contains(argType: Class<T>): Boolean {
        return arguments.any { argType.isInstance(it) }
    }

    inline fun <reified T : Argument> get(): T? {
        return get(T::class.java)
    }

    inline fun <reified T : Argument> getOrDefault(default: T): T =
        get<T>() ?: default

    inline fun <reified T : Argument> contains(): Boolean {
        return arguments.any { it is T }
    }

    @JvmStatic
    fun addArgument(argument: Argument) {
        arguments += argument
    }

    @JvmStatic
    fun addArguments(arguments: Collection<Argument>) {
        this.arguments += arguments
    }
}