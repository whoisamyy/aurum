package lang.aurum.ir

import lang.aurum.model.Attribute
import lang.aurum.model.Field
import lang.aurum.model.Member
import lang.aurum.model.Method
import lang.aurum.model.Type
import java.io.DataOutputStream
import java.nio.file.Path
import kotlin.io.path.outputStream

object IrFileWriter {
    fun write(file: IrFile, out: Path) {
        val outStream = DataOutputStream(out.outputStream())

        file.fileMembers.forEach {
            it.write(outStream)
        }
        file.classes.forEach {
            it.write(outStream)
        }
    }
}

private fun Type.write(out: DataOutputStream) {
    out.writeInt(intFlags())
    out.writeUTF("class ${fullName()}")

    if (typeArguments().isEmpty) {
        typeParameters().ifPresent { params ->
            out.writeUTF(
                "<${
                    params.joinToString(",") {
                        "${it.name()}:${it.bound().fullName()}"
                    }
                }>"
            )
        }
    } else {
        out.writeUTF(typeArguments().get().joinToString {
            it.bound().fullName()
        })
        typeParameters().ifPresent {
            out.writeUTF(it.clone().filter { param ->
                typeArguments().get().any { arg -> arg.name().equals(param.name()) }
            }.joinToString(",", prefix = "<", postfix = ">") { param ->
                "${param.name()}:${param.bound().fullName()}"
            })
        }
    }

    out.writeUTF(":${superClass()}")
    interfaces().ifPresent {
        out.writeUTF(",")
        out.writeUTF(it.joinToString(","))
    }

    attributes().forEachIndexed { i, it ->
        it.write(out)
        if (i != attributes().size-1) {
            out.writeByte(','.code)
        }
    }

    out.writeByte('{'.code)
    fields().forEach { it.write(out) }  
    methods().forEach { it.write(out) }
    out.writeByte('}'.code)
    out.writeByte(';'.code)
}

@Suppress("UnusedReceiverParameter", "unused")
private fun Member.write(out: DataOutputStream) {}

private fun Method.write(out: DataOutputStream) {
    out.writeUTF("fn ")
    typeParameters().ifPresent { params ->
        out.writeUTF("<${params.joinToString(",") {
            "${it.name()}:${it.bound().fullName()}"
        }}>")
    }
    out.writeUTF(" ${name()} (${
        parameters().map {
            "${name()}: ${it.type()}"
        }
    }): ${returnType()}")
    attributes().forEachIndexed { i, it ->
        it.write(out)
        if (i != attributes().size-1) {
            out.writeByte(','.code)
        }
    }
    out.writeByte(';'.code)
}

private fun Field.write(out: DataOutputStream) {
    out.writeUTF(name())
    out.writeByte(':'.code)
    out.writeUTF(type().fullName())
    attributes().forEachIndexed { i, it ->
        it.write(out)
        if (i != attributes().size-1) {
            out.writeByte(','.code)
        }
    }
    out.writeByte(';'.code)
}

private fun Attribute.write(out: DataOutputStream) {
    out.writeByte('{'.code)
    out.writeUTF("name:${name()}")
    values().forEach { (name, value) ->
        out.writeUTF(",")
        out.writeUTF("$name:")
        if (value is CodeElement)
            value.write(out)
        else
            out.writeUTF(value.toString())
    }
    out.writeByte('}'.code)
}