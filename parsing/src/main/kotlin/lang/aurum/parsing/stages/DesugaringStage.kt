package lang.aurum.parsing.stages

import lang.aurum.ir.CodeAttribute
import lang.aurum.ir.Reference
import lang.aurum.model.Member
import lang.aurum.model.Method
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.attribute.PrimaryConstructorAttribute
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.stages.coderesolution.IRCompiler

class DesugaringStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(fileCtx: FileContext) {
        fileCtx.classes.keys.flatMap { t -> t.members().toList() }
            .forEach { m ->
                process(DesugaringStageContext(fileCtx, m))
            }
    }

    private fun process(context: DesugaringStageContext) {
        desugarImplicitConstructors(context)
    }

    private fun desugarImplicitConstructors(ctx: DesugaringStageContext) {
        if (ctx.member !is Method || ctx.member.attributes().none { it is PrimaryConstructorAttribute })
            return

        val constructor = ctx.member as MutableMethod
        val compiler = IRCompiler(ctx.fileContext, constructor)
        val params = constructor.parameters()
        val owner = constructor.owner()
        for (field in params) {
            compiler.generator.putField(
                Reference("this"),
                compiler.constantPool.getReference(owner.findField(field.name()).orElseThrow()),
                compiler.currentScope[field.name()]!!.toReference()
            )
        }

        constructor.attributes += CodeAttribute(compiler.instructions)
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
//                        fileClass.resolveMember(fileCtx, it)
                    }

                    is AurumParser.VarDeclContext -> {
//                        fileClass.resolveMember(fileCtx, it)
                    }

                    is AurumParser.OperatorDeclContext -> {
//                        fileClass.resolveMember(fileCtx, it)
                    }
                }
            }
    }
}

data class DesugaringStageContext (
    val fileContext: FileContext,
    val member: Member // member can be created or changed
) : AbstractParsingContext()