@file:Suppress("ConstPropertyName")

package lang.aurum.ir

import java.io.DataInputStream
import java.io.DataOutputStream

interface CodeElement {
    fun write(out: DataOutputStream)
}

data class LabeledBlock (
    val name: String,
    val instructions: List<Instruction>
) : CodeElement {
    val size: Int = instructions.fold(name.length) { i, inst -> i + inst.size }

    override fun write(out: DataOutputStream) {
        out.writeUTF(name)
        instructions.forEach { it.write(out) }
    }
}

data class SwitchCase (
    val expectedVal: Int,
    val labelRef: Int
) : CodeElement {
    override fun write(out: DataOutputStream) {
        out.writeByte(expectedVal)
        out.writeByte(labelRef)
    }
}

abstract class Instruction (
    val code: Int,
    val size: Int
        // size can be changed for example in switch instruction
) : CodeElement

object Nop : Instruction(0xff, 1) {
    override fun write(out: DataOutputStream) {
        out.write(code)
    }
}

data class Break (
    val labelRef: Int
) : Instruction(code, 2) {
    companion object {
        const val code: Int = 0x01
    }

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(labelRef)
    }
}

data class BreakIf (
    val condRef: Int,
    val trueLabel: Int,
    val falseLabel: Int
) : Instruction(code, 4) {
    companion object {
        const val code: Int = 0x02
    }

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(condRef)
        out.writeByte(trueLabel)
        out.writeByte(falseLabel)
    }
}

data class Switch (
    val valueRef: Int,
    val cases: List<SwitchCase>,
    val defaultLabel: Int
) : Instruction(code, 4 + cases.size*2) {
    companion object {
        const val code: Int = 0x03
    }

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(cases.size)
        cases.forEach {
            it.write(out)
        }
        out.writeByte(defaultLabel)
    }
}

data class Return (
    val valueRef: Int
) : Instruction(code, 2) {
    companion object {
        const val code: Int = 0x04
    }

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(valueRef)
    }
}

data class Const (
    val target: Int,
    val typeRef: Int,
    val constRef: Int
) : Instruction(code, 4) {
    companion object {
        const val code: Int = 0x05
    }

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(target)
        out.writeByte(typeRef)
        out.writeByte(constRef)
    }
}

data class Load (
    val target: Int,
    val varRef: Int
) : Instruction(code, 3) {
    companion object {
        const val code: Int = 0x06
    }

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(target)
        out.writeByte(varRef)
    }
}

data class BinOp (
    val target: Int,
    val opRef: Int,
    val leftRef: Int,
    val rightRef: Int
) : Instruction(code, 5) {
    companion object {
        const val code: Int = 0x07
    }

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(target)
        out.writeByte(opRef)
        out.writeByte(leftRef)
        out.writeByte(rightRef)
    }
}

data class Call (
    val target: Int,
    val fnRef: Int,
    val argRefs: List<Int>?
) : Instruction(code, 4 + (argRefs?.size ?: 0)) {
    companion object {
        const val code: Int = 0x08
    }

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(target)
        out.writeByte(fnRef)
        out.writeByte(argRefs?.size ?: 0)
        argRefs?.forEach {
            out.writeByte(it)
        }
    }
}

data class Closure (
    val target: Int,
    val lambdaRef: Int,
    val envRefs: List<Int>?
) : Instruction(code, 4 + (envRefs?.size ?: 0)) {
    companion object {
        const val code: Int = 0x09
    }

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(target)
        out.writeByte(lambdaRef)
        out.writeByte(envRefs?.size ?: 0)
        envRefs?.forEach {
            out.writeByte(it)
        }
    }
}

// should be followed by constructor call (like in JVM)
data class New (
    val target: Int,
    val typeRef: Int
) : Instruction(code, 3) {
    companion object {
        const val code: Int = 0x0A
    }

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(target)
        out.writeByte(typeRef)
    }
}

