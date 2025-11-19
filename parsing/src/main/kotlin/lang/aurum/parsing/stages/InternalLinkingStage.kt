package lang.aurum.parsing.stages

import lang.aurum.model.Type
import lang.aurum.parsing.model.MutableField
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType
import kotlin.io.path.Path

class InternalLinkingStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute() {
        parsingContext.files.forEach {
            process(LinkingContext(it))
        }
    }

    private fun process(context: LinkingContext) {
        context.fileContext.typeImportMap.values.forEach {
            (it as? MutableType)?.link(context)
        }
        context.fileContext.classes
            .forEach { (it.key as? MutableType)?.link(context) }
        context.fileContext.fileClass.link(context)
        context.fileContext.members
            .forEach {
                when (val member = it.first) {
                    is MutableMethod -> {
                        member.link(context)
                    }

                    is MutableField -> {
                        (member.type as? MutableType)?.link(context)
                    }
                }
            }
//        context.fileContext.fileClass.link(context)
    }
}

private fun MutableType.link(linkingContext: LinkingContext) {
    val fullName = fullName()
    if (linkingContext.linkTable.containsKey(fullName)) {
        val t = linkingContext.linkTable[fullName]!!
//        this.className = t.className()
//        this.pkg = t.pkg()
        this.superClass = t.superClass()
        this.interfaces = t.interfaces().orElse(null)?.toMutableList()
        this.fields = t.fields().toMutableList()
        this.methods = t.methods().toMutableList()
        this.accessFlags = t.accessFlags().toMutableList()
        this.attributes = t.attributes().toMutableList()
        this.typeParameters = t.typeParameters().orElse(null)?.toMutableList()
        this.typeArguments = t.typeArguments().orElse(null)?.toMutableList()
        this.primitive = t.isPrimitive
        return
    }
    val parsingContext = linkingContext.fileContext.parsingContext
    if (parsingContext.classPath.contains(
            Path("${fullName.replace(".", "/")}.class")
        )) {
        linkingContext.linkTable[fullName] = this
        return // todo
    }

    try {
        val clazz = ClassLoader.getPlatformClassLoader().loadClass(fullName)
        val t = Type.ofClass(clazz)
//        this.className = t.className()
//        this.pkg = t.pkg()
        this.superClass = t.superClass()
        this.interfaces = t.interfaces().orElse(null)?.toMutableList()
//        this.arrayDimensions = t.arrayDimensions()
        this.fields = t.fields().toMutableList()
        this.methods = t.methods().toMutableList()
        this.accessFlags = t.accessFlags().toMutableList()
        this.attributes = t.attributes().toMutableList()
        this.typeParameters = t.typeParameters().orElse(null)?.toMutableList()
        this.typeArguments = t.typeArguments().orElse(null)?.toMutableList()
        this.primitive = t.isPrimitive
        linkingContext.linkTable[fullName] = this
        return
    } catch (_: ClassNotFoundException) {}

    val classes = linkingContext.fileContext.classes
    interfaces?.find { !it.isInterface }?.let {
        this.superClass = it
    }
    linkingContext.linkTable[fullName] = this
//            val ctx = classes.first { it.first.fullName() == fullName }.second

    fields.forEach {
        if (it is MutableField && it.type is MutableType && it.type != this) {
            (it.type as MutableType).link(linkingContext)
        }
    }

    methods.forEach {
        if (it is MutableMethod)
            it.link(linkingContext)
    }

}

private fun MutableMethod.link(linkingContext: LinkingContext) {
    (returnType as? MutableType)?.link(linkingContext)

    parameters.map { it.type() }
        .forEach {
            (it as? MutableType)?.link(linkingContext)
        }

    typeParameters
        ?.map { it.bound() }
        ?.forEach { (it as? MutableType)?.link(linkingContext) }
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
