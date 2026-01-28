package lang.aurum.model;

import lang.aurum.model.impl.Utils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;
import java.util.Optional;

public interface PrimitiveType extends Type {
    String jvmName();
    Type boxed();

    @NotNull
    @Override
    default String pkg() {
        return "";
    }

    @NotNull
    @Override
    default Type superClass() {
        return Types.OBJECT;
    }


    @Override
    default @NotNull Optional<@NotNull Type @NotNull []> interfaces() {
        return Optional.empty();
    }

    @Override
    default @NotNull PrimitiveType withTypeArguments(@NotNull TypeArgument @NotNull [] typeArguments) {
        return this; // todo: add logging
    }

    @Override
    default @NotNull PrimitiveType withTypeArguments(@NotNull Type @NotNull [] typeArguments) {
        return this;
    }

    @Override
    default @NotNull PrimitiveType withDefaultTypeArguments() {
        return this;
    }

    @NotNull
    @Override
    default Type asArrayWithTypeArguments(int dimensions, TypeArgument[] typeArguments) {
        return Type.super.asArrayWithTypeArguments(dimensions, typeArguments);
    }

    @Override
    default boolean isPrimitive() {
        return true;
    }

    @NotNull
    @Override
    default Optional<TypeParameter[]> typeParameters() {
        return Optional.empty();
    }

    @NotNull
    @Override
    default Optional<TypeArgument[]> typeArguments() {
        return Optional.empty();
    }

    @Override
    default Member[] members() {
        return Utils.EMPTY_MEMBERS;
    }

    @NotNull
    @Override
    default Field[] fields() {
        return Utils.EMPTY_FIELDS;
    }

    @NotNull
    @Override
    default Method[] methods() {
        return Utils.EMPTY_METHODS;
    }

    @NotNull
    @Override
    default AccessFlag[] accessFlags() {
        return Utils.DEFAULT_ACCESS_FLAGS;
    }

    @NotNull
    @Override
    default Attribute[] attributes() {
        return Utils.EMPTY_ATTRIBUTES;
    }
}