data class GetField (
    val target: Int,
    val objRef: Int,
    val fieldRef: Int
) : Instruction(code, 4) {
    companion object {
        const val code: Int = 0x0B
    }

    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(target)
        out.writeByte(objRef)
        out.writeByte(fieldRef)
    }
}

data class SetField (
    val objRef: Int,
    val fieldRef: Int,
    val valueRef: Int
) : Instruction(code, 4) {
    companion object {
        const val code: Int = 0x0C
    }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(objRef)
        out.writeByte(fieldRef)
        out.writeByte(valueRef)
    }
}

data class CallMethod (
    val target: Int,
    val objRef: Int,
    val methodRef: Int,
    val argRefs: List<Int>?
) : Instruction(code, 5 + (argRefs?.size ?: 0)) {
    companion object {
        const val code: Int = 0x0D
    }
    override fun write(out: DataOutputStream) {
        out.writeByte(code)
        out.writeByte(target)
        out.writeByte(objRef)
        out.writeByte(methodRef)
        out.writeByte(argRefs?.size ?: 0)
        argRefs?.forEach {
            out.writeByte(it)
        }
    }
}

// todo.note: move and use in parser
private fun parseInstruction(input: DataInputStream): Instruction {
    return when (input.readByte().toInt()) {
        Nop.code -> Nop
        Break.code -> Break(input.readByte().toInt())
        BreakIf.code -> BreakIf(
            input.readByte().toInt(),
            input.readByte().toInt(),
            input.readByte().toInt()
        )
        Switch.code -> {
            val valRef = input.readByte()
            val casesCount = input.readByte()
            val cases: MutableList<SwitchCase> = mutableListOf()
            repeat(casesCount.toInt()) {
                cases += SwitchCase(
                    input.readByte().toInt(),
                    input.readByte().toInt()
                )
            }
            val defaultLabel = input.readByte()

            return Switch(
                valRef.toInt(),
                cases,
                defaultLabel.toInt()
            )
        }
        Return.code -> Return(input.readByte().toInt())
        Const.code -> Const(
            input.readByte().toInt(),
            input.readByte().toInt(),
            input.readByte().toInt()
        )
        Load.code -> Load(
            input.readByte().toInt(),
            input.readByte().toInt()
        )
        BinOp.code -> BinOp(
            input.readByte().toInt(),
            input.readByte().toInt(),
            input.readByte().toInt(),
            input.readByte().toInt()
        )
        Call.code -> {
            val target = input.readByte()
            val fnRef = input.readByte()

            val argC = input.readByte()
            val args: MutableList<Int> = mutableListOf()
            repeat(argC.toInt()) {
                args += input.readByte().toInt()
            }
            return Call(
                target.toInt(),
                fnRef.toInt(),
                args.toList()
            )
        }
        Closure.code -> {
            val target = input.readByte()
            val lambdaRef = input.readByte()

            val envC = input.readByte()
            val envs: MutableList<Int> = mutableListOf()
            repeat(envC.toInt()) {
                envs += input.readByte().toInt()
            }
            return Closure(
                target.toInt(),
                lambdaRef.toInt(),
                envs.toList()
            )
        }
        New.code -> New(
            input.readByte().toInt(),
            input.readByte().toInt(),
        )
        GetField.code -> GetField(
            input.readByte().toInt(),
            input.readByte().toInt(),
            input.readByte().toInt(),
        )
        SetField.code -> SetField(
            input.readByte().toInt(),
            input.readByte().toInt(),
            input.readByte().toInt(),
        )
        CallMethod.code -> {
            val target = input.readByte()
            val objRef = input.readByte()

            val methodRef = input.readByte()

            val argC = input.readByte()
            val args: MutableList<Int> = mutableListOf()
            repeat(argC.toInt()) {
                args += input.readByte().toInt()
            }
            return CallMethod(
                target.toInt(),
                objRef.toInt(),
                methodRef.toInt(),
                args.toList()
            )
        }
        else -> Nop
    }
}
