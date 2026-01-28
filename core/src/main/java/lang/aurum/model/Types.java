package lang.aurum.model;

import lang.aurum.model.impl.PrimitiveTypeImpl;

public final class Types {
    private Types() {}

    public static final PrimitiveType VOID = PrimitiveTypeImpl.VOID;
    public static final PrimitiveType BOOLEAN = PrimitiveTypeImpl.BOOLEAN;
    public static final PrimitiveType BYTE = PrimitiveTypeImpl.BYTE;
    public static final PrimitiveType SHORT = PrimitiveTypeImpl.SHORT;
    public static final PrimitiveType CHAR = PrimitiveTypeImpl.CHAR;
    public static final PrimitiveType INT = PrimitiveTypeImpl.INT;
    public static final PrimitiveType FLOAT = PrimitiveTypeImpl.FLOAT;
    public static final PrimitiveType LONG = PrimitiveTypeImpl.LONG;
    public static final PrimitiveType DOUBLE = PrimitiveTypeImpl.DOUBLE;
    public static final Type OBJECT = Type.ofClass(Object.class);
    public static final Type STRING = Type.ofClass(String.class);
}
