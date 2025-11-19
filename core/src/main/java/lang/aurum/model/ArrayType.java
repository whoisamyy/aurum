package lang.aurum.model;

import org.jetbrains.annotations.NotNull;

public interface ArrayType<T extends Type> extends Type {
    @NotNull T componentType();
    int arrayDimensions();

    @Override
    @NotNull ArrayType<T> asArray(int dimensions);

    @Override
    default Type superClass() {
        return Type.ofClass(Object.class);
    }

    @Override
    default @NotNull Field[] fields() {
        return Type.ofClass(Object.class).fields();
    }

    @Override
    default @NotNull Method[] methods() {
        return Type.ofClass(Object.class).methods();
    }

    @Override
    default boolean isArray() {
        return true;
    }
}
