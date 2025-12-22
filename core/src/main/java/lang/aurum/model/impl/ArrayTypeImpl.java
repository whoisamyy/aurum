package lang.aurum.model.impl;

import lang.aurum.model.*;
import org.jetbrains.annotations.NotNull;

import java.lang.classfile.TypeKind;
import java.lang.reflect.AccessFlag;
import java.util.Optional;

public record ArrayTypeImpl<T extends Type>(
        T componentType,
        int arrayDimensions
) implements ArrayType<T> {
    @Override
    public @NotNull String className() {
        return componentType.className();
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
        return componentType.interfaces();
    }

    @Override
    public boolean isPrimitive() {
        return componentType.isPrimitive();
    }

    @Override
    public boolean isArray() {
        return componentType.isArray();
    }

    @Override
    public boolean isSubclassOf(Type other) {
        return componentType.isSubclassOf(other);
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
        return componentType.withTypeArguments(typeArguments);
    }

    @Override
    public @NotNull Type withTypeArguments(Type @NotNull [] typeArguments) {
        return componentType.withTypeArguments(typeArguments);
    }

    @Override
    public @NotNull TypeKind typeKind() {
        return componentType.typeKind();
    }

    @Override
    public @NotNull Optional<Method> findMethodExact(String name, Type returnType, Type... parameterTypes) {
        return componentType.findMethodExact(name, returnType, parameterTypes);
    }

    @Override
    @NotNull
    public Optional<Method> findMethodExact(String name, Type... parameterTypes) {
        return componentType.findMethodExact(name, parameterTypes);
    }

    @Override
    @NotNull
    public Optional<Method> findMethodExact(String name, Type returnType) {
        return componentType.findMethodExact(name, returnType);
    }

    @Override
    @NotNull
    public Method[] getMethodsExact(String name, Type returnType) {
        return componentType.getMethodsExact(name, returnType);
    }

    @Override
    @NotNull
    public Optional<Method> findMethod(String name, Type returnType, Type... parameterTypes) {
        return componentType.findMethod(name, returnType, parameterTypes);
    }

    @Override
    @NotNull
    public Optional<Method> findMethod(String name, Type[] parameterTypes) {
        return componentType.findMethod(name, parameterTypes);
    }

    @Override
    @NotNull
    public Optional<Method> findMethod(String name, Type returnType) {
        return componentType.findMethod(name, returnType);
    }

    @Override
    @NotNull
    public Optional<Method> findMethod(String name) {
        return componentType.findMethod(name);
    }

    @Override
    @NotNull
    public Method[] getMethods(String name, Type returnType) {
        return componentType.getMethods(name, returnType);
    }

    @Override
    @NotNull
    public Method[] getMethods(String name) {
        return componentType.getMethods(name);
    }

    @Override
    public @NotNull Optional<Field> findField(String name) {
        return componentType.findField(name);
    }

    @Override
    public @NotNull AccessFlag[] accessFlags() {
        return componentType.accessFlags();
    }

    @Override
    public @NotNull Attribute[] attributes() {
        return componentType.attributes();
    }

    @Override
    public @NotNull Optional<TypeParameter[]> typeParameters() {
        return componentType.typeParameters();
    }

    @Override
    public @NotNull Optional<TypeArgument[]> typeArguments() {
        return componentType.typeArguments();
    }
}
