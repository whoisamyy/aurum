package lang.aurum.parsing

import lang.aurum.ir.IrFile
import lang.aurum.parsing.model.ConstantPool
import java.nio.file.Path

abstract class IrFileWriter(
    val constantPool: ConstantPool,
    val file: IrFile,
) {
    abstract fun write(out: Path)
}