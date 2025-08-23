package lang.aurum.model.impl;

import lang.aurum.model.*;

import java.lang.reflect.AccessFlag;
import java.util.*;

public record TypeImpl(
        String className,
        String pkg,
        Type superClass,
        Optional<Type[]> interfaces,
        int arrayDimensions,
        Field[] fields,
        Method[] methods,
        AccessFlag[] accessFlags,
        Attribute[] attributes,
        Optional<TypeParameter[]> typeParameters,
        Optional<TypeArgument[]> typeArguments
) implements Type {
    @Override
    public Type asArray(int dimensions) {
        return new TypeImpl(
                className,
                pkg,
                superClass,
                interfaces,
                arrayDimensions+dimensions,
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
    @Override
    public Type withTypeArguments(TypeArgument[] typeArguments) {
        if (typeArguments == null) return this;
        if (typeParameters.isEmpty()) return this;

        // Build mapping from this type's parameters to provided argument bounds
        Map<String, Type> typeMap = getTypeMap(typeArguments);
        if (typeMap.isEmpty()) return this;

        // Superclass and interfaces with applied substitutions
        Type newSuperClass = Utils.replaceTemplates(superClass, typeMap);
        Optional<Type[]> newInterfaces = interfaces.map(intfs -> {
            Type[] arr = new Type[intfs.length];
            for (int i = 0; i < intfs.length; i++) arr[i] = Utils.replaceTemplates(intfs[i], typeMap);
            return arr;
        });

        // Fields with applied substitutions
        Field[] newFields = new Field[fields.length];
        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i];
            Type newFieldType = Utils.replaceTemplates(f.type(), typeMap);
            newFields[i] = new FieldImpl(f.name(), newFieldType, f.attributes(), f.accessFlags());
        }

        // Methods with applied substitutions (do not treat method type parameters as targets here)
        Method[] newMethods = new Method[methods.length];
        for (int i = 0; i < methods.length; i++) {
            newMethods[i] = methods[i].withTypeArguments(typeArguments);
        }

        return new TypeImpl(
                className,
                pkg,
                newSuperClass,
                newInterfaces,
                arrayDimensions,
                newFields,
                newMethods,
                accessFlags,
                attributes,
                typeParameters,
                Optional.of(typeArguments)
        );
    }

    private Map<String, Type> getTypeMap(TypeArgument[] typeArguments) {
        TypeParameter[] tps = typeParameters.get();
        int n = Math.min(tps.length, typeArguments.length);
        Map<String, Type> typeMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            typeMap.put(tps[i].name(), typeArguments[i].bound());
        }
        return typeMap;
    }

    @Override
    public Type withTypeArguments(Type[] typeArguments) {
        if (typeArguments == null) return this;
        if (typeParameters.isEmpty()) return this;
        TypeParameter[] tps = typeParameters.get();
        int n = Math.min(tps.length, typeArguments.length);
        TypeArgument[] args = new TypeArgument[n];
        for (int i = 0; i < n; i++) {
            args[i] = new TypeArgumentImpl(tps[i].name(), typeArguments[i]);
        }
        return withTypeArguments(args);
    }
}
