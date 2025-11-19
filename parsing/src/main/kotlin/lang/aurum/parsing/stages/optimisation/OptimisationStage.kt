package lang.aurum.parsing.stages.optimisation

import lang.aurum.ir.CodeAttribute
import lang.aurum.ir.Instruction
import lang.aurum.model.Member
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.stages.FileContext
import lang.aurum.parsing.stages.ParsingContext
import lang.aurum.parsing.stages.ParsingStage

class OptimizationStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute() {
        val action: (FileContext, Member) -> Unit = { fileCtx, member ->
            val codeAttr = (member as? MutableMethod)?.attributes?.find { it is CodeAttribute } as CodeAttribute?
            if (codeAttr != null) {
                ConstantFolding.run(fileCtx, codeAttr.code)
                CopyPropagation.run(fileCtx, codeAttr.code)
                CallOnClosure.run(fileCtx, codeAttr.code)
                DeadCodeElimination.run(fileCtx, codeAttr.code)
            }
        }
        parsingContext.files.forEach { file ->
            file.fileClass.methods.forEach { action.invoke(file, it) }
            file.members.map { it.first }.forEach { action.invoke(file, it) }
            file.classes.forEach { (k, _) ->
                k.methods().forEach { action.invoke(file, it) }
            }
        }
    }
}

interface OptimizationPass {
    fun run(fileCtx: FileContext, instructions: MutableList<Instruction>): Boolean
}

enum class OptimizationLevel {
    O1, O2, O3
}

data class OptimizationContext(
    val fileContext: FileContext,
    val optimisationLevel: OptimizationLevel
)