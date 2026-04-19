package aurum.lang.ir

import aurum.lang.model.Field
import aurum.lang.model.Member
import aurum.lang.model.Method
import aurum.lang.model.Type
import aurum.lang.util.HashBiMap
import aurum.lang.util.MutableBiMap

class ConstantPool {
    val references: MutableSet<ConstantPoolRef> = HashSet()

    /**
     * Bidirectional mapping between [ConstantPoolRef] and the underlying value.
     *
     * Keys are instances of [ConstantPoolRef] (including [TypeRef], [SingleMethodRef],
     * [FieldRef] and primitive [ConstRef] implementations). Values are arbitrary
     * constant pool entries: primitive constants, strings, types, methods, fields, etc.
     */
    val constantPool: MutableBiMap<ConstantPoolRef, Any> = HashBiMap()

    internal fun <T : Any> addRefValue(ref: ConstantPoolRef, value: T) {
        if (references.add(ref)) {
            constantPool[ref] = value
        }
    }

    /**
     * Returns an existing constant reference for [value] if present, otherwise creates
     * a new one and stores it in the pool.
     *
     * The returned reference type depends on the runtime type of [value].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getConstant(value: T): ConstRef<T> {
        if (constantPool.containsValue(value)) {
            return constantPool.inverse[value] as ConstRef<T>
        }

        val ref = createConstant(value)
        addRefValue(ref, value)
        return ref
    }

    private fun <T : Any> createConstant(value: T): ConstRef<T> {
        val index = references.size.toUShort()
        @Suppress("UNCHECKED_CAST")
        return when (value) {
            is Boolean -> BooleanRef(index)
            is Byte -> ByteRef(index)
            is Short -> ShortRef(index)
            is Char -> CharRef(index)
            is Int -> IntRef(index)
            is Float -> FloatRef(index)
            is Long -> LongRef(index)
            is Double -> DoubleRef(index)
            is String -> StringRef(index)
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

    @Suppress("UNCHECKED_CAST")
    fun <T : Type> getReference(value: T): TypeRef {
        if (constantPool.containsValue(value)) {
            return constantPool.inverse[value] as TypeRef
        }

        val ref = TypeRef(references.size.toUShort())
        addRefValue(ref, value)
        return ref
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Method> getReference(value: T): SingleMethodRef {
        if (constantPool.containsValue(value)) {
            return constantPool.inverse[value] as SingleMethodRef
        }

        val ref = SingleMethodRef(references.size.toUShort())
        addRefValue(ref, value)
        return ref
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Field> getReference(value: T): FieldRef {
        if (constantPool.containsValue(value)) {
            return constantPool.inverse[value] as FieldRef
        }

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
    fun <T : Any> dereference(ref: ConstantPoolRef): T {
        return constantPool[ref] as T
    }

    /**
     * Dereferences a value by its raw constant pool index.
     *
     * This is implemented as a linear search over the key set because the index is
     * stored in [ConstantPoolRef.ref], not used as a direct key in [constantPool].
     * This preserves the previous behaviour while keeping the external API intact.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> dereference(index: Int): T {
        val key = constantPool.keys.firstOrNull { it.ref.toInt() == index }
            ?: error("No constant pool entry with index $index")
        return constantPool[key] as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> dereference(index: UShort): T {
        val key = constantPool.keys.firstOrNull { it.ref == index }
            ?: error("No constant pool entry with index $index")
        return constantPool[key] as T
    }

    fun remove(ref: ConstantPoolRef) = remove(ref.ref)

    fun remove(ref: UShort) {
        val key = constantPool.keys.firstOrNull { it.ref == ref } ?: return
        constantPool.remove(key)
        references.remove(key)
    }

    fun remove(ref: Int) {
        val key = constantPool.keys.firstOrNull { it.ref.toInt() == ref } ?: return
        constantPool.remove(key)
        references.remove(key)
    }

    @JvmName("removeAllRefs")
    fun removeAll(refs: Iterable<ConstantPoolRef>) = refs.forEach { remove(it) }

    @JvmName("removeAllUShorts")
    fun removeAll(refs: Iterable<UShort>) = refs.forEach { remove(it) }

    @JvmName("removeAllInts")
    fun removeAll(refs: Iterable<Int>) = refs.forEach { remove(it) }

    /**
     * Compacts the constant pool so that only [refsToKeep] remain and their indices
     * are re‑assigned to be contiguous, starting from 0, in ascending order of the
     * old indices.
     *
     * All [ConstantPoolRef] instances in [refsToKeep] are updated in place, so any
     * instructions holding references to them will see the new indices automatically.
     */
    fun compactKeeping(refsToKeep: Collection<ConstantPoolRef>) {
        if (refsToKeep.isEmpty() || constantPool.isEmpty()) {
            references.clear()
            constantPool.clear()
            return
        }

        val keepSet = refsToKeep.toSet()
        val sortedRefs = references
            .filter { it in keepSet }
            .sortedBy { it.ref }

        if (sortedRefs.isEmpty()) {
            references.clear()
            constantPool.clear()
            return
        }

        val values = sortedRefs.map { ref ->
            constantPool[ref] ?: error("Constant pool is missing value for ref $ref")
        }

        references.clear()
        constantPool.clear()

        sortedRefs.forEachIndexed { newIndex, ref ->
            ref.ref = newIndex.toUShort()
            references.add(ref)
            constantPool[ref] = values[newIndex]
        }
    }
}