package lang.aurum.model.impl;

import lang.aurum.model.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;
import java.util.*;

public final class TypeImpl implements Type {
    private final String className;
    private final String pkg;
    private Type superClass;
    private final Optional<Type[]> interfaces;
    private final Field[] fields;
    private final Method[] methods;
    private final AccessFlag[] accessFlags;
    private final Attribute[] attributes;
    private final Optional<TypeParameter[]> typeParameters;
    private final Optional<TypeArgument[]> typeArguments;

    public TypeImpl(
            String className,
            String pkg,
            Type superClass,
            Optional<Type[]> interfaces,
            Field[] fields,
            Method[] methods,
            AccessFlag[] accessFlags,
            Attribute[] attributes,
            Optional<TypeParameter[]> typeParameters,
            Optional<TypeArgument[]> typeArguments
    ) {
        this.className = className;
        this.pkg = pkg;
        this.superClass = superClass;
        this.interfaces = interfaces;
        this.fields = fields;
        this.methods = methods;
        this.accessFlags = accessFlags;
        this.attributes = attributes;
        this.typeParameters = typeParameters;
        this.typeArguments = typeArguments;
    }

    @NotNull
    @Override
    public Type asArray(int dimensions) {
        return new TypeImpl(
                className,
                pkg,
                superClass,
                interfaces,
                fields,
                methods,
                accessFlags,
                attributes,
                typeParameters,
                typeArguments
        );
    }

    /*
    class C<T, U, V> : S<T, String>, W<List<U>, V>
          typeParams    arg   arg        arg   arg
                       tmpl   type    typ<tmp> tmp
     */
    @NotNull
    @Override
    public Type withTypeArguments(TypeArgument[] typeArguments) {
        return Utils.applyTypeArguments(this, typeArguments);
    }

    @NotNull
    @Override
    public Type withTypeArguments(Type[] typeArguments) {
        return Utils.applyTypeArguments(this, typeArguments);
    }

    @Override
    public @NotNull String className() {
        return className;
    }

    @Override
    public @NotNull String pkg() {
        return pkg;
    }

    @Override
    public Type superClass() {
        return superClass;
    }

    public void setSuperClass(Type superClass) {
        this.superClass = superClass;
    }

    @Override
    public @NotNull Optional<Type[]> interfaces() {
        return interfaces;
    }

    @Override
    public Field[] fields() {
        return fields;
    }

    @Override
    public Method[] methods() {
        return methods;
    }

    @Override
    public AccessFlag[] accessFlags() {
        return accessFlags;
    }

    @Override
    public Attribute[] attributes() {
        return attributes;
    }

    @Override
    public Optional<TypeParameter[]> typeParameters() {
        return typeParameters;
    }

    @Override
    public Optional<TypeArgument[]> typeArguments() {
        return typeArguments;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (TypeImpl) obj;
        return Objects.equals(this.className, that.className) &&
                Objects.equals(this.pkg, that.pkg) &&
                Objects.equals(this.superClass, that.superClass) &&
                Objects.equals(this.interfaces, that.interfaces) &&
                Arrays.equals(this.fields, that.fields) &&
                Arrays.equals(this.methods, that.methods) &&
                Arrays.equals(this.accessFlags, that.accessFlags) &&
                Arrays.equals(this.attributes, that.attributes) &&
                Objects.equals(this.typeParameters, that.typeParameters) &&
                Objects.equals(this.typeArguments, that.typeArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, pkg, superClass, interfaces, fields, methods, accessFlags, attributes, typeParameters, typeArguments);
    }

    @Override
    public String toString() {
        return "TypeImpl[" +
                "className=" + className + ", " +
                "pkg=" + pkg + ", " +
                "superClass=" + superClass + ", " +
                "interfaces=" + interfaces + ", " +
                "fields=" + fields + ", " +
                "methods=" + methods + ", " +
                "accessFlags=" + accessFlags + ", " +
                "attributes=" + attributes + ", " +
                "typeParameters=" + typeParameters + ", " +
                "typeArguments=" + typeArguments + ']';
    }

}
