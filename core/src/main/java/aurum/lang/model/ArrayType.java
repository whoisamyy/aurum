package aurum.lang.model;

import org.jetbrains.annotations.NotNull;

public interface ArrayType<T extends Type> extends Type {
    @NotNull T componentType();
    int arrayDimensions();

    @Override
    @NotNull ArrayType<T> asArray(int dimensions);

    @Override
    default Type superClass() {
        return Types.OBJECT;
    }

    @Override
    default boolean isArray() {
        return true;
    }

    @Override
    default boolean isPlainType() {
        return false;
    }
}
