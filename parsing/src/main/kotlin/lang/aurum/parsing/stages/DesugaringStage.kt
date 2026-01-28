package lang.aurum.parsing.stages

import lang.aurum.ir.CodeAttribute
import lang.aurum.ir.Reference
import lang.aurum.model.Member
import lang.aurum.model.Method
import lang.aurum.model.Type
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.attribute.PrimaryConstructorAttribute
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType
import lang.aurum.parsing.stages.coderesolution.IRCompiler
import java.lang.reflect.AccessFlag

class DesugaringStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(fileCtx: FileContext) {
        fileCtx.classes.keys.forEach { t ->
            t.members().forEach { m ->
                desugariseMember(MemberDesugaringContext(fileCtx, m))
            }
            desugariseType(TypeDesugaringContext(fileCtx, t));
        }
    }

    private fun desugariseMember(context: MemberDesugaringContext) {
        desugarImplicitConstructors(context)
    }

    private fun desugariseType(ctx: TypeDesugaringContext) {
        createImplicitConstructors(ctx)
    }

    private fun createImplicitConstructors(ctx: TypeDesugaringContext) {
        val type = ctx.type
        if (type !is MutableType)
            return

        type.methods().filter { it.name() == "<init>" && it.owner() == type }
            .ifEmpty {
                val constructor = MutableMethod(
                    type,
                    "<init>",
                    accessFlags = mutableListOf(AccessFlag.PUBLIC)
                )
                type.methods += constructor

                val compiler = IRCompiler(ctx.fileContext, constructor)

                compiler.generator.invokeConstructor(Reference.Super)
                compiler.generator.return_()

                constructor.attributes += CodeAttribute(compiler.instructions)
            }
    }

    private fun desugarImplicitConstructors(ctx: MemberDesugaringContext) {
        if (ctx.member !is Method || ctx.member.attributes().none { it is PrimaryConstructorAttribute })
            return

        val constructor = ctx.member as MutableMethod
        val compiler = IRCompiler(ctx.fileContext, constructor)
        val params = constructor.parameters()
        val owner = constructor.owner()
        for (field in params) {
            compiler.generator.putField(
                Reference.This,
                compiler.constantPool.getReference(owner.findField(field.name()).orElseThrow()),
                compiler.currentScope[field.name()]!!.toReference()
            )
        }
        compiler.generator.return_()

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

data class MemberDesugaringContext (
    val fileContext: FileContext,
    val member: Member // member can be created or changed
) : AbstractParsingContext()

data class TypeDesugaringContext (
    val fileContext: FileContext,
    val type: Type // member can be created or changed
) : AbstractParsingContext()