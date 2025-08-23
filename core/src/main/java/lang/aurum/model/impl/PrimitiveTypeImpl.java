package lang.aurum.model.impl;

import lang.aurum.model.PrimitiveType;
import lang.aurum.model.Type;

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

    @Override
    public Type asArray(int dimensions) {
        return new TypeImpl(
                className(),
                "",
                superClass(),
                interfaces(),
                dimensions,
                fields(),
                methods(),
                accessFlags(),
                attributes(),
                typeParameters(),
                typeArguments()
        );
    }
    @Override
    public TypeKind typeKind() {
        return typeKind;
    }

    @Override
    public String className() {
        return className;
    }

    @Override
    public String jvmName() {
        return jvmName;
    }
}
