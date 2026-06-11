package aurum.lang.compiler.backend;

import aurum.lang.compiler.backend.ir.IRCompiler;
import aurum.lang.compiler.frontend.stages.ProcessedType;
import aurum.lang.ir.Instruction;
import aurum.lang.model.Type;
import org.jetbrains.annotations.NotNull;

import java.lang.classfile.TypeKind;

/// Translator is an object that converts [Type] into set of instructions (i.e. java bytecode).
/// @param <T> type of output that this translator emits. For IR this would be [Type].
/// @see Instruction
/// @see IRCompiler
public abstract class Translator<T> {
    public final @NotNull ProcessedType processedType;
    protected final Type type;

    protected Translator(@NotNull ProcessedType processedType) {
        this.processedType = processedType;
        this.type = processedType.getType();
    }

    public Translator<T> init() {
        return this;
    }
    abstract public T translate();

    protected static int localSlots(Type type) {
        TypeKind kind = type.typeKind();
        return kind == TypeKind.LONG || kind == TypeKind.DOUBLE ? 2 : 1;
    }
}
