package lang.aurum.parsing

import lang.aurum.ir.*
import lang.aurum.model.*
import lang.aurum.parsing.model.MutableField
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType
import java.io.DataOutputStream
import java.nio.file.Path
import kotlin.io.path.outputStream

class ByteIrFileWriter (
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
        this.constantPool.forEach { (k, v) ->
            out.writeShort(k.ref.toInt())

            when (k) {
                is Method -> {
                    val fullName = k.owner().toUsageString()
                    val name = k.name()
                    val params = k.parameters().map { "${it.type().toUsageString()};" }
                    val returnType = "${k.returnType().toUsageString()};"
                    val str = "$fullName.$name($params)$returnType"
                    out.writeByte('M'.code)
                    out.writeByte(':'.code)
                    out.writeBytes(str)
                }
                is Field -> {
                    val fullName = k.owner().toUsageString()
                    val name = k.name()
                    val type = "${k.type().toUsageString()};"
                    val str = "$fullName.$name;$type"
                    out.writeByte('F'.code)
                    out.writeByte(':'.code)
                    out.writeBytes(str)
                }
                is Type -> {
                    out.writeByte('T'.code)
                    out.writeByte(':'.code)
                    out.writeBytes("${k.toUsageString()};")
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
        out.writeBytes("class ${toUsageString()}")

        out.writeBytes(":${superClass().toUsageString()}")
        interfaces().ifPresent {
            out.writeBytes(",")
            out.writeBytes(it.joinToString(",") { t -> t.toUsageString() })
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
            is MutableMethod -> this.write(out)
            is MutableField -> this.write(out)
        }
    }

    private fun Method.write(out: DataOutputStream) {
        if (this !is MutableMethod) return

        out.writeBytes("fn ")
        typeParameters().ifPresent { params ->
            if (params.isNotEmpty())
                out.writeBytes(params.joinToString(",", prefix = "<", postfix = ">") {
                    "${it.name()}:${it.bound().toUsageString()}"
                })
        }
        out.writeBytes("${name()}(${
            parameters().map {
                "${it.name()}:${it.type().toUsageString()};"
            }
        }):${returnType().toUsageString()};")
        attributes().forEachIndexed { i, it ->
            it.write(out)
            if (i != attributes().size-1) {
                out.writeByte(','.code)
            }
        }
        out.writeByte(';'.code)
    }

    private fun Field.write(out: DataOutputStream) {
        if (this !is MutableField) return
        
        out.writeBytes(name())
        out.writeByte(':'.code)
        out.writeBytes(type().toUsageString())
        attributes().forEachIndexed { i, it ->
            it.write(out)
            if (i != attributes().size-1) {
                out.writeByte(','.code)
            }
        }
        out.writeByte(';'.code)
    }

    private fun Attribute.write(out: DataOutputStream) {
        if (!this.isVisible) return
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

