package lang.aurum.model;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;
import java.util.Arrays;

public interface Accessible {
    @NotNull AccessFlag[] accessFlags();
    default int intFlags() {
        return Arrays.stream(accessFlags()).reduce(0, (flags, f2) -> flags | f2.mask(), (f1, f2) -> f1 | f2);
    }
    default boolean isAbstract() {
        return (AccessFlag.ABSTRACT.mask() & intFlags()) > 0;
    }
    default boolean isAnnotation() {
        return (AccessFlag.ANNOTATION.mask() & intFlags()) > 0;
    }
    default boolean isBridge() {
        return (AccessFlag.BRIDGE.mask() & intFlags()) > 0;
    }
    default boolean isEnum() {
        return (AccessFlag.ENUM.mask() & intFlags()) > 0;
    }
    default boolean isFinal() {
        return (AccessFlag.FINAL.mask() & intFlags()) > 0;
    }
    default boolean isInterface() {
        return (AccessFlag.INTERFACE.mask() & intFlags()) > 0;
    }
    default boolean isMandated() {
        return (AccessFlag.MANDATED.mask() & intFlags()) > 0;
    }
    default boolean isModule() {
        return (AccessFlag.MODULE.mask() & intFlags()) > 0;
    }
    default boolean isNative() {
        return (AccessFlag.NATIVE.mask() & intFlags()) > 0;
    }
    default boolean isOpen() {
        return (AccessFlag.OPEN.mask() & intFlags()) > 0;
    }
    default boolean isPrivate() {
        return (AccessFlag.PRIVATE.mask() & intFlags()) > 0;
    }
    default boolean isProtected() {
        return (AccessFlag.PROTECTED.mask() & intFlags()) > 0;
    }
    default boolean isPublic() {
        return (AccessFlag.PUBLIC.mask() & intFlags()) > 0;
    }
    default boolean isStatic() {
        return (AccessFlag.STATIC.mask() & intFlags()) > 0;
    }
    default boolean isStaticPhase() {
        return (AccessFlag.STATIC_PHASE.mask() & intFlags()) > 0;
    }
    default boolean isStrict() {
        return (AccessFlag.STRICT.mask() & intFlags()) > 0;
    }
    default boolean isSuper() {
        return (AccessFlag.SUPER.mask() & intFlags()) > 0;
    }
    default boolean isSynchronized() {
        return (AccessFlag.SYNCHRONIZED.mask() & intFlags()) > 0;
    }
    default boolean isSynthetic() {
        return (AccessFlag.SYNTHETIC.mask() & intFlags()) > 0;
    }
    default boolean isTransient() {
        return (AccessFlag.TRANSIENT.mask() & intFlags()) > 0;
    }
    default boolean isTransitive() {
        return (AccessFlag.TRANSITIVE.mask() & intFlags()) > 0;
    }
    default boolean isVarargs() {
        return (AccessFlag.VARARGS.mask() & intFlags()) > 0;
    }
    default boolean isVolatile() {
        return (AccessFlag.VOLATILE.mask() & intFlags()) > 0;
    }
}
