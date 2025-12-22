package lang.aurum.parsing.model

import lang.aurum.ir.*
import lang.aurum.model.Field
import lang.aurum.model.Member
import lang.aurum.model.Method
import lang.aurum.model.Type
import lang.aurum.util.HashBiMap
import lang.aurum.util.MutableBiMap

class ConstantPool() {
    val references: MutableSet<ConstantPoolRef> = HashSet()
    val constantPool: MutableBiMap<ConstantPoolRef, Any> = HashBiMap()
    internal fun <T : Any> addRefValue(ref: ConstantPoolRef, value: T) {
        if (references.add(ref)) constantPool[ref] = value
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

    fun <T : Any> getReference(value: T): ConstantPoolRef {
        return when (value) {
            is Type -> getReference(value)
            is Method -> getReference(value)
            is Field -> getReference(value)
            else -> getConstant(value)
        }
    }

    fun <T : Type> getReference(value: T): TypeRef {
        if (constantPool.containsValue(value))
            return constantPool.inverse[value] as TypeRef

        val ref = TypeRef(references.size.toUShort())
        addRefValue(ref, value)
        return ref
    }

    fun <T : Method> getReference(value: T): SingleMethodRef {
        if (constantPool.containsValue(value))
            return constantPool.inverse[value] as SingleMethodRef

        val ref = SingleMethodRef(references.size.toUShort())
        addRefValue(ref, value)
        return ref
    }

    fun <T : Field> getReference(value: T): FieldRef {
        if (constantPool.containsValue(value))
            return constantPool.inverse[value] as FieldRef

        val ref = FieldRef(references.size.toUShort())
        addRefValue(ref, value)
        return ref
    }

    fun getReference(methods: Iterable<Method>): MethodGroupRef {
        return MethodGroupRef(methods.map { getReference(it) })
    }

    fun getReference(fields: Iterable<Field>): FieldGroupRef {
        return FieldGroupRef(fields.map { getReference(it) })
    }

    fun getReference(members: Iterable<Member>): MemberGroupRef {
        return MemberGroupRef(members.map { getReference(it) as MemberRef })
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Member> dereference(ref: MemberGroupRef): List<T> {
        return ref.refs.map { dereference<Member>(it as ConstantPoolRef) as T }
    }


    @Suppress("UNCHECKED_CAST")
    fun <T : Field> dereference(ref: FieldGroupRef): List<T> {
        return ref.refs.map { dereference<Field>(it as ConstantPoolRef) as T }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Method> dereference(ref: MethodGroupRef): List<T> {
        return ref.refs.map { dereference<Method>(it as ConstantPoolRef) as T }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> dereference(ref: ConstantPoolRef): T = dereference(ref.ref)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> dereference(index: Int): T {
        return constantPool.filter { (k, _) -> k.ref.toInt() == index }.values.first() as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> dereference(index: UShort): T {
        return constantPool.filter { (k, _) -> k.ref == index }.values.first() as T
    }

    fun remove(ref: ConstantPoolRef) = remove(ref.ref)

    fun remove(ref: UShort) {
        val key = constantPool.keys.find { it.ref == ref }
        constantPool.remove(key)
        references.remove(key)
    }

    fun remove(ref: Int) {
        val key = constantPool.keys.find { it.ref == ref.toUShort() }
        constantPool.remove(key)
        references.remove(key)
    }

    @JvmName("removeAllRefs")
    fun removeAll(refs: Iterable<ConstantPoolRef>) = refs.forEach { remove(it) }
    @JvmName("removeAllUShorts")
    fun removeAll(refs: Iterable<UShort>) = refs.forEach { remove(it) }
    @JvmName("removeAllInts")
    fun removeAll(refs: Iterable<Int>) = refs.forEach { remove(it) }

    @JvmName("keepAllRefs")
    fun keepAll(refs: Collection<ConstantPoolRef>) {
        references.retainAll(refs.toSet())
        constantPool.keys.retainAll(references)
    }

    @JvmName("keepAllInts")
    fun keepAll(refs: Collection<Int>) {
        references.retainAll {
            it.ref.toInt() in refs
        }
        constantPool.keys.retainAll(references)
    }

    @JvmName("keepAllUShorts")
    fun keepAll(refs: Collection<UShort>) {
        references.retainAll {
            it.ref in refs
        }
        constantPool.keys.retainAll(references)
    }
}
