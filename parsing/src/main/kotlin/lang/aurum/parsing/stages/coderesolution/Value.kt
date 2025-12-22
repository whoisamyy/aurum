package lang.aurum.parsing.stages.coderesolution

import lang.aurum.ir.RValue
import lang.aurum.model.Type

data class Value (
    val type: Type,
    val value: RValue
)