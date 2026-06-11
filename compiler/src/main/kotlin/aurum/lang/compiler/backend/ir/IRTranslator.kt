package aurum.lang.compiler.backend.ir

import aurum.lang.attribute.ConstantPoolAttribute
import aurum.lang.compiler.backend.Translator
import aurum.lang.compiler.frontend.attribute.get
import aurum.lang.compiler.frontend.stages.ProcessedType
import aurum.lang.ir.*
import aurum.lang.model.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class IRTranslator(processedType: ProcessedType) : Translator<String>(processedType) {
    private val baos = ByteArrayOutputStream()
    private val out = DataOutputStream(baos)
    private val constantPool = type.attributes().get<ConstantPoolAttribute>()?.constantPool
    private var tabs = 0

    private fun DataOutputStream.write(s: String) {
        this.writeBytes("${"  ".repeat(tabs)}$s")
    }

    override fun translate(): String {
        constantPool?.let {
            write(constantPool)
        }
        write(type)
        try {
            return baos.toString()
        } finally {
            out.close()
            baos.close()
        }
    }

    private fun write(type: Type) {
        write(type.attributes())
        out.write(buildString {
            append(toString(type))
            append("class ")
            append(type.toUsageString())
        })
        if (type.typeParameters().isNotEmpty()) {
            out.write(type.typeParameters().joinToString(prefix = "<", postfix = ">") { "${it.name()} : ${it.bound().toUsageString()}" })
        }
        val supers = mutableListOf<Type>()
        if (type.superClass() != null) {
            supers += type.superClass()!!
        }
        if (type.interfaces().isNotEmpty()) {
            supers += type.interfaces()
        }
        if (supers.isNotEmpty())
            out.write(supers.joinToString(prefix = " : ") { it.toUsageString() })
        out.write("\n")

        tabs++

        type.fields().filter { it.owner() == type }.forEach(::write)
        type.methods().filter { it.owner() == type }.forEach(::write)

        tabs--
    }

    private fun write(constantPool: ConstantPool) {
        constantPool.constantPool.forEach { (ref, value) ->
            when (ref) {
                is BooleanRef -> out.write("b$ref:$value\n")
                is ByteRef -> out.write("B$ref:$value\n")
                is ShortRef -> out.write("s$ref:$value\n")
                is CharRef -> out.write("c$ref:$value\n")
                is IntRef -> out.write("i$ref:$value\n")
                is FloatRef -> out.write("f$ref:$value\n")
                is LongRef -> out.write("l$ref:$value\n")
                is DoubleRef -> out.write("d$ref:$value\n")
                is StringRef -> out.write("S$ref:$value\n")

                is TypeRef -> out.write("t$ref:${(value as Type).toUsageString()}\n")
                is MethodRef -> out.write("m$ref:${(value as Method).owner().toUsageString()}.${value.signature()}\n")
                is FieldRef -> out.write("F$ref:${(value as Field).owner().toUsageString()}.${value.name()}\n")
            }
        }
    }

    private fun write(method: Method) {
        write(method.attributes())

        out.write(buildString {
            append(toString(method))
            append(method.returnType().toUsageString())
            append(" ")
            append(method.owner().toUsageString())
            append(".")
            append(method.name())
            append(method.parameters().joinToString(prefix = "(", postfix = ")") { "${it.name()}: ${it.type().toUsageString()}" })
            append("\n")
        })
        method.attributes().get<CodeAttribute>()
            ?.let {
                tabs++
                tabs++

                it.code.forEach { inst ->
                    if (inst is LabelInst)
                        tabs--
                    out.write("$inst\n")
                    if (inst is LabelInst)
                        tabs++
                }

                tabs--
                tabs--
            }
    }

    private fun toString(accessible: Accessible): String {
        var s = ""
        if (accessible.accessFlags()?.isNotEmpty() == true) {
            s = accessible.accessFlags()?.joinToString(" ") { it.name.lowercase() } ?: ""
            s += " "
        }
        return s
    }

    private fun write(attributes: Array<Attribute>) {
        if (attributes.isEmpty()) return
        attributes.filter { it !is CodeAttribute }
            .forEach {
                if (it.values().isNotEmpty())
                    out.write("${it.values()}\n")
            }
    }

    private fun write(field: Field) {
        write(field.attributes())
        out.write(buildString {
            append(toString(field))
            append(field)
            append("\n")
        })
    }
}