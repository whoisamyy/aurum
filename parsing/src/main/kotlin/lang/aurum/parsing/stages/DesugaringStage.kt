package lang.aurum.parsing.stages

import lang.aurum.model.Member
import lang.aurum.parsing.antlr.AurumParser

class DesugaringStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute() {
        parsingContext.files.forEach {
            it.members.forEach { pair ->
                process(DesugaringStageContext(it, pair.first))
            }
        }
    }

    private fun process(context: DesugaringStageContext) {
        val fileCtx = context.fileContext
        desugarPackageMembers(fileCtx)
        val member = context.member
    }

    private fun desugarPackageMembers(fileCtx: FileContext) {
        fileCtx.ctx.declaration()
            .map { it.getChild(0) }
            .filter {
                it is AurumParser.FuncDeclContext
                        || it is AurumParser.VarDeclContext
                        || it is AurumParser.OperatorDeclContext
            }
            .forEach {
                val fileClass = fileCtx.fileClass
                when (it) {
                    is AurumParser.FuncDeclContext -> {
                        fileClass.resolveMember(fileCtx, it)
                    }

                    is AurumParser.VarDeclContext -> {
                        fileClass.resolveMember(fileCtx, it)
                    }

                    is AurumParser.OperatorDeclContext -> {
                        fileClass.resolveMember(fileCtx, it)
                    }
                }
            }
    }
}

data class DesugaringStageContext (
    val fileContext: FileContext,
    val member: Member // member can be created or changed
) : AbstractParsingContext()