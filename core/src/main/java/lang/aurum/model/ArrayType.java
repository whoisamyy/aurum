package lang.aurum.model;

import lang.aurum.model.impl.FieldImpl;
import lang.aurum.model.impl.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
    default @NotNull Field[] fields() {
        var fields = new ArrayList<>(List.of(Types.OBJECT.fields()));
        fields.add(
                new FieldImpl(
                        this,
                        "length",
                        Types.INT,
                        Utils.EMPTY_ATTRIBUTES,
                        Utils.DEFAULT_ACCESS_FLAGS
                )
        );

        return fields.toArray(Field[]::new);
    }

    @Override
    default @NotNull Method[] methods() {
        return Types.OBJECT.methods();
    }

    @Override
    default boolean isArray() {
        return true;
    }
}
