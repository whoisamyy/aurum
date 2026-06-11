package aurum.lang.cli

import kotlin.reflect.KClass


/**
 * Marks a class as a CLI command. When present on [CLI.target]'s class, parsing runs.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Command(vararg val names: String = [])

/**
 * Positional parameters. Unnamed parameters must appear before flags in the argument list.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Parameters(vararg val names: String = [])

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
annotation class Option(vararg val names: String = [])

/**
 * Groups several argument types into one field ([Array], [Set], or [List]).
 *
 * - Types without [Option]/[Command] class annotations are matched as the default positional group.
 * - Types that are not assignable to the field type are skipped.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ArgumentGroup(val types: Array<KClass<*>>)