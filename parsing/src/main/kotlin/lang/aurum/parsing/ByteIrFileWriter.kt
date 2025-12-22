package lang.aurum.parsing

import lang.aurum.ir.*
import lang.aurum.model.*
import lang.aurum.parsing.model.ConstantPool
import lang.aurum.parsing.model.MutableType
import java.io.DataOutputStream
import java.nio.file.Path
import kotlin.io.path.outputStream

class ByteIrFileWriter (
    constantPool: ConstantPool,
    file: IrFile,
    vararg args: Argument
) : IrFileWriter(constantPool, file, *args) {
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
        this.constantPool.forEach { (k, v) ->
            out.writeShort(k.ref.toInt())

            when (k) {
                is Method -> {
                    val fullName = k.owner().fullName()
                    val name = k.name()
                    val params = k.parameters().map { "${it.type().fullName()};" }
                    val returnType = "${k.returnType().fullName()};"
                    val str = "$fullName.$name($params)$returnType"
                    out.writeByte('M'.code)
                    out.writeByte(':'.code)
                    out.writeBytes(str)
                }
                is Field -> {
                    val fullName = k.owner().fullName()
                    val name = k.name()
                    val type = "${k.type().fullName()};"
                    val str = "$fullName.$name;$type"
                    out.writeByte('F'.code)
                    out.writeByte(':'.code)
                    out.writeBytes(str)
                }
                is Type -> {
                    out.writeByte('T'.code)
                    out.writeByte(':'.code)
                    out.writeBytes("${k.fullName()};")
                }
                else -> when (v) {
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
        out.writeByte(';'.code)
    }

    private fun StringRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('S'.code)
        out.writeByte(':'.code)
        out.writeBytes(cp.dereference(this.ref.toInt()))
    }

    private fun BooleanRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('Z'.code)
        out.writeByte(':'.code)
        out.writeBoolean(cp.dereference(this.ref.toInt()))
    }

    private fun ByteRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('B'.code)
        out.writeByte(':'.code)
        out.writeByte(cp.dereference(this.ref.toInt()))
    }

    private fun ShortRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('s'.code)
        out.writeByte(':'.code)
        out.writeShort(cp.dereference(this.ref.toInt()))
    }

    private fun CharRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('C'.code)
        out.writeByte(':'.code)
        out.writeChar(cp.dereference(this.ref.toInt()))
    }

    private fun IntRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('I'.code)
        out.writeByte(':'.code)
        out.writeInt(cp.dereference(this.ref.toInt()))
    }

    private fun FloatRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('f'.code)
        out.writeByte(':'.code)
        out.writeFloat(cp.dereference(this.ref.toInt()))
    }

    private fun LongRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('L'.code)
        out.writeByte(':'.code)
        out.writeLong(cp.dereference(this.ref.toInt()))
    }

    private fun DoubleRef.write(cp: ConstantPool, out: DataOutputStream) {
        out.writeByte('D'.code)
        out.writeByte(':'.code)
        out.writeDouble(cp.dereference(this.ref.toInt()))
    }

    private fun Type.write(out: DataOutputStream) {
        out.writeInt(intFlags())
        out.writeBytes("class ${fullName()}")

        if (typeArguments().isEmpty) {
            typeParameters().ifPresent { params ->
                out.writeBytes(
                    "<${
                        params.joinToString(",") {
                            "${it.name()}:${it.bound().fullName()}"
                        }
                    }>"
                )
            }
        } else {
            out.writeBytes(typeArguments().get().joinToString {
                it.bound().fullName()
            })
            typeParameters().ifPresent {
                out.writeBytes(it.clone().filter { param ->
                    typeArguments().get().any { arg -> arg.name().equals(param.name()) }
                }.joinToString(",", prefix = "<", postfix = ">") { param ->
                    "${param.name()}:${param.bound().fullName()}"
                })
            }
        }

        out.writeBytes(":${superClass().fullName()}")
        interfaces().ifPresent {
            out.writeBytes(",")
            out.writeBytes(it.joinToString(","))
        }

        attributes().forEachIndexed { i, it ->
            if (!it.isVisible) return@forEachIndexed
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

    private fun Member.write(out: DataOutputStream) {
        when (this) {
            is Method -> this.write(out)
            is Field -> this.write(out)
        }
    }

    private fun Method.write(out: DataOutputStream) {
        out.writeBytes("fn ")
        typeParameters().ifPresent { params ->
            out.writeBytes("<${params.joinToString(",") {
                "${it.name()}:${it.bound().fullName()}"
            }}>")
        }
        out.writeBytes("${name()}(${
            parameters().map {
                "${name()}:${it.type().fullName()};"
            }
        }):${returnType().fullName()};")
        attributes().forEachIndexed { i, it ->
            it.write(out)
            if (i != attributes().size-1) {
                out.writeByte(','.code)
            }
        }
        out.writeByte(';'.code)
    }

    private fun Field.write(out: DataOutputStream) {
        out.writeBytes(name())
        out.writeByte(':'.code)
        out.writeBytes(type().fullName())
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
        out.writeBytes("name:${name()}")
        values().forEach { (name, value) ->
            out.writeBytes(",")
            out.writeBytes("$name:")
            if (value is Instruction)
                value.write(out)
            else if (value is Collection<*>) {
                if (value.all { it is Instruction })
                    value.forEach { (it as Instruction).write(out) }
            }
            else
                out.writeBytes(value::class.qualifiedName!!)
        }
        out.writeByte('}'.code)
    }
}

