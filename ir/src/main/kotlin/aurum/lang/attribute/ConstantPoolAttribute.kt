package aurum.lang.attribute

import aurum.lang.ir.ConstantPool
import aurum.lang.model.Attribute

data class ConstantPoolAttribute (
    val constantPool: ConstantPool
) : Attribute {
    override fun isVisible(): Boolean = false
    override fun name(): String = "ConstantPoolAttribute"

    override fun values(): Map<String, Any?> = mapOf(
        "constantPool" to constantPool
    )

}