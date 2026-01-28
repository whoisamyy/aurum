package lang.aurum.parsing.stages

import lang.aurum.attribute.ExtensionAttribute
import lang.aurum.model.Type
import lang.aurum.parsing.attribute.contains
import lang.aurum.parsing.model.MutableField
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType

class InternalLinkingStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(fileContext: FileContext) {
        process(LinkingContext(fileContext))

        linkPackage(fileContext)
    }

    private fun linkPackage(fileContext: FileContext) {
        val types = fileContext.classes.keys.map { type -> type.className() to type }

        val staticMethods = fileContext.fileClass.methods
            .filter { m -> !m.isPrivate && !m.isProtected }
            .groupBy { m -> m.name() }
            .map { (k, v) -> k to v.toMutableSet() }

        val extensionMethods = fileContext.classes.keys.mapNotNull { type ->
            if (!type.attributes().contains<ExtensionAttribute>())
                null
            else {
                type.methods().groupBy { m -> m.name() }.map { (k, v) -> k to v.toMutableSet() }
            }
        }.flatMap { m -> m.toList() }

        val staticFields = fileContext.fileClass.fields
            .filter { m -> !m.isPrivate && !m.isProtected }
            .groupBy { m -> m.name() }
            .map { (k, v) -> k to v.toMutableSet() }

        parsingContext.files.forEach { file ->
            file.importMap.typeMap += types
            file.importMap.methodMap += staticMethods
            file.importMap.methodMap += extensionMethods
            file.importMap.fieldMap += staticFields
        }
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
