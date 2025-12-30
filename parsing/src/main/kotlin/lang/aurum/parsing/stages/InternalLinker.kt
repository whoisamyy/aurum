package lang.aurum.parsing.stages

import lang.aurum.model.Type
import lang.aurum.model.impl.Utils
import lang.aurum.parsing.model.MutableField
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType
import kotlin.io.path.Path

object InternalLinker : Linker() {
    override fun link(type: MutableType?, linkingContext: LinkingContext) {
        if (type == null)
            return
        val fullName = type.fullName()
        if (linkingContext.linkTable.containsKey(fullName)) {
            val t = linkingContext.linkTable[fullName]!!
//        this.className = t.className()
//        this.pkg = t.pkg()
            type.superClass = t.superClass()
            type.interfaces = t.interfaces().orElse(null)?.toMutableList()
            type.fields = t.fields().toMutableList()
            type.methods = t.methods().toMutableList()
            type.accessFlags = t.accessFlags().toMutableList()
            type.attributes = t.attributes().toMutableList()
            type.typeParameters = t.typeParameters().orElse(Utils.EMPTY_TYPE_PARAMETERS).toMutableList()
//            type.typeArguments = t.typeArguments().orElse(Utils.EMPTY_TYPE_ARGUMENTS).toMutableList()
            type.primitive = t.isPrimitive
            return
        }
        val parsingContext = linkingContext.fileContext.parsingContext
        if (parsingContext.classPath.contains(
                Path("${fullName.replace(".", "/")}.class")
            )) {
            linkingContext.linkTable[fullName] = type
            return // todo
        }

        if (linkWithJvm(fullName, type, linkingContext)) return

        type.interfaces?.find { !it.isInterface }?.let {
            type.superClass = it
        }
        linkingContext.linkTable[fullName] = type

        type.fields.forEach {
            if (it is MutableField && it.type is MutableType && it.type != type) {
                link(it.type as MutableType, linkingContext)
            }
        }

        type.methods.forEach {
            if (it is MutableMethod)
                link(it, linkingContext)
        }

    }

    fun linkWithJvm(
        fullName: String,
        type: MutableType,
        linkingContext: LinkingContext
    ): Boolean {
        try {
            val clazz = ClassLoader.getPlatformClassLoader().loadClass(fullName)
            val t = Type.ofClass(clazz)
    //        this.className = t.className()
    //        this.pkg = t.pkg()
            type.superClass = t.superClass()
            type.interfaces = t.interfaces().orElse(null)?.toMutableList()
    //        this.arrayDimensions = t.arrayDimensions()
            type.fields = t.fields().toMutableList()
            type.methods = t.methods().toMutableList()
            type.accessFlags = t.accessFlags().toMutableList()
            type.attributes = t.attributes().toMutableList()
            type.typeParameters = t.typeParameters().orElse(Utils.EMPTY_TYPE_PARAMETERS).toMutableList()
            type.typeArguments = t.typeArguments().orElse(Utils.EMPTY_TYPE_ARGUMENTS).toMutableList()
            type.primitive = t.isPrimitive
            linkingContext.linkTable[fullName] = type
            return true
        } catch (_: ClassNotFoundException) {
            return false
        }
    }

    override fun link(method: MutableMethod?, linkingContext: LinkingContext) {
        if (method == null)
            return
        link(method.returnType as? MutableType, linkingContext)

        method.parameters.map { it.type() }
            .forEach {
                link(it as? MutableType, linkingContext)
            }

        method.typeParameters
            ?.map { it.bound() }
            ?.forEach { link(it as? MutableType, linkingContext) }
    }
}