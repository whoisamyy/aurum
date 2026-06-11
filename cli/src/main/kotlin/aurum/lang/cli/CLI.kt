package aurum.lang.cli

import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

class CLI<T : Any>(
    val target: T,
    vararg args: String,
) {
    val args: MutableList<String> = args.toMutableList()

    companion object {
        @Suppress("UNCHECKED_CAST")
        private val typeConverters: MutableMap<Class<*>, TypeConverter<*>> = buildMap {
            fun register(primitive: Class<*>, boxed: Class<*>, converter: TypeConverter<*>) {
                put(primitive, converter)
                put(boxed, converter)
            }
            register(String::class.java, String::class.java, StringConverter)
            register(Int::class.javaPrimitiveType!!, Int::class.javaObjectType, IntConverter)
            register(Char::class.javaPrimitiveType!!, Char::class.javaObjectType, CharConverter)
            register(Long::class.javaPrimitiveType!!, Long::class.javaObjectType, LongConverter)
            register(Short::class.javaPrimitiveType!!, Short::class.javaObjectType, ShortConverter)
            register(Byte::class.javaPrimitiveType!!, Byte::class.javaObjectType, ByteConverter)
            register(Boolean::class.javaPrimitiveType!!, Boolean::class.javaObjectType, BooleanConverter)
            register(Float::class.javaPrimitiveType!!, Float::class.javaObjectType, FloatConverter)
            register(Double::class.javaPrimitiveType!!, Double::class.javaObjectType, DoubleConverter)
            put(Path::class.java, PathConverter)
        } as MutableMap<Class<*>, TypeConverter<*>>

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T : Any, U : TypeConverter<T>> registerTypeConverter(type: Class<T>, converterType: Class<U>) {
            typeConverters[type] = converterType.getDeclaredConstructor().newInstance()
        }

        @JvmStatic
        fun <T : Any> registerTypeConverter(type: Class<T>, converter: TypeConverter<T>) {
            typeConverters[type] = converter
        }
    }

    fun parseArgs(): T {
        val klass = target::class
        if (klass.annotations.none { it is Command }) return target

        val knownNames = klass.collectArgNames()
        parseArgumentGroups(knownNames)
        if (args.isNotEmpty()) parseNestedCommands(knownNames)
        if (args.isNotEmpty()) parsePositionalParameters(knownNames)
        if (args.isNotEmpty()) parseOptions(knownNames)
        return target
    }

    private fun parseNestedCommands(knownNames: List<String>) {
        target::class.java.declaredInstanceFields()
            .filter { it.type.isAnnotationPresent(Command::class.java) }
            .forEach { field -> parseNestedCommand(field, knownNames) }
    }

    private fun parseNestedCommand(field: Field, knownNames: List<String>) {
        val command = field.type.getAnnotation(Command::class.java)
        val commandIndex = args.indexOfFirst { it in command.names.toList() }
        val sliceStart = if (commandIndex != -1) commandIndex else 0
        val relativeEnd = args.subList(sliceStart, args.size)
            .indexOfFirst { it in knownNames }
        val sliceEnd = if (relativeEnd == -1) args.size else sliceStart + relativeEnd
        val commandArgs = args.subList(sliceStart, sliceEnd).toTypedArray()

        val nested = field.type.getConstructor().newInstance()
        CLI(nested, *commandArgs).parseArgs()
        field.write(target, nested)
    }

    private fun parseOptions(knownNames: List<String>) {
        target::class.java.fieldsAnnotatedWith(Option::class.java).forEach { field ->
            val option = field.getAnnotation(Option::class.java)
            val token = args.find { it in option.names }
                ?: run {
                    if (field.get(target) == null)
                        error("Could not parse argument ${option.names[0]}")

                    return@forEach
                }
            field.write(target, parseFieldValue(knownNames, token, option.names.toList(), field))
        }
    }

    private fun parsePositionalParameters(knownNames: List<String>) {
        target::class.java.fieldsAnnotatedWith(Parameters::class.java).forEach { field ->
            val params = field.getAnnotation(Parameters::class.java)
            val token = args.find { it in params.names } ?: args.first()
            field.write(target, parseFieldValue(knownNames, token, params.names.toList(), field))
        }
    }

    private fun parseArgumentGroups(knownNames: List<String>) {
        target::class.java.fieldsAnnotatedWith(ArgumentGroup::class.java).forEach { field ->
            val group = field.getAnnotation(ArgumentGroup::class.java)
            val types = group.types.toList()
            val groupKnownNames = buildGroupKnownNames(knownNames, types)
            val values = if (args.firstOrNull() in groupKnownNames) {
                parseExplicitGroupValues(types, groupKnownNames)
            } else {
                parseImplicitGroupValues(types, groupKnownNames)
            }
            field.write(target, valuesToFieldValue(field, values))
        }
    }

    private fun buildGroupKnownNames(base: List<String>, types: List<KClass<*>>): List<String> =
        (base +
            types.flatMap { it.collectArgNames() } +
            types.flatMap { it.classLevelNames() } +
            types.flatMap { it.enumSynonyms() }
        ).distinct()

    private fun parseExplicitGroupValues(types: List<KClass<*>>, knownNames: List<String>): MutableList<Any> =
        types.mapNotNull { type ->
            val names = type.classLevelNames()
            if (args.isEmpty()) null
            else parseValue(knownNames, args.find { it in names } ?: args.first(), names, type.java)
        }.toMutableList()

    private fun parseImplicitGroupValues(types: List<KClass<*>>, knownNames: List<String>): MutableList<Any> {
        val values = mutableListOf<Any>()
        val defaultType = types.firstOrNull { it.hasNoClassLevelFlags() } ?: return values

        val remaining = types - defaultType
        parseDefaultGroupMember(defaultType, knownNames)?.let { values += it }

        remaining.mapNotNullTo(values) { type -> parseTypedGroupMember(type, knownNames) }
        return values
    }

    private fun parseDefaultGroupMember(type: KClass<*>, knownNames: List<String>): Any? {
        if (type.annotations.any { it is Command }) {
            val instance = type.java.getConstructor().newInstance()
            val memberNames = type.collectArgNames().toSet()
            val end = args.indexOfFirst { it in (knownNames.toSet() - memberNames) }.let { if (it == -1) args.size else it }
            val consumed = args.subList(0, end).toList()
            CLI(instance, *consumed.toTypedArray()).parseArgs()
            args.removeAll(consumed.toSet())
            return instance
        }
        val names = type.collectArgNames()
        return parseValue(knownNames, args.find { it in names } ?: args.first(), names, type.java)
    }

    private fun parseTypedGroupMember(type: KClass<*>, knownNames: List<String>): Any? {
        val names = type.classLevelNames()
        if (args.isEmpty() || (args.none { it in names } && !type.java.isEnum)) return null

        return if (type.annotations.any { it is Command }) {
            val instance = type.java.getConstructor().newInstance()
            val start = args.indexOfLast { it in names }.coerceAtLeast(0)
            val end = args.indexOfLast { it in names }.let { if (it <= 0) args.size else it }
            CLI(instance, *args.subList(start, end).toTypedArray()).parseArgs()
        } else {
            parseValue(knownNames, args.find { it in names } ?: args.first(), names, type.java)
        }
    }

    private fun valuesToFieldValue(field: Field, values: List<Any>): Any? = when {
        field.type.isArray -> values.toTypedArray()
        field.type.kotlin.isSubclassOf(Set::class) -> values.toSet()
        field.type.kotlin.isSubclassOf(List::class) -> values
        else -> values.firstOrNull()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun parseFieldValue(
        knownNames: List<String>,
        token: String,
        names: List<String>,
        field: Field,
    ): Any? {
        val startIndex = args.indexOf(token)
        parseValue(knownNames, token, names, field.type)?.let { return it }

        if (!field.type.kotlin.isSubclassOf(MutableCollection::class)) return null

        val elementType = (field.genericType as ParameterizedType).actualTypeArguments[0] as Class<*>
        val endIndex = collectionEndIndex(startIndex, names, knownNames, token, elementType)
        val sliceStart = if (names.isNotEmpty() && token in names) startIndex + 1 else startIndex
        val elements = args.subList(sliceStart, endIndex).map { parseValue(knownNames, it, names, elementType) }
        if (elements.isEmpty()) return null

        return newMutableCollection(field.type, elements)
    }

    private fun collectionEndIndex(
        startIndex: Int,
        names: List<String>,
        knownNames: List<String>,
        token: String,
        elementType: Class<*>,
    ): Int {
        val relativeStop = args.subList(startIndex, args.size)
            .indexOfFirst { it in (knownNames - token) }
        return when {
            elementType == Boolean::class.javaObjectType -> {
                val flagStop = args.subList(startIndex, args.size)
                    .indexOfFirst { it in (knownNames - names.toSet()) }
                if (flagStop == -1) args.size else startIndex + flagStop
            }
            relativeStop == -1 -> args.size
            else -> startIndex + relativeStop
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun newMutableCollection(type: Class<*>, elements: List<Any?>): MutableCollection<Any?> = when {
        type.kotlin.isSubclassOf(Set::class) -> HashSet(elements.filterNotNull())
        type.kotlin.isSubclassOf(List::class) -> ArrayList(elements.filterNotNull())
        else -> (type.getConstructor().newInstance() as MutableCollection<Any?>).also { it.addAll(elements.filterNotNull()) }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun parseValue(
        knownNames: List<String>,
        token: String,
        names: List<String>,
        type: Class<*>,
    ): Any? {
        val index = args.indexOf(token)

        return when {
            type.isArray -> parseArrayValue(knownNames, token, names, type, index)
            type.isEnum -> parseEnumValue(token, names, type, index)
            type == Boolean::class.javaPrimitiveType || type == java.lang.Boolean::class.java ->
                parseBooleanFlag(token, names, index)
            type.kotlin.objectInstance != null -> parseObjectSingleton(token, names, type, index)
            else -> parseConvertedValue(token, names, type, index)
        }
    }

    private fun parseArrayValue(
        knownNames: List<String>,
        token: String,
        names: List<String>,
        type: Class<*>,
        index: Int,
    ): Any? {
        val relativeEnd = args.subList(index, args.size).indexOfFirst { it in (knownNames - token) }
        val end = if (relativeEnd == -1) args.size else index + relativeEnd
        val elements = args.subList(index + 1, end).map { parseValue(knownNames, it, names, type.componentType) }
        return elements.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEnumValue(token: String, names: List<String>, type: Class<*>, index: Int): Any? {
        val (valueToken, removeIndex) = if (token !in names) {
            token to index
        } else {
            args.getOrNull(index + 1) to index + 1
        }
        if (valueToken == null) return null

        val match = (type.enumConstants as Array<Enum<*>>)
            .find { it.name.equals(valueToken, true) || "-${it.name}".equals(valueToken, true) }
            ?: return null

        args.removeAt(removeIndex)
        return match
    }

    private fun parseBooleanFlag(token: String, names: List<String>, index: Int): Boolean {
        if (token in names) {
            args.removeAt(index)
            return true
        }
        return false
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun parseObjectSingleton(token: String, names: List<String>, type: Class<*>, index: Int): Any? {
        if (token !in names) return null
        args.removeAt(index)
        return type.kotlin.objectInstance
    }

    private fun parseConvertedValue(token: String, names: List<String>, type: Class<*>, index: Int): Any? {
        val valueIndex = if (token in names) index + 1 else index
        val raw = args.getOrNull(valueIndex) ?: return null
        val converted = typeConverters[type]?.convert(raw) ?: return null
        args.removeAt(valueIndex)
        if (token in names)
            args.remove(token)
        return converted
    }
}
