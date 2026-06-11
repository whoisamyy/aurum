package aurum.lang.model.impl;

import aurum.lang.model.*;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record ArrayTypeImpl<T extends Type>(
        T componentType,
        int arrayDimensions
) implements ArrayType<T> {
    private static final Map<ArrayTypeImpl<?>, Field[]> arrayTypeFields = new ConcurrentHashMap<>();
    private static final Map<ArrayTypeImpl<?>, Method[]> arrayTypeMethods = new ConcurrentHashMap<>();

    private static final Type[] DEFAULT_ARRAY_INTERFACES =
            new Type[]{Type.ofClass(Serializable.class), Type.ofClass(Cloneable.class)};

    @Override
    public @NotNull String className() {
        return "%s%s".formatted(componentType.className(), "[]".repeat(arrayDimensions));
    }

    @Override
    public @NotNull String pkg() {
        return componentType.pkg();
    }

    @Override
    public @NotNull String fullName() {
        return componentType.fullName();
    }

    @Override
    public @NotNull Type @NotNull [] interfaces() {
        return DEFAULT_ARRAY_INTERFACES;
    }

    @Override
    public @NotNull Field[] fields() {
        return arrayTypeFields.computeIfAbsent(
                this,
                t -> new Field[]{
                    new FieldImpl(
                            t,
                            "length",
                            Types.INT,
                            Utils.EMPTY_ATTRIBUTES,
                            Utils.DEFAULT_ACCESS_FLAGS
                    )
                }
        );
    }

    @Override
    public @NotNull Method[] methods() {
        return arrayTypeMethods.computeIfAbsent(
                this,
                t -> {
                    var methods = new ArrayList<>(List.of(Types.OBJECT.methods()));
                    methods.removeIf(m -> m.name().equals("<init>"));
                    methods.add(
                            new MethodImpl(
                                    t,
                                    "<init>",
                                    t,
                                    Utils.DEFAULT_ARRAY_INIT_PARAMETERS,
                                    Utils.EMPTY_TYPES,
                                    Utils.DEFAULT_ACCESS_FLAGS,
                                    Utils.EMPTY_TYPE_PARAMETERS,
                                    Utils.EMPTY_TYPE_ARGUMENTS,
                                    Utils.EMPTY_ATTRIBUTES
                            )
                    );
                    return methods.toArray(Method[]::new);
                }
        );
    }

    @Override
    public @NotNull ArrayType<T> asArray(int dimensions) {
        return new ArrayTypeImpl<>(
                componentType,
                arrayDimensions + dimensions
        );
    }

    @Override
    @NotNull
    public Type withTypeArguments(TypeArgument @NotNull [] typeArguments) {
        return componentType.withTypeArguments(typeArguments).asArray(arrayDimensions);
    }

    @Override
    public @NotNull Type withTypeArguments(Type @NotNull [] typeArguments) {
        return componentType.withTypeArguments(typeArguments).asArray(arrayDimensions);
    }

    @Override
    public @NotNull Type withDefaultTypeArguments() {
        return componentType.withDefaultTypeArguments().asArray(arrayDimensions);
    }

    @Override
    public @NotNull AccessFlag[] accessFlags() {
        return Utils.DEFAULT_ACCESS_FLAGS;
    }

    @Override
    public @NotNull Attribute[] attributes() {
        return Utils.EMPTY_ATTRIBUTES;
    }

    @Override
    public @NotNull TypeParameter @NotNull [] typeParameters() {
        return componentType.typeParameters();
    }

    @Override
    public @NotNull TypeArgument @NotNull [] typeArguments() {
        return componentType.typeArguments();
    }

    @Override
    public @NotNull String toString() {
        return toUsageString();
    }
}
