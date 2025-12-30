package lang.aurum

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties

/**
 * Constructs a group of [Parameters] and [Option]s
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Command (
    vararg val names: String = []
)

/**
 * Constructs an array of values
 *
 * Parameter args must come first if no names are provided
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Parameters (
    vararg val names: String = []
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
annotation class Option (
    vararg val names: String = []
)


/**
 * Constructs a group or option of arguments that are defined in [types].
 *
 * Will construct a group if type of field is [Array], [Set] or [List]
 *
 * Note:
 * - if one of [types] does not have [Option] or [Command] annotation this type will not be processed.
 * - if one of [types] is not subclass of field type this type also won't be processed.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ArgumentGroup (
    vararg val types: KClass<*>
)

private fun KClass<*>.getArgNames(): List<String> {
    val allAnnotations = this.memberProperties
        .flatMap { it.annotations }

    val paramNames = mutableListOf<String>()

    paramNames += allAnnotations
        .mapNotNull { it as? Parameters }
        .flatMap { it.names.toList() }

    paramNames += allAnnotations
        .mapNotNull { it as? Option }
        .flatMap { it.names.toList() }

    paramNames +=
        this.memberProperties.filter {
                it.annotations
                    .any { a -> a is Command }
                        && it.annotations.any { a -> a is Option }
            }
            .mapNotNull { it.annotations.find { a -> a is Command } as? Command }
            .flatMap { it.names.toList() }

    paramNames +=
        this.memberProperties.filter { it.annotations.any { a -> a is ArgumentGroup } }
            .mapNotNull { it.annotations.find { a -> a is ArgumentGroup } as? ArgumentGroup }
            .flatMap { it.types.flatMap { t -> t.annotations } }
            .mapNotNull {
                (it as? Option)?.names ?: (it as? Command)?.names
            }
            .flatMap { it.toList() }

    return paramNames
}

class CLI<T : Any>(
    val target: T,
    vararg args: String
) {
    val args: MutableList<String> = args.toMutableList()

    companion object {
        @Suppress("UNCHECKED_CAST")
        private val registeredTypeConverters: MutableMap<Class<*>, Class<TypeConverter<*>>> = mutableMapOf(
            String::class.java to StringConverter::class.java,
            Int::class.java to IntConverter::class.java,
            Char::class.java to CharConverter::class.java,
            Long::class.java to LongConverter::class.java,
            Short::class.java to ShortConverter::class.java,
            Byte::class.java to ByteConverter::class.java,
            Boolean::class.java to BooleanConverter::class.java,
            Path::class.java to PathConverter::class.java,
            Float::class.java to FloatConverter::class.java,
            Double::class.java to DoubleConverter::class.java,
            Int::class.javaObjectType to IntConverter::class.java,
            Char::class.javaObjectType to CharConverter::class.java,
            Long::class.javaObjectType to LongConverter::class.java,
            Short::class.javaObjectType to ShortConverter::class.java,
            Byte::class.javaObjectType to ByteConverter::class.java,
            Boolean::class.javaObjectType to BooleanConverter::class.java,
            Float::class.javaObjectType to FloatConverter::class.java,
            Double::class.javaObjectType to DoubleConverter::class.java,
        ) as MutableMap<Class<*>, Class<TypeConverter<*>>>

        @Suppress("UNCHECKED_CAST")
        fun <T : Any, U : TypeConverter<T>> registerTypeConverter(type: Class<T>, converterType: Class<U>) {
            registeredTypeConverters[type] = converterType as Class<TypeConverter<*>>
        }
    }

    fun parseArgs(): T {
        val klass = target::class
        if (klass.annotations.any { it is Command }) {
            val paramNames = klass.getArgNames()

            parseArgumentGroup(paramNames)
            if (args.isNotEmpty())
                parseCommands(paramNames)
            if (args.isNotEmpty())
                parseParameters(paramNames)
            if (args.isNotEmpty())
                parseOptions(paramNames)
        }

        return target
    }

    private fun parseCommands(
        paramNames: List<String>
    ) {
        target::class.java.declaredFields.filter { it.type.annotations.any { a -> a is Command} }
            .forEach {
                val command = it.type.annotations.find{ a -> a is Command } as Command
                val indexOfFirst = args.indexOfFirst { s -> s in command.names }
                val argIndex = if (indexOfFirst != -1) indexOfFirst else 0
                val indexOfFirst1 = args.toList().subList(argIndex, args.size).indexOfFirst { s -> s in paramNames }
                val endIndex = if (indexOfFirst1 != -1) indexOfFirst1 else args.size

                val newTarget = (it.type as Class<*>).getConstructor().newInstance()

                parseArgs()

                if (Modifier.isFinal(it.modifiers))
                    throw IllegalStateException("todo")

                it.set(target, newTarget)
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseOptions(
        paramNames: List<String>
    ) {
        target::class.java.declaredFields.filter { it.annotations.any { a -> a is Option } }
            .forEach {
                val parameters = it.annotations.find { a -> a is Option } as Option
                val names = parameters.names
                if (Modifier.isFinal(it.modifiers))
                    throw IllegalStateException("todo")

                val value = parseValue(paramNames, args.find { a -> a in names } ?: args[0], names.toList(), it)
                it.set(target, value)
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseArgumentGroup(
        paramNames: List<String>
    ) {
        target::class.java.declaredFields.filter { it.annotations.any { a -> a is ArgumentGroup } }
            .forEach {
                val types = (it.annotations.find { a -> a is ArgumentGroup } as ArgumentGroup).types
                val newParamNames = (
                        paramNames
                        + types.flatMap { t -> t.getArgNames() }
                        + types.flatMap { t -> t.annotations.mapNotNull { a -> a as? Option }.flatMap { a -> a.names.toList() } }
                        + types.flatMap { t -> t.annotations.mapNotNull { a -> a as? Command }.flatMap { a -> a.names.toList() } }
                        + types.flatMap { t ->
                            if (t.java.isEnum) {
                                (t.java.enumConstants as Array<Enum<*>>).flatMap { e -> listOf(e.name, "-${e.name}") }
                            } else listOf()
                        }
                ).toSet().toList()

                val values: MutableList<Any>
                if (args[0] in (newParamNames)) {
                    values = types.mapNotNull { t ->
                        val names = (
                                types.flatMap { t -> t.annotations.mapNotNull { a -> a as? Option }.flatMap { a -> a.names.toList() } }
                                + types.flatMap { t -> t.annotations.mapNotNull { a -> a as? Command }.flatMap { a -> a.names.toList() } }
                        )

                        if (args.isEmpty())
                            null
                        else
                            parseValue(
                                newParamNames,
                                args.find { a -> a in names } ?: args[0],
                                names.toList(),
                                t.javaObjectType
                            )
                    }.toMutableList()
                } else {
                    values = mutableListOf()

                    val t = types.find { t ->
                        (
                            t.annotations.mapNotNull { a -> a as? Option }.flatMap { a -> a.names.toList() }
                            + t.annotations.mapNotNull { a -> a as? Command }.flatMap { a -> a.names.toList() }
                        ).isEmpty() && !t.java.isEnum
                    }?.let { t ->
                        val names = t.getArgNames()

                        val value = if (t.annotations.any { a -> a is Command }) {
                            val newInstance = t.javaObjectType.getConstructor().newInstance()
                            val subList =
                                listOf(*args.subList(0, args.indexOfFirst { s -> s in (newParamNames - names.toSet()) }).toTypedArray())
                            val cli = CLI(
                                newInstance,
                                *subList
                                    .toTypedArray()
                            )
                            val value = cli
                                .parseArgs()

                            this@CLI.args -= subList.toSet()

                            value
                        } else {
                            parseValue(
                                newParamNames,
                                args.find { a -> a in names } ?: args[0],
                                names.toList(),
                                t.javaObjectType
                            )
                        }
                        if (value != null)
                            values += value

                        t
                    }
                    val newTypes = if (t != null) types.toList() - t else types.toList()

                    values.addAll(newTypes.mapNotNull { t ->
                        val names = (
                                t.annotations.mapNotNull { a -> a as? Option }.flatMap { a -> a.names.toList() }
                                + t.annotations.mapNotNull { a -> a as? Command }.flatMap { a -> a.names.toList() }
                            )

                        if ((args.isEmpty() || !args.any { s -> s in names }) && !t.java.isEnum)
                            null
                        else {
                            if (t.annotations.any { a -> a is Command }) {
                                val newInstance = t.javaObjectType.getConstructor().newInstance()
                                CLI(newInstance, *args.subList(0, args.indexOfFirst { s -> s in names }).toTypedArray())
                                    .parseArgs()
                            } else {
                                parseValue(
                                    newParamNames,
                                    args.find { a -> a in names } ?: args[0],
                                    names.toList(),
                                    t.javaObjectType
                                )
                            }
                        }
                    })

                }
                if (it.type.isArray) {
                    it.set(target, values.toTypedArray())
                } else if (it.type.kotlin.isSubclassOf(Set::class)) {
                    it.set(target, values.toSet())
                } else if (it.type.kotlin.isSubclassOf(List::class)) {
                    it.set(target, values)
                } else {
                    it.set(target, values[0])
                }

                /*
                if (it.type is Collection) {
                    val value = Collection::newInstance
                    it.set(target, value)
                    for (t in types) {
                        value += ValueProcessor(names, args, paramNames, t).parseValue()
                    }
                }
                 */
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseParameters(
        paramNames: List<String>
    ) {
        target::class.java.declaredFields.filter { it.annotations.any { a -> a is Parameters } }
            .forEach {
                val parameters = it.annotations.find { a -> a is Parameters } as Parameters
                val names = parameters.names
                if (Modifier.isFinal(it.modifiers))
                    throw IllegalStateException("todo")

                val value = parseValue(paramNames, args.find { a -> a in names } ?: args[0], names.toList(), it)
                it.set(target, value)
            }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun parseValue(paramNames: List<String>, argString: String, names: List<String>, field: Field): Any? {
        var argIndex = args.indexOf(argString)
        val type = field.type

        val value = parseValue(paramNames, argString, names, type)
        if (value != null)
            return value

        if (type.kotlin.isSubclassOf(MutableCollection::class)) {
            val component = (field.genericType as ParameterizedType).actualTypeArguments[0] as Class<*>
            val indexOfFirst = args.toList().subList(argIndex, args.size).indexOfFirst { s -> s in (paramNames - argString) }
            val endIndex: Int = if (component == Boolean::class.javaObjectType) {
                val indexOfFirst1 =
                    args.toList().subList(argIndex, args.size).indexOfFirst { s -> s in (paramNames - names.toSet()) }
                if (indexOfFirst1 == -1) args.size else indexOfFirst1
            } else {
                if (names.isNotEmpty())
                    argIndex++
                if (indexOfFirst == -1) args.size else indexOfFirst
            }

            val newValue = when {
                type.kotlin.isSubclassOf(Set::class) -> {
                    HashSet()
                }

                type.kotlin.isSubclassOf(List::class) -> {
                    ArrayList()
                }

                else -> type.getConstructor().newInstance() as MutableCollection<Any?>
            }

            val subList = listOf(*args.subList(argIndex, endIndex).toTypedArray())
            subList.forEach { s ->
                newValue.add(parseValue(paramNames, s, names, component))
            }

            return newValue.ifEmpty { null }
        }

        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseValue(
        paramNames: List<String>,
        argString: String,
        names: List<String>,
        type: Class<*>
    ): Any? {
        val argIndex = args.indexOf(argString)

        return when {
            type.isArray -> {
                val indexOfFirst = args.toList().subList(argIndex, args.size).indexOfFirst { s -> s in (paramNames - argString) }
                val endIndex = if (indexOfFirst == -1) args.size else indexOfFirst
                val component = type.componentType
                val subList = listOf(*args.subList(argIndex+1, endIndex).toTypedArray())
                val array = subList.map { s ->
                    parseValue(paramNames, s, names, component)
                }.toTypedArray()

                array.ifEmpty { null }
            }

            type.isEnum -> {
                val (newArgString, newArgIndex) = if (argString !in names)
                    argString to argIndex
                else
                    args[argIndex+1] to argIndex+1

                val value = (type.enumConstants as Array<Enum<*>>).find { e -> (e.name == newArgString || "-${e.name}" == newArgString) }
                if (value != null)
                    args.removeAt(newArgIndex)

                value
            }

            type == Boolean::class.javaPrimitiveType || type == java.lang.Boolean::class.java -> {
                if (argString in names) {
                    args.removeAt(argIndex)
                    return true
                }
                return false
            }

            // if type is object
            type.kotlin.objectInstance != null -> {
                if (argString in names) {
                    args.removeAt(argIndex)
                    type.kotlin.objectInstance
                }
                else null
            }

            else -> {
                val newArgIndex = if (argString in names)
                    argIndex+1
                else argIndex

                val value = registeredTypeConverters[type]?.getConstructor()?.newInstance()
                    ?.convert(args[newArgIndex])
                if (value != null) {
                    args.removeAt(newArgIndex)
                }

                value
            }
        }
    }
}

interface TypeConverter<T : Any> {
    fun convert(string: String): T
}

class StringConverter : TypeConverter<String> {
    override fun convert(string: String): String = string
}

class IntConverter : TypeConverter<Int> {
    override fun convert(string: String): Int = string.toInt()
}

class CharConverter : TypeConverter<Char> {
    override fun convert(string: String): Char = string[0]
}

class LongConverter : TypeConverter<Long> {
    override fun convert(string: String): Long = string.toLong()
}

class FloatConverter : TypeConverter<Float> {
    override fun convert(string: String): Float = string.toFloat()
}

class DoubleConverter : TypeConverter<Double> {
    override fun convert(string: String): Double = string.toDouble()
}

class ShortConverter : TypeConverter<Short> {
    override fun convert(string: String): Short = string.toShort()
}

class ByteConverter : TypeConverter<Byte> {
    override fun convert(string: String): Byte = string.toByte()
}

class BooleanConverter : TypeConverter<Boolean> {
    override fun convert(string: String): Boolean = string.toBoolean()
}

class PathConverter : TypeConverter<Path> {
    override fun convert(string: String): Path = Path.of(string)
}
