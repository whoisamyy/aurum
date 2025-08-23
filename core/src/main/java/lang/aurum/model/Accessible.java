package lang.aurum.model;

import java.lang.reflect.AccessFlag;
import java.util.Arrays;

public interface Accessible {
    AccessFlag[] accessFlags();
    default int toInt() {
        return Arrays.stream(accessFlags()).reduce(0, (flags, f2) -> flags | f2.mask(), (f1, f2) -> f1 | f2);
    }
    default boolean isAbstract() {
        return (AccessFlag.ABSTRACT.mask() & toInt()) > 0;
    }
    default boolean isAnnotation() {
        return (AccessFlag.ANNOTATION.mask() & toInt()) > 0;
    }
    default boolean isBridge() {
        return (AccessFlag.BRIDGE.mask() & toInt()) > 0;
    }
    default boolean isEnum() {
        return (AccessFlag.ENUM.mask() & toInt()) > 0;
    }
    default boolean isFinal() {
        return (AccessFlag.FINAL.mask() & toInt()) > 0;
    }
    default boolean isInterface() {
        return (AccessFlag.FINAL.mask() & toInt()) > 0;
    }
    default boolean isMandated() {
        return (AccessFlag.MANDATED.mask() & toInt()) > 0;
    }
    default boolean isModule() {
        return (AccessFlag.MODULE.mask() & toInt()) > 0;
    }
    default boolean isNative() {
        return (AccessFlag.NATIVE.mask() & toInt()) > 0;
    }
    default boolean isOpen() {
        return (AccessFlag.OPEN.mask() & toInt()) > 0;
    }
    default boolean isPrivate() {
        return (AccessFlag.PRIVATE.mask() & toInt()) > 0;
    }
    default boolean isProtected() {
        return (AccessFlag.PROTECTED.mask() & toInt()) > 0;
    }
    default boolean isPublic() {
        return (AccessFlag.PUBLIC.mask() & toInt()) > 0;
    }
    default boolean isStatic() {
        return (AccessFlag.STATIC.mask() & toInt()) > 0;
    }
    default boolean isStaticPhase() {
        return (AccessFlag.STATIC_PHASE.mask() & toInt()) > 0;
    }
    default boolean isStrict() {
        return (AccessFlag.STRICT.mask() & toInt()) > 0;
    }
    default boolean isSuper() {
        return (AccessFlag.SUPER.mask() & toInt()) > 0;
    }
    default boolean isSynchronized() {
        return (AccessFlag.SYNCHRONIZED.mask() & toInt()) > 0;
    }
    default boolean isSynthetic() {
        return (AccessFlag.SYNTHETIC.mask() & toInt()) > 0;
    }
    default boolean isTransient() {
        return (AccessFlag.TRANSIENT.mask() & toInt()) > 0;
    }
    default boolean isTransitive() {
        return (AccessFlag.TRANSITIVE.mask() & toInt()) > 0;
    }
    default boolean isVarargs() {
        return (AccessFlag.VARARGS.mask() & toInt()) > 0;
    }
    default boolean isVolatile() {
        return (AccessFlag.VOLATILE.mask() & toInt()) > 0;
    }
}
