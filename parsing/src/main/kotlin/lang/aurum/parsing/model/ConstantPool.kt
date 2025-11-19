package lang.aurum.parsing.model

import lang.aurum.ir.*
import lang.aurum.model.Field
import lang.aurum.model.Method
import lang.aurum.model.Type
import lang.aurum.util.LinkedHashBiMap
import lang.aurum.util.MutableBiMap

class ConstantPool() {
    val references: MutableList<ConstantPoolRef> = mutableListOf()
    val constantPool: MutableBiMap<ConstantPoolRef, Any> = LinkedHashBiMap()
    private fun <T : Any> addRefValue(ref: ConstantPoolRef, value: T) {
        references += ref
        constantPool[ref] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getConstant(value: T): ConstRef<T> {
        if (constantPool.containsValue(value))
            return constantPool.inverse[value] as ConstRef<T>

        val ref = createConstant(value)
        addRefValue(ref, value)
        return ref
    }

    private fun <T : Any> createConstant(value: T): ConstRef<T> {
        val ref = constantPool.size.toUShort()
        @Suppress("UNCHECKED_CAST")
        return when (value) {
            is Boolean -> BooleanRef(ref)
            is Byte -> ByteRef(ref)
            is Short -> ShortRef(ref)
            is Char -> CharRef(ref)
            is Int -> IntRef(ref)
            is Float -> FloatRef(ref)
            is Long -> LongRef(ref)
            is Double -> DoubleRef(ref)
            is String -> StringRef(ref)
            else -> throw IllegalStateException("Unexpected value of type ${value::class}")
        } as ConstRef<T>
    }

    fun <T : Type> getReference(value: T): TypeRef {
        if (constantPool.containsValue(value))
            return constantPool.inverse[value] as TypeRef

        val ref = TypeRef(constantPool.size.toUShort())
        addRefValue(ref, value)
        return ref
    }

    fun <T : Method> getReference(value: T): SingleMethodRef {
        if (constantPool.containsValue(value))
            return constantPool.inverse[value] as SingleMethodRef

        val ref = SingleMethodRef(constantPool.size.toUShort())
        addRefValue(ref, value)
        return ref
    }

    @ExperimentalUnsignedTypes
    fun getReference(methods: Collection<Method>): MethodGroupRef {
        return MethodGroupRef(methods.map { getReference(it).ref }.toUShortArray())
    }

    fun <T : Field> getReference(value: T): FieldRef {
        if (constantPool.containsValue(value))
            return constantPool.inverse[value] as FieldRef

        val ref = FieldRef(constantPool.size.toUShort())
        addRefValue(ref, value)
        return ref
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> dereference(index: Int): T {
        return constantPool[references.filterIndexed { i, _ -> i == index }.first()] as T
    }
}