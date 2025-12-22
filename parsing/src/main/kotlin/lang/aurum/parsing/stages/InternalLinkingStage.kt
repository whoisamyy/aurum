package lang.aurum.parsing.stages

import lang.aurum.model.Type
import lang.aurum.parsing.model.MutableField
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType

class InternalLinkingStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(fileContext: FileContext) {
        process(LinkingContext(fileContext))
    }

    private fun process(context: LinkingContext) {
        context.fileContext.importMap.typeMap.values.forEach {
            InternalLinker.link(it as? MutableType, context)
        }
        context.fileContext.classes
            .forEach { InternalLinker.link(it.key as? MutableType, context) }
        InternalLinker.link(context.fileContext.fileClass, context)
        context.fileContext.fileClass.members()
            .forEach { member ->
                when (member) {
                    is MutableMethod -> {
                        InternalLinker.link(member, context)
                    }

                    is MutableField -> {
                        InternalLinker.link(member.type as? MutableType, context)
                    }
                }
            }
//        context.fileContext.fileClass.link(context)
    }
}

data class LinkingContext(
    val fileContext: FileContext,
    val linkTable: MutableMap<String, Type> = mutableMapOf()
) : AbstractParsingContext()
//data class TypeLinkingContext ( // linker links superclass, interfaces and signature of given Type
//    override val parsingContext: ParsingContext,
//    override val fileContext: FileContext,
//    val type: Type
//) : AbstractLinkingContext(parsingContext, fileContext)
//
//// linker links method parameters, type parameters and types used in method body
//// or field's type with type arguments
//data class MemberLinkingContext (
//    override val parsingContext: ParsingContext,
//    override val fileContext: FileContext,
//    val member: Member,
//    val block: AurumParser.BlockContext
//) : AbstractLinkingContext(parsingContext, fileContext)
