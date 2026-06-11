package aurum.lang.cli

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

internal fun Field.ensureMutable() {
    if (Modifier.isFinal(modifiers)) {
        throw IllegalStateException(
            "Cannot assign CLI value to final field ${declaringClass.simpleName}#$name"
        )
    }
}

internal fun Field.write(target: Any, value: Any?) {
    ensureMutable()
    if (!canAccess(target)) trySetAccessible()
    set(target, value)
}

internal fun Class<*>.declaredInstanceFields(): Sequence<Field> =
    declaredFields.asSequence().filter { !Modifier.isStatic(it.modifiers) && !Modifier.isPrivate(it.modifiers) }

internal fun Class<*>.fieldsAnnotatedWith(annotation: Class<out Annotation>): List<Field> =
    declaredInstanceFields().filter { it.isAnnotationPresent(annotation) }.toList()

internal fun KClass<*>.classLevelNames(): List<String> =
    annotations.mapNotNull { ann ->
        when (ann) {
            is Option -> ann.names
            is Command -> ann.names
            else -> null
        }
    }.flatMap { it.toList() }

@Suppress("UNCHECKED_CAST")
internal fun KClass<*>.enumSynonyms(): List<String> =
    if (!java.isEnum) emptyList()
    else (java.enumConstants as Array<Enum<*>>).flatMap { listOf(it.name, "-${it.name}") }

internal fun KClass<*>.collectArgNames(): List<String> {
    val fromFields = memberProperties
        .mapNotNull { it.javaField }
        .flatMap { field ->
            field.annotations.mapNotNull { ann ->
                when (ann) {
                    is Parameters -> ann.names
                    is Option -> ann.names
                    else -> null
                }
            }.flatMap { it.toList() }
        }

    val commandOptions = memberProperties
        .filter { prop ->
            prop.annotations.any { it is Command } && prop.annotations.any { it is Option }
        }
        .mapNotNull { it.javaField?.getAnnotation(Command::class.java)?.names }
        .flatMap { it.toList() }

    val fromGroups = memberProperties
        .mapNotNull { it.javaField?.getAnnotation(ArgumentGroup::class.java) }
        .flatMap { group ->
            group.types.flatMap { type ->
                type.annotations.mapNotNull { ann ->
                    when (ann) {
                        is Option -> ann.names
                        is Command -> ann.names
                        else -> null
                    }
                }.flatMap { it.toList() }
            }
        }

    return fromFields + commandOptions + fromGroups
}

internal fun KClass<*>.hasNoClassLevelFlags(): Boolean =
    classLevelNames().isEmpty() && !java.isEnum