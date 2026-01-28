package lang.aurum.parsing.stages.optimisation

import lang.aurum.Argument
import lang.aurum.Arguments
import lang.aurum.Command
import lang.aurum.Option
import lang.aurum.ir.CodeAttribute
import lang.aurum.ir.Instruction
import lang.aurum.model.Member
import lang.aurum.model.Method
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.stages.FileContext
import lang.aurum.parsing.stages.ParsingContext
import lang.aurum.parsing.stages.ParsingStage
import lang.aurum.parsing.stages.optimisation.OptimisationLevel.Custom

class OptimisationStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(method: Method) {
        if (Arguments.get<OptimisationLevel>() == OptimisationLevel.ORAW) {
            return
        }
        applyOptimisations(this.currentFileContext, method)
    }

    private fun applyOptimisations(fileCtx: FileContext, member: Member) {
        val codeAttr = (member as? MutableMethod)?.attributes?.find { it is CodeAttribute } as CodeAttribute?
        if (codeAttr != null) {
            val customOptLevel = Arguments.getOrDefault(Custom())

            if (!customOptLevel.constantFolding)
                ConstantFolding.run(fileCtx, codeAttr.code)

            if (!customOptLevel.copyPropagation)
                CopyPropagation.run(fileCtx, codeAttr.code)

            if (!customOptLevel.groupRefInlining)
                GroupRefInlining.run(fileCtx, codeAttr.code)

            if (!customOptLevel.dce)
                DeadCodeElimination.run(fileCtx, codeAttr.code)
        }
    }
}

interface OptimizationPass {
    fun run(fileCtx: FileContext, instructions: MutableList<Instruction>): Boolean
}

sealed interface OptimisationLevel : Argument {
    @Option(names = ["-O1"])
    object O1 : OptimisationLevel
    @Option(names = ["-O2"])
    object O2 : OptimisationLevel
    @Option(names = ["-O3"])
    object O3 : OptimisationLevel
    @Option(names = ["-ORAW"])
    object ORAW : OptimisationLevel

    @Command(names = ["--custom-optimisation"])
    class Custom : OptimisationLevel {
        @JvmField
        @Option(names = ["--no-constant-folding"])
        var constantFolding: Boolean = false
        @JvmField
        @Option(names = ["--no-copy-propagation"])
        var copyPropagation: Boolean = false
        @JvmField
        @Option(names = ["--no-group-ref-inlining"])
        var groupRefInlining: Boolean = false
        @JvmField
        @Option(names = ["--no-dead-code-elimination", "--no-dce"])
        var dce: Boolean = false
    }
}