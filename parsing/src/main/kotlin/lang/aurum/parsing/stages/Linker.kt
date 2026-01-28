package lang.aurum.parsing.stages

import lang.aurum.model.Member
import lang.aurum.model.Type
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType

abstract class Linker {
    abstract fun linkType(symbol: String, linkingContext: LinkingContext): Type
    abstract fun linkMember(symbol: String, linkingContext: LinkingContext): Member
    abstract fun link(type: MutableType?, linkingContext: LinkingContext)
    abstract fun link(method: MutableMethod?, linkingContext: LinkingContext)
}