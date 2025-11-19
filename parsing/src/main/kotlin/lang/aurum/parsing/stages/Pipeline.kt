package lang.aurum.parsing.stages

import lang.aurum.parsing.stages.coderesolution.CodeResolutionStage
import lang.aurum.parsing.stages.optimisation.OptimizationStage

data class Pipeline(
    val stages: List<(ParsingContext) -> ParsingStage> = listOf(
        ::DependenciesResolutionStage,
        ::ClassesResolutionStage,
        ::ClassMemberResolutionStage,
        ::InternalLinkingStage,
        ::ExternalLinkingStage,
        ::DesugaringStage,
        ::CodeResolutionStage,
        ::OptimizationStage
    )
) {
    constructor(vararg stages: (ParsingContext) -> ParsingStage) : this(stages.toList())

    fun execute(context: ParsingContext) {
        stages.forEach {
            it(context).execute()
        }
    }
}