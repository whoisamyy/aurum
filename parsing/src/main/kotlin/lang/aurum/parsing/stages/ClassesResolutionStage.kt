package lang.aurum.parsing.stages

import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.attribute.ExtensionAttributeImpl
import lang.aurum.parsing.model.MutableTypePool
import java.lang.reflect.AccessFlag

class ClassesResolutionStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(fileContext: FileContext) {
        process(fileContext)
    }

    private fun process(context: FileContext) {
        context.ctx.declaration()
            .filter {
                val child = it.getChild(0)
                return@filter child is AurumParser.ExtensionDeclContext
                        || child is AurumParser.ClassDeclContext
                        || child is AurumParser.InterfaceDeclContext
                        || child is AurumParser.DecoratorDeclContext
            }
            .forEach {
                when (val decl = it.getChild(0)) {
                    is AurumParser.ClassDeclContext -> {
                        context.classes += MutableTypePool.get(
                            decl.Identifier().text,
                            context.pkg,
                            accessFlags = decl.modifier()?.toAccessFlags() ?: mutableListOf()
                        ) to ClassDeclCtx(decl)
                    }

                    is AurumParser.InterfaceDeclContext -> {
                        val accessFlags = decl.modifier()?.toAccessFlags() ?: mutableListOf()
                        accessFlags += AccessFlag.INTERFACE
                        accessFlags += AccessFlag.ABSTRACT
                        context.classes += MutableTypePool.get(
                            decl.Identifier().text,
                            context.pkg,
                            accessFlags = accessFlags,
                        ) to InterfaceDeclCtx(decl)
                    }

                    is AurumParser.DecoratorDeclContext -> {
                        context.classes += MutableTypePool.get(
                            decl.Identifier().text,
                            context.pkg,
                            accessFlags = decl.modifier()?.toAccessFlags() ?: mutableListOf()
                        ) to DecoratorDeclCtx(decl)
                    }

                    is AurumParser.ExtensionDeclContext -> {
                        context.classes += MutableTypePool.get(
                            "extension$${decl.typeExpr().text}",
                            context.pkg,
                            accessFlags = decl.modifier()?.toAccessFlags() ?: mutableListOf(),
                            attributes = mutableListOf(ExtensionAttributeImpl(decl.typeExpr()))
                        ) to ExtensionDeclCtx(decl)
                    }
                }
            }
//        if (context.ctx.declaration()
//                .map { it.getChild(0) }
//                .any {
//                    it is AurumParser.FuncDeclContext
//                            || it is AurumParser.VarDeclContext
//                            || it is AurumParser.OperatorDeclContext
//                }
//        ) {
//        }
    }
}

fun Collection<AurumParser.ModifierContext>.toAccessFlags(): MutableList<AccessFlag> {
    return this.map {
        return@map when (it) {
            is AurumParser.PublicModContext -> AccessFlag.PUBLIC
            is AurumParser.PrivateModContext -> AccessFlag.PRIVATE
            is AurumParser.ProtectedModContext -> AccessFlag.PROTECTED
            is AurumParser.StaticModContext -> AccessFlag.STATIC
            is AurumParser.FinalModContext -> AccessFlag.FINAL
            is AurumParser.AbstractModContext -> AccessFlag.ABSTRACT
            else -> throw IllegalStateException("Incorrect modifier was provided: ${it.text}")
        }
    }.toMutableList()
}