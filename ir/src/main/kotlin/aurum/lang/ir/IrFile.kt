package aurum.lang.ir

import aurum.lang.model.Member
import aurum.lang.model.Type
import java.nio.file.Path

data class IrFile(
    val srcPath: Path,
    val constantPool: ConstantPool,
    val classes: List<Type>,
    val fileMembers: List<Member>
)
