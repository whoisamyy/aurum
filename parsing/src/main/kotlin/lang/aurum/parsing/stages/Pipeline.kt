package lang.aurum.parsing.stages

import lang.aurum.parsing.AurumCompilationError
import lang.aurum.parsing.stages.coderesolution.CodeResolutionStage
import lang.aurum.parsing.stages.memberresolution.ClassMemberResolutionStage
import lang.aurum.parsing.stages.memberresolution.EarlyLinkingStage
import lang.aurum.parsing.stages.optimisation.OptimizationStage
import java.nio.file.Files

data class Pipeline(
    val stages: List<(ParsingContext) -> ParsingStage> = listOf(
        ::DependenciesResolutionStage,
        ::ClassesResolutionStage,
        ::EarlyLinkingStage,
        ::ClassMemberResolutionStage,
        ::InternalLinkingStage,
        ::ExternalLinkingStage,
        ::DesugaringStage,
        ::CodeResolutionStage,
        ::OptimizationStage,
        ::ConstantPoolCleaningStage
    )
) {
    constructor(vararg stages: (ParsingContext) -> ParsingStage) : this(stages.toList())

    fun printErrorMessage(e: Throwable) {
        if (e is AurumCompilationError) {
            val fileContent = Files.readAllLines(e.filePath)

            val line = e.line
            val column = e.column
            val lineContent = fileContent[e.line!! - 1]
            val repeatCount = e.column!! + 1 + line.toString().length + 1
            println("""
                ERROR: ${e.filePath}:$line:$column
                
                $line $lineContent
                ${"-".repeat(repeatCount)}${"^".repeat(lineContent.length - repeatCount + 3)}
                
                ${e.filePath ?: ""}:$line:$column : ${e.message}
            """.trimIndent())

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