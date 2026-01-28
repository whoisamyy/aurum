package lang.aurum.parsing.stages.coderesolution

import lang.aurum.ir.LValue
import lang.aurum.ir.RValue
import lang.aurum.ir.Reference
import lang.aurum.model.Type
import lang.aurum.model.Types

data class Variable (
    val name: String,
    var type: Type = Types.OBJECT,
    var value: RValue? = null
) {
    internal var assignments: Int = 0

    constructor(name: String) : this(name, Types.OBJECT)

    fun toReference(): Reference.Named = Reference.Named(name) // _$assignments
    fun toLValue(): LValue = Reference.Named(name)
    fun toReferenceValue(): Value = Value(type, this.toReference())
    fun toValue(): Value = Value(type, this.value ?: this.toReference())
}
