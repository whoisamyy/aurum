package lang.aurum.parsing.stages.optimisation

import lang.aurum.Argument
import lang.aurum.Arguments
import lang.aurum.ir.CodeAttribute
import lang.aurum.ir.Instruction
import lang.aurum.model.Member
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.stages.FileContext
import lang.aurum.parsing.stages.ParsingContext
import lang.aurum.parsing.stages.ParsingStage

class OptimizationStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    private val action: (FileContext, Member) -> Unit = { fileCtx, member ->
        val codeAttr = (member as? MutableMethod)?.attributes?.find { it is CodeAttribute } as CodeAttribute?
        if (codeAttr != null) {
            ConstantFolding.run(fileCtx, codeAttr.code)
            CopyPropagation.run(fileCtx, codeAttr.code)
            DeadCodeElimination.run(fileCtx, codeAttr.code)
        }
    }

    override fun execute(file: FileContext) {
        if (Arguments.get<OptimisationLevel>() == OptimisationLevel.ORAW) {
            return
        }

        file.fileClass.methods.forEach { action.invoke(file, it) }
        file.classes.forEach { (k, _) ->
            k.methods().forEach { action.invoke(file, it) }
        }
    }
}

interface OptimizationPass {
    fun run(fileCtx: FileContext, instructions: MutableList<Instruction>): Boolean
}

enum class OptimisationLevel : Argument {
    O1, O2, O3, ORAW
}