package lang.aurum.parsing.stages.coderesolution

import lang.aurum.ir.RValue
import lang.aurum.ir.Reference
import lang.aurum.ir.Target
import lang.aurum.model.Type

data class Variable (
    val name: String,
    var type: Type = Type.ofClass(Object::class.java),
    var value: RValue? = null
) {
    internal var assignments: Int = 0

    constructor(name: String) : this(name, Type.ofClass(Object::class.java))

    fun toReference(): Reference = Reference(name+assignments)
    fun toTarget(): Target = Target(name+assignments)
    fun toReferenceValue(): Value = Value(type, this.toReference())
    fun toValue(): Value = Value(type, this.value ?: this.toReference())
}
