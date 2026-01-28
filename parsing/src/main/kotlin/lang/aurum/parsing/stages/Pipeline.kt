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
            val lineContent = fileContent[e.line!! - 1]
            val repeatCount = e.column!! + 1 + line.toString().length
            println("""
                ERROR: ${e.filePath}:$line:$column
                
                $line $lineContent
                |${"-".repeat(repeatCount-1)}${"^".repeat(Math.clamp((lineContent.length - repeatCount + 2).toLong(), 1, Int.MAX_VALUE))}
                
                ${e.filePath ?: ""}:$line:$column : ${e.message}
            """.trimIndent())

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