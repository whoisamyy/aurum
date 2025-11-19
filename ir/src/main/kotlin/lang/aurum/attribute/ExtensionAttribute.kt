package lang.aurum.attribute

import lang.aurum.model.Attribute

interface ExtensionAttribute : Attribute {
    override fun name(): String = "Extension"
}