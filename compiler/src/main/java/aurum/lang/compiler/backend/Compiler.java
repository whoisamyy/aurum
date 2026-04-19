package aurum.lang.compiler.backend;

import aurum.lang.ir.ConstantPool;
import aurum.lang.model.Type;

import java.io.IOException;
import java.nio.file.Path;

public interface Compiler {
    Type type();
    ConstantPool constantPool();

    boolean compile(Path output) throws IOException;
}
