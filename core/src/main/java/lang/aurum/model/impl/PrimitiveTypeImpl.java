package lang.aurum.model.impl;

import lang.aurum.model.PrimitiveType;
import lang.aurum.model.Type;
import org.jetbrains.annotations.NotNull;

import java.lang.classfile.TypeKind;

public enum PrimitiveTypeImpl implements PrimitiveType {
    VOID("V", "void", TypeKind.VOID),
    BOOLEAN("Z", "boolean", TypeKind.BOOLEAN),
    BYTE("B", "byte", TypeKind.BYTE),
    SHORT("S", "short", TypeKind.SHORT),
    CHAR("C", "char", TypeKind.CHAR),
    INT("I", "int", TypeKind.INT),
    FLOAT("F", "float", TypeKind.FLOAT),
    LONG("J", "long", TypeKind.LONG),
    DOUBLE("D", "double", TypeKind.DOUBLE);

    private final String jvmName;
    private final String className;
    private final TypeKind typeKind;

    PrimitiveTypeImpl(String jvmName, String className, TypeKind typeKind) {
        this.jvmName = jvmName;
        this.className = className;
        this.typeKind = typeKind;

    }

    // array types are not primitive so return TypeImpl

    @NotNull
    @Override
    public Type asArray(int dimensions) {
        if (dimensions == 0)
            return this;

        return new ArrayTypeImpl<PrimitiveType>(this, dimensions);
    }

    @NotNull
    @Override
    public TypeKind typeKind() {
        return typeKind;
    }

    @NotNull
    @Override
    public String className() {
        return className;
    }

    @Override
    public String jvmName() {
        return jvmName;
    }

    @Override
    public Type boxed() {
        return switch (this) {
            case VOID -> Type.ofClass(Void.class);
            case BOOLEAN -> Type.ofClass(Boolean.class);
            case BYTE -> Type.ofClass(Byte.class);
            case SHORT -> Type.ofClass(Short.class);
            case CHAR -> Type.ofClass(Character.class);
            case INT -> Type.ofClass(Integer.class);
            case FLOAT -> Type.ofClass(Float.class);
            case LONG -> Type.ofClass(Long.class);
            case DOUBLE -> Type.ofClass(Double.class);
        };
    }
}
