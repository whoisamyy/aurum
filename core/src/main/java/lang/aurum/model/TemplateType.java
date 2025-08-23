package lang.aurum.model;

import lang.aurum.model.impl.Utils;

import java.lang.reflect.AccessFlag;
import java.util.Optional;

public interface TemplateType extends Type {
    @Override
    default String pkg() {
        return "";
    }

    @Override
    default Type superClass() {
        return null;
    }

    @Override
    default Optional<Type[]> interfaces() {
        return Optional.empty();
    }

    @Override
    default Field[] fields() {
        return new Field[0];
    }

    @Override
    default Method[] methods() {
        return new Method[0];
    }

    @Override
    default boolean isArray() {
        return false;
    }

    @Override
    default Type withTypeArguments(TypeArgument[] typeArguments) {
        return null;
    }

    @Override
    default AccessFlag[] accessFlags() {
        return Utils.DEFAULT_ACCESS_FLAGS;
    }

    @Override
    default boolean isAbstract() {
        return false;
    }

    @Override
    default boolean isAnnotation() {
        return false;
    }

    @Override
    default boolean isBridge() {
        return false;
    }

    @Override
    default boolean isEnum() {
        return false;
    }

    @Override
    default boolean isFinal() {
        return false;
    }

    @Override
    default boolean isInterface() {
        return false;
    }

    @Override
    default boolean isMandated() {
        return false;
    }

    @Override
    default boolean isModule() {
        return false;
    }

    @Override
    default boolean isNative() {
        return false;
    }

    @Override
    default boolean isOpen() {
        return false;
    }

    @Override
    default boolean isPrivate() {
        return false;
    }

    @Override
    default boolean isProtected() {
        return false;
    }

    @Override
    default boolean isPublic() {
        return false;
    }

    @Override
    default boolean isStatic() {
        return false;
    }

    @Override
    default boolean isStaticPhase() {
        return false;
    }

    @Override
    default boolean isStrict() {
        return false;
    }

    @Override
    default boolean isSuper() {
        return false;
    }

    @Override
    default boolean isSynchronized() {
        return false;
    }

    @Override
    default boolean isSynthetic() {
        return false;
    }

    @Override
    default boolean isTransient() {
        return false;
    }

    @Override
    default boolean isTransitive() {
        return false;
    }

    @Override
    default boolean isVarargs() {
        return false;
    }

    @Override
    default boolean isVolatile() {
        return false;
    }

    @Override
    default Attribute[] attributes() {
        return new Attribute[0];
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
        return Type.super.members();
    }
}
