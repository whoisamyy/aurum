package lang.aurum.ir

import lang.aurum.model.Member
import lang.aurum.model.Type

data class IrFile(
    val classes: List<Type>,
    val fileMembers: List<Member>
)
