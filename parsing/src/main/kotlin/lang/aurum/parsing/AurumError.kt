package lang.aurum.parsing

import lang.aurum.parsing.stages.FileContext
import lang.aurum.parsing.stages.coderesolution.IRCompiler
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import java.nio.file.Path

/**
 * Exception thrown when an error occurs during compilation of Aurum code.
 * Includes file location and position information when available.
 */
class AurumCompilationError(
    message: String,
    val filePath: Path? = null,
    val line: Int? = null,
    val column: Int? = null
) : IllegalStateException(formatMessage(message, filePath, line, column)) {

    companion object {
        private fun formatMessage(
            message: String,
            filePath: Path?,
            line: Int?,
            column: Int?
        ): String {
            val location = buildString {
                if (filePath != null) {
                    append(filePath)
                }
                if (line != null) {
                    if (filePath != null) append(":")
                    append(line)
                    if (column != null) {
                        append(":")
                        append(column)
                    }
                }
            }
            return if (location.isNotEmpty()) {
                "$location: $message"
            } else {
                message
            }
        }
    }
}

/**
 * Creates an [AurumCompilationError] with location information from a parser context.
 *
 * @param message The error message
 * @param ctx The parser rule context providing location information
 * @param fileContext Optional file context providing file path information
 * @return An [AurumCompilationError] with formatted location information
 */
fun aurumError(
    message: String,
    ctx: ParserRuleContext? = null,
    fileContext: FileContext? = null
): AurumCompilationError {
    val line = ctx?.start?.line
    val column = ctx?.start?.charPositionInLine
    val filePath = fileContext?.path
    return AurumCompilationError(message, filePath, line, column)
}

/**
 * Creates an [AurumCompilationError] with location information from a parser context.
 *
 * @param message The error message
 * @param positionString The string providing location information
 * @param fileContext Optional file context providing file path information
 * @return An [AurumCompilationError] with formatted location information
 */
fun aurumError(
    message: String,
    positionString: String?,
    fileContext: FileContext? = null
): AurumCompilationError {
    val position = positionString?.split(":")?.map(String::toInt)
    val line = position?.get(0)
    val column = position?.get(1)
    val filePath = fileContext?.path
    return AurumCompilationError(message, filePath, line, column)
}

/**
 * Creates an [AurumCompilationError] with location information from a terminal node.
 *
 * @param message The error message
 * @param node The terminal node providing location information
 * @param fileContext Optional file context providing file path information
 * @return An [AurumCompilationError] with formatted location information
 */
fun aurumError(
    message: String,
    node: TerminalNode,
    fileContext: FileContext? = null
): AurumCompilationError {
    val line = node.symbol.line
    val column = node.symbol.charPositionInLine
    val filePath = fileContext?.path
    return AurumCompilationError(message, filePath, line, column)
}

/**
 * Creates an [AurumCompilationError] with explicit location information.
 *
 * @param message The error message
 * @param filePath Optional file path
 * @param line Optional line number (1-based)
 * @param column Optional column number (0-based)
 * @return An [AurumCompilationError] with formatted location information
 */
fun aurumError(
    message: String,
    filePath: Path? = null,
    line: Int? = null,
    column: Int? = null
): AurumCompilationError {
    return AurumCompilationError(message, filePath, line, column)
}

/**
 * Creates an [AurumCompilationError] with explicit location information.
 *
 * @param message The error message
 * @param filePath Optional file path
 * @param positionString in format of "line:column"
 * @return An [AurumCompilationError] with formatted location information
 */
fun aurumError(
    message: String,
    filePath: Path? = null,
    positionString: String? = null
): AurumCompilationError {
    val position = positionString?.split(":")?.map(String::toInt)
    val line = position?.get(0)
    val column = position?.get(1)
    return AurumCompilationError(message, filePath, line, column)
}

/**
 * Throws an [AurumCompilationError] with location information from a parser context.
 *
 * @param message The error message
 * @param ctx The parser rule context providing location information
 * @param fileContext Optional file context providing file path information
 * @throws AurumCompilationError Always throws an exception
 */
fun throwAurumError(
    message: String,
    ctx: ParserRuleContext? = null,
    fileContext: FileContext? = null
): Nothing {
    throw aurumError(message, ctx, fileContext)
}

/**
 * Throws an [AurumCompilationError] with location information from a terminal node.
 *
 * @param message The error message
 * @param node The terminal node providing location information
 * @param fileContext Optional file context providing file path information
 * @throws AurumCompilationError Always throws an exception
 */
fun throwAurumError(
    message: String,
    node: TerminalNode,
    fileContext: FileContext? = null
): Nothing {
    throw aurumError(message, node, fileContext)
}


/**
 * Throws an [AurumCompilationError] with location information from a terminal node.
 *
 * @param message The error message
 * @param positionString The string providing location information
 * @param fileContext Optional file context providing file path information
 * @throws AurumCompilationError Always throws an exception
 */
fun throwAurumError(
    message: String,
    positionString: String? = null,
    fileContext: FileContext? = null
): Nothing {
    throw aurumError(message, positionString, fileContext)
}

/**
 * Throws an [AurumCompilationError] with explicit location information.
 *
 * @param message The error message
 * @param filePath Optional file path
 * @param line Optional line number (1-based)
 * @param column Optional column number (0-based)
 * @throws AurumCompilationError Always throws an exception
 */
fun throwAurumError(
    message: String,
    filePath: Path? = null,
    line: Int? = null,
    column: Int? = null
): Nothing {
    throw aurumError(message, filePath, line, column)
}

/**
 * Extension function to create an [AurumCompilationError] using IRCompiler's file context.
 *
 * @param message The error message
 * @param ctx The parser rule context providing location information
 * @return An [AurumCompilationError] with formatted location information
 */
fun IRCompiler.aurumError(
    message: String,
    ctx: ParserRuleContext? = null
): AurumCompilationError {
    return aurumError(message, ctx, this.fileContext)
}

/**
 * Extension function to throw an [AurumCompilationError] using IRCompiler's file context.
 *
 * @param message The error message
 * @param ctx The parser rule context providing location information
 * @throws AurumCompilationError Always throws an exception
 */
fun IRCompiler.throwAurumError(
    message: String,
    ctx: ParserRuleContext? = null
): Nothing {
    throw aurumError(message, ctx, this.fileContext)
}

