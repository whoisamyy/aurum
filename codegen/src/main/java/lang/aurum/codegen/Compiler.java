package lang.aurum.codegen;

import lang.aurum.codegen.jvm.JVMCompiler;
import lang.aurum.ir.ConstantPool;
import lang.aurum.model.Type;

import java.io.IOException;
import java.nio.file.Path;

public interface Compiler {
    Type type();
    ConstantPool constantPool();

    boolean compile(Path output) throws IOException;

    static Compiler get(Type type, ConstantPool constantPool, Target target) {
        //noinspection SwitchStatementWithTooFewBranches
        return switch (target) {
            case JVM -> new JVMCompiler(type, constantPool);
            case IR -> new Compiler() {
                @Override
                public Type type() {
                    return null;
                }

                @Override
                public ConstantPool constantPool() {
                    return null;
                }

                @Override
                public boolean compile(Path output) throws IOException {
                    return false;
                }
            };
            default -> throw new IllegalStateException("Unknown target: " + target);
        };
    }
}
