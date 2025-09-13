package lang.aurum.model;

import lang.aurum.model.impl.PrimitiveTypeImpl;
import lang.aurum.model.impl.TemplateTypeImpl;
import lang.aurum.model.impl.Utils;

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

    @Override
    default String pkg() {
        return "";
    }

    @Override
    default Type superClass() {
        return Type.ofClass(Object.class);
    }

    @Override
    default Optional<Type[]> interfaces() {
        return Optional.empty();
    }

    @Override
    default Type withTypeArguments(TypeArgument[] typeArguments) {
        return this; // todo: add logging
    }

    @Override
    default Type withTypeArguments(Type[] typeArguments) {
        return this;
    }

    @Override
    default Type asArrayWithTypeArguments(int dimensions, TypeArgument[] typeArguments) {
        return Type.super.asArrayWithTypeArguments(dimensions, typeArguments);
    }

    @Override
    default boolean isPrimitive() {
        return true;
    }

    @Override
    default int arrayDimensions() {
        return 0;
    }

    @Override
    default boolean isArray() {
        return false;
    }

    @Override
    default Optional<TypeParameter[]> typeParameters() {
        return Optional.empty();
    }

    @Override
    default Optional<TypeArgument[]> typeArguments() {
        return Optional.empty();
    }

    @Override
    default Member[] members() {
        return Utils.EMPTY_MEMBERS;
    }

    @Override
    default Field[] fields() {
        return Utils.EMPTY_FIELDS;
    }

    @Override
    default Method[] methods() {
        return Utils.EMPTY_METHODS;
    }

    @Override
    default AccessFlag[] accessFlags() {
        return Utils.DEFAULT_ACCESS_FLAGS;
    }

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
