package lang.aurum.parsing.stages.memberresolution

import lang.aurum.model.Type
import lang.aurum.parsing.model.MutableType
import lang.aurum.parsing.stages.*

class EarlyLinkingStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(fileContext: FileContext) {
        val linkTable = mutableMapOf<String, Type>()
        fileContext.importMap.typeMap.values.forEach { t -> InternalLinker.link(t as? MutableType,
            LinkingContext(fileContext, linkTable)
        ) }
    }
}