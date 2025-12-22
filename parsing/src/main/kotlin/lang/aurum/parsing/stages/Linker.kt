package lang.aurum.parsing.stages

import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType

abstract class Linker {
    abstract fun link(type: MutableType?, linkingContext: LinkingContext)
    abstract fun link(method: MutableMethod?, linkingContext: LinkingContext)
}