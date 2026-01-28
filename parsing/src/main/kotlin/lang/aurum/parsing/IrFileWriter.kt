package lang.aurum.parsing

import lang.aurum.ir.IrFile
import java.nio.file.Path

abstract class IrFileWriter(
    val file: IrFile,
) {
    val constantPool = file.constantPool

    abstract fun write(out: Path)
}