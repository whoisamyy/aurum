package lang.aurum.parsing

import lang.aurum.ir.*
import lang.aurum.model.*
import lang.aurum.parsing.model.MutableField
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType
import java.io.DataOutputStream
import java.nio.file.Path
import kotlin.io.path.outputStream

class VerboseIrFileWriter (
    file: IrFile
) : IrFileWriter(file) {
    override fun write(out: Path) {
        val outStream = DataOutputStream(out.outputStream())

        constantPool.write(outStream)

        file.fileMembers.forEach {
            it.write(outStream)
        }
        file.classes.forEach {
            (it as? MutableType)?.write(outStream)
        }
    }

    private fun ConstantPool.write(out: DataOutputStream) {
        this.constantPool.forEach { (v, k) ->
            out.writeBytes("#${v.ref}")

            when (k) {
                is Method -> {
                    val fullName = k.owner().toUsageString()
                    val name = k.name()
                    val params = k.parameters().joinToString(", ") { it.type().toUsageString() }
                    val returnType = k.returnType().fullName()
                    val str = "$fullName.$name($params)$returnType"
                    out.writeBytes("M:$str")
                }
                is Field -> {
                    val fullName = k.owner().toUsageString()
                    val name = k.name()
                    val type = k.type().toUsageString()
                    val str = "$fullName.$name;$type"
                    out.writeBytes("F:$str")
                }
                is Type -> {
                    out.writeBytes("T:${k.toUsageString()}")
                }
                else -> {
                    when (v) {
                        is StringRef -> v.write(this, out)
                        is BooleanRef -> v.write(this, out)
                        is ByteRef -> v.write(this, out)
                        is ShortRef -> v.write(this, out)
                        is CharRef -> v.write(this, out)
                        is IntRef -> v.write(this, out)
                        is FloatRef -> v.write(this, out)
                        is LongRef -> v.write(this, out)
                        is DoubleRef -> v.write(this, out)
                    }
                }
            }
            out.writeBytes(";\n")
        }
    }

    private fun StringRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('S'.code)
        out.writeByte(':'.code)
        out.writeBytes(cp.dereference(this.ref.toInt()))
    }

    private fun BooleanRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('Z'.code)
        out.writeByte(':'.code)
        out.writeBytes(cp.dereference<Boolean>(this.ref.toInt()).toString())
    }

    private fun ByteRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('B'.code)
        out.writeByte(':'.code)
        out.writeBytes(cp.dereference<Byte>(this.ref.toInt()).toString())
    }

    private fun ShortRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('s'.code)
        out.writeByte(':'.code)
        out.writeBytes(cp.dereference<Short>(this.ref.toInt()).toString())
    }

    private fun CharRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('C'.code)
        out.writeByte(':'.code)
        out.writeBytes(cp.dereference<Char>(this.ref.toInt()).toString())
    }

    private fun IntRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('I'.code)
        out.writeByte(':'.code)
        out.writeBytes(cp.dereference<Int>(this.ref.toInt()).toString())
    }

    private fun FloatRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('f'.code)
        out.writeByte(':'.code)
        out.writeBytes(cp.dereference<Float>(this.ref.toInt()).toString())
    }

    private fun LongRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('L'.code)
        out.writeByte(':'.code)
        out.writeBytes(cp.dereference<Long>(this.ref.toInt()).toString())
    }

    private fun DoubleRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('D'.code)
        out.writeByte(':'.code)
        out.writeBytes(cp.dereference<Double>(this.ref.toInt()).toString())
    }

    private fun Type.write(out: DataOutputStream) {
        writeAttributes(this.attributes(), out)
        if (accessFlags().size != 0)
            out.writeBytes(accessFlags().joinToString(" ", postfix = " ") { it.name })
        out.writeBytes("class ${toUsageString()}")

        if (typeArguments().isEmpty()) {
            typeParameters().let { params ->
                if (params.size == 0)
                    return@let
                out.writeBytes(
                    params.joinToString(", ", prefix = "<", postfix = "> ") {
                        "${it.name()}:${it.bound().toUsageString()}"
                    }
                )
            }
        } else {
//            out.writeBytes(typeArguments().get().joinToString {
//                it.bound().toUsageString()
//            })
            typeParameters().let {
                out.writeBytes(it.clone().filter { param ->
                    typeArguments().any { arg -> arg.name().equals(param.name()) }
                }.joinToString(", ", prefix = "<", postfix = "> ") { param ->
                    "${param.name()}:${param.bound().toUsageString()}"
                })
            }
        }

        out.writeBytes(": ${superClass()?.toUsageString() ?: ""}")
        interfaces().let {
            if (it.size == 0)
                return@let
            out.writeBytes(", ")
            out.writeBytes(it.joinToString(", ") { inter -> inter.toUsageString()})
        }

        out.writeBytes(" {")
        fields().forEach {
            if (it !is MutableField)
                return@forEach

            out.writeBytes("\n")
            it.write(out)
        }
        methods().forEach {
            if (it !is MutableMethod)
                return@forEach

            out.writeBytes("\n")
            it.write(out)
        }
        out.writeBytes("};\n")
    }

    private fun Member.write(out: DataOutputStream) {
        when (this) {
            is MutableMethod -> this.write(out)
            is MutableField -> this.write(out)
        }
    }

    private fun MutableMethod.write(out: DataOutputStream) {
        writeAttributes(this.attributes(), out)
        if (accessFlags().isNotEmpty())
            out.writeBytes(accessFlags().joinToString(" ", postfix = " ") { it.name })

        out.writeBytes("fn ")

        typeParameters().let { params ->
            if (params.isEmpty())
                return@let
            out.writeBytes(params.joinToString(",", prefix = " <", postfix = "> ") {
                "${it.name()}:${it.bound().toUsageString()}"
            })
        }

        out.writeBytes(name())
        out.writeBytes("(")
        out.writeBytes(
            parameters().joinToString(", ") {
                "${it.name()}: ${it.type().toUsageString()}"
            }
        )
        out.writeBytes(")")
        out.writeBytes(": ${returnType().toUsageString()} ")

        if (!this.attributes().any { it is CodeAttribute }) {
            out.writeBytes("\n")
            return
        }

        out.writeByte('{'.code)
        out.writeBytes("\n")
        val code = this.attributes().find { it is CodeAttribute } as CodeAttribute

        code.code.forEach { inst ->
            if (inst !is LabelInst)
                out.writeBytes("  ")
            out.writeBytes(inst.toString())
            out.writeBytes("\n")
        }

        out.writeBytes("}\n")
    }

    private fun writeAttributes(attributes: Array<out Attribute>, out: DataOutputStream) {
        attributes.forEachIndexed { i, it ->
            if (!it.isVisible) return@forEachIndexed
            if (it is CodeAttribute) return@forEachIndexed
            it.write(out)
            if (i != attributes.size - 1) {
                out.writeByte(','.code)
            }
        }
        if (attributes.isNotEmpty())
            out.writeBytes("\n")
    }

    private fun MutableField.write(out: DataOutputStream) {
        writeAttributes(this.attributes(), out)

        if (accessFlags().isNotEmpty())
            out.writeBytes(accessFlags().joinToString(" ", postfix = " ") { it.name })
        out.writeBytes(name())
        out.writeByte(':'.code)
        out.writeBytes(type().toUsageString())
        out.writeByte(';'.code)
    }

    private fun Attribute.write(out: DataOutputStream) {
        out.writeByte('{'.code)
        out.writeBytes("name:${name()}")
        values().forEach { (name, value) ->
            out.writeBytes(",")
            out.writeBytes("$name: ")
            out.writeBytes(value.toString())
        }
        out.writeByte('}'.code)
    }
}