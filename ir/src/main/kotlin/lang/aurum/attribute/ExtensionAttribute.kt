package lang.aurum.attribute

import lang.aurum.model.Attribute
import lang.aurum.model.Type

abstract class ExtensionAttribute() : Attribute {
    lateinit var type: Type
    override fun name(): String = "Extension"
}