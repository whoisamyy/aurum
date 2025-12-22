package lang.aurum.model;

import lang.aurum.model.impl.PrimitiveTypeImpl;
import lang.aurum.model.impl.TemplateTypeImpl;
import lang.aurum.model.impl.Utils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;
import java.util.Optional;

public interface PrimitiveType extends Type {
    PrimitiveType VOID = PrimitiveTypeImpl.VOID;
    PrimitiveType BOOLEAN = PrimitiveTypeImpl.BOOLEAN;
    PrimitiveType BYTE = PrimitiveTypeImpl.BYTE;
    PrimitiveType SHORT = PrimitiveTypeImpl.SHORT;
    PrimitiveType CHAR = PrimitiveTypeImpl.CHAR;
    PrimitiveType INT = PrimitiveTypeImpl.INT;
    PrimitiveType FLOAT = PrimitiveTypeImpl.FLOAT;
    PrimitiveType LONG = PrimitiveTypeImpl.LONG;
    PrimitiveType DOUBLE = PrimitiveTypeImpl.DOUBLE;

    String jvmName();

    @NotNull
    @Override
    default String pkg() {
        return "";
    }

    @NotNull
    @Override
    default Type superClass() {
        return Type.ofClass(Object.class);
    }

    @NotNull
    @Override
    default Optional<Type[]> interfaces() {
        return Optional.empty();
    }

    @NotNull
    @Override
    default Type withTypeArguments(TypeArgument @NotNull [] typeArguments) {
        return this; // todo: add logging
    }

    @NotNull
    @Override
    default Type withTypeArguments(Type @NotNull [] typeArguments) {
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

    static TemplateType of(String name, int arrayDimensions) {
        return new TemplateTypeImpl(name, arrayDimensions);
    }

    static TemplateType of(String name) {
        return of(name, 0);
    }
}
