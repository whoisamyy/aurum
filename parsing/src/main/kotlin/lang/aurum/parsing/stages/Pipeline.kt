package lang.aurum.parsing.stages

import lang.aurum.Arguments
import lang.aurum.parsing.AurumCompilationError
import lang.aurum.parsing.ParserArgument
import lang.aurum.parsing.stages.coderesolution.CodeResolutionStage
import lang.aurum.parsing.stages.memberresolution.ClassMemberResolutionStage
import lang.aurum.parsing.stages.memberresolution.EarlyLinkingStage
import lang.aurum.parsing.stages.optimisation.OptimisationStage
import java.nio.file.Files

data class Pipeline (
    val stages: List<(ParsingContext) -> ParsingStage> = listOf(
        ::DependenciesResolutionStage,
        ::ClassesResolutionStage,
        ::EarlyLinkingStage,
        ::ClassMemberResolutionStage,
        ::InternalLinkingStage,
        ::ExternalLinkingStage,
        ::DesugaringStage,
        ::CodeResolutionStage,
        ::OptimisationStage,
        ::ConstantPoolCleaningStage,
        ::CodeCleaningStage,
    )
) {
    constructor(vararg stages: (ParsingContext) -> ParsingStage) : this(stages.toList())

    fun printErrorMessage(e: Throwable) {
        if (e is AurumCompilationError) {
            val fileContent = Files.readAllLines(e.filePath)

            val line = e.line
            val column = e.column
            var errorLines: String = ""
            if (line != null && column != null) {
                val repeatCount = column + line.toString().length
                val lineContent = fileContent[e.line - 1]
                val carets = "^".repeat(lineContent.length-repeatCount+line.toString().length+1)

                val dashes = "-".repeat(repeatCount)

                errorLines = """
                    |$line $lineContent
                    |$dashes$carets
                """.trimMargin()
            }
            val positionString = if (line != null && column != null) "$line:$column" else ""
            @Suppress("SENSELESS_COMPARISON") // it is not senseless
            println("""
                |ERROR: ${e.filePath}:$positionString
                |
                |$errorLines
                |
                |${e.filePath ?: ""}:$positionString : ${e.message}
            """.trimMargin())

            if (Arguments.contains<ParserArgument.PrintStackTrace>())
                e.printStackTrace(System.err)
        } else
            e.printStackTrace(System.err)
    }

    fun execute(context: ParsingContext) {
        stages.forEach {
            try {
                it(context).execute()
            } catch (e: Throwable) {
                printErrorMessage(e)
            }
        }
    }
}