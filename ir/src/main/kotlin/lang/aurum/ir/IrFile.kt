package lang.aurum.ir

import lang.aurum.model.Member
import lang.aurum.model.Type
import java.nio.file.Path

data class IrFile(
    val srcPath: Path,
    val constantPool: ConstantPool,
    val classes: List<Type>,
    val fileMembers: List<Member>
)
