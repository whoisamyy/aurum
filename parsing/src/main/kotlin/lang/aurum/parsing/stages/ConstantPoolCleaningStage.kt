package lang.aurum.parsing.stages

import lang.aurum.ir.CodeAttribute
import lang.aurum.ir.ConstantPoolRef
import lang.aurum.ir.Instruction
import lang.aurum.ir.RValue
import lang.aurum.model.Method
import lang.aurum.parsing.attribute.get
import lang.aurum.parsing.model.ConstantPool
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType

class ConstantPoolCleaningStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    lateinit var constantPool: ConstantPool
    val allInstructions = mutableListOf<Instruction>()
    val instructionsMap = mutableMapOf<Method, MutableList<Instruction>>()

    override fun execute(fileContext: FileContext) {
        this.constantPool = fileContext.constantPool
    }

    override fun execute(method: Method) {
        val instructions = method.attributes().get<CodeAttribute>()?.code ?: mutableListOf()
        allInstructions += instructions
        instructionsMap += method to instructions
    }

    override fun afterFileContext(fileContext: FileContext) {
        val newConstantPool = ConstantPool()
        for (inst in allInstructions) {
            inst::class.memberProperties.filter {
                it.returnType.isSubtypeOf(RValue::class.starProjectedType)
            }.forEach {
                val ref = it.getter.call(inst)
                if (ref is ConstantPoolRef) {
                    if (ref !in constantPool.constantPool) return@forEach
                    val value = constantPool.dereference<Any>(ref)
                    val newRef = newConstantPool.getReference(value)
                    ref.ref = newRef.ref
                }
            }

            inst::class.memberProperties.filter {
                it.returnType.isSubtypeOf(
                    Iterable::class
                        .createType(
                            listOf(
                                KTypeProjection(
                                    KVariance.INVARIANT,
                                    RValue::class.starProjectedType
                                )
                            )
                        )
                )
            }.forEach {
                @Suppress("UNCHECKED_CAST")
                val refs = it.getter.call(inst) as? Iterable<RValue>
                refs?.forEach { ref ->
                    if (ref is ConstantPoolRef) {
                        if (ref !in constantPool.constantPool) return@forEach
                        val value = constantPool.dereference<Any>(ref)
                        val newRef = newConstantPool.getReference(value)
                        ref.ref = newRef.ref
                    }
                }
            }
        }

        constantPool.constantPool.clear()
        constantPool.references.clear()
        constantPool.constantPool += newConstantPool.constantPool
        constantPool.references += newConstantPool.references
//        constantPool.keepAll(newConstantPool.references)

//        currentFileContext.constantPool = newConstantPool

//        constantPool.constantPool.clear()
//        constantPool.references.clear()
//        constantPool.constantPool += newConstantPool.constantPool
//        constantPool.references += newConstantPool.references
    }
}