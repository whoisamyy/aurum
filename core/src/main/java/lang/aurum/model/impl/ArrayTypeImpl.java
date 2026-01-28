package lang.aurum.model.impl;

import lang.aurum.model.*;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.lang.reflect.AccessFlag;
import java.util.Optional;

public record ArrayTypeImpl<T extends Type>(
        T componentType,
        int arrayDimensions
) implements ArrayType<T> {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static final Optional<Type[]> DEFAULT_ARRAY_INTERFACES =
            Optional.of(new Type[]{Type.ofClass(Serializable.class), Type.ofClass(Cloneable.class)});

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
    public @NotNull Optional<Type[]> interfaces() {
        return DEFAULT_ARRAY_INTERFACES;
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
    public @NotNull Optional<TypeParameter[]> typeParameters() {
        return componentType.typeParameters();
    }

    @Override
    public @NotNull Optional<TypeArgument[]> typeArguments() {
        return componentType.typeArguments();
    }

    @Override
    public @NotNull String toString() {
        return toUsageString();
    }
}
