package lang.aurum.parsing.stages

import lang.aurum.attribute.ExtensionAttribute
import lang.aurum.model.Type
import lang.aurum.model.Types
import lang.aurum.parsing.attribute.contains
import lang.aurum.parsing.model.MutableType

class CodeCleaningStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(type: Type?) {
        if (type !is MutableType)
            return

        if (!type.attributes.contains<ExtensionAttribute>())
            return

        if (type.superClass!!.isPrimitive)
            type.superClass = Types.OBJECT
    }
}