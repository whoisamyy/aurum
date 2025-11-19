package lang.aurum.parsing

import lang.aurum.ir.IrFile
import lang.aurum.parsing.model.ConstantPool
import java.nio.file.Path

abstract class IrFileWriter(
    val constantPool: ConstantPool,
    val file: IrFile,
    vararg val args: Argument
) {
    abstract fun write(out: Path)
}