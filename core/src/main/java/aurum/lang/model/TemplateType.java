package aurum.lang.model;

import aurum.lang.model.impl.TemplateTypeImpl;
import aurum.lang.model.impl.Utils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;

public interface TemplateType extends Type {
    @NotNull
    @Override
    default String pkg() {
        return "";
    }

    @Override
    default Type superClass() {
        return null;
    }

    @NotNull
    @Override
    default Type @NotNull [] interfaces() {
        return Utils.EMPTY_TYPES;
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
    default Type withTypeArguments(TypeArgument @NotNull [] typeArguments) {
        return this;
    }

    @NotNull
    @Override
    default Type withTypeArguments(Type @NotNull [] typeArguments) {
        return this;
    }

    @Override
    default @NotNull Type withDefaultTypeArguments() {
        return this;
    }

    @NotNull
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

    @NotNull
    @Override
    default Attribute[] attributes() {
        return Utils.EMPTY_ATTRIBUTES;
    }

    @NotNull
    @Override
    default TypeParameter @NotNull [] typeParameters() {
        return Utils.EMPTY_TYPE_PARAMETERS;
    }

    @NotNull
    @Override
    default TypeArgument @NotNull [] typeArguments() {
        return Utils.EMPTY_TYPE_ARGUMENTS;
    }

    @Override
    default Member[] members() {
        return Utils.EMPTY_MEMBERS;
    }

    static TemplateType of(String name) {
        return TemplateTypeImpl.of(name);
    }
}
