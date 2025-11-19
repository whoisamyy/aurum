package lang.aurum.model.impl;

import lang.aurum.model.*;

import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Utils {
    public static final AccessFlag[] DEFAULT_ACCESS_FLAGS = new AccessFlag[]{AccessFlag.PUBLIC, AccessFlag.FINAL};
    public static final Member[] EMPTY_MEMBERS = new Member[0];
    public static final Field[] EMPTY_FIELDS = new Field[0];
    public static final Method[] EMPTY_METHODS = new Method[0];
    public static final Attribute[] EMPTY_ATTRIBUTES = new Attribute[0];
    public static final TypeParameter[] EMPTY_TYPE_PARAMETERS = new TypeParameter[0];
    public static final Type[] EMPTY_TYPES = new Type[0];

    public static Type replaceTemplates(Type type, Map<String, Type> typeMap) {
        if (type == null) return null;

        // If it's a template type, swap to the mapped concrete bound (preserving array dimensions)
        if (type instanceof TemplateType templateType) {
            Type mapped = typeMap.get(templateType.className());
            if (mapped != null) {
                return mapped;
            }
            return type;
        }

        // Only rebuild when there are type arguments and at least one bound changes
        var tArgsOpt = type.typeArguments();
        if (tArgsOpt.isEmpty()) return type;

        var oldArgs = tArgsOpt.get();
        var newArgs = new TypeArgument[oldArgs.length];
        boolean changed = false;
        for (int i = 0; i < oldArgs.length; i++) {
            var a = oldArgs[i];
            Type newBound = replaceTemplates(a.bound(), typeMap);
            newArgs[i] = new TypeArgumentImpl(a.name(), newBound);
            if (newBound != a.bound()) changed = true;
        }

        if (!changed) return type;

        return new TypeImpl(
                type.className(),
                type.pkg(),
                type.superClass(),
                type.interfaces(),
                type.fields(),
                type.methods(),
                type.accessFlags(),
                type.attributes(),
                type.typeParameters(),
                Optional.of(newArgs)
        );
    }

    public static Map<String, Type> getTypeMap(Type type, Type[] typeArguments) {
        Map<String, Type> typeMap = new HashMap<>();
        if (typeArguments == null || typeArguments.length == 0)
            return typeMap;
        int argC = 0;
        switch (type) {
            case TemplateType template -> typeMap.put(template.className(), typeArguments[0]);
            case IntersectionType intersection -> {
                for (var t : intersection.types()) {
                    if (t instanceof TemplateType template)
                        typeMap.put(template.className(), typeArguments[argC++]);
                    else {
                        Map<String, Type> iTypeMap = getTypeMap(t, Arrays.stream(typeArguments).skip(argC).toArray(Type[]::new));
                        argC += iTypeMap.size();
                        typeMap.putAll(iTypeMap);
                    }
                }
            }
            case UnionType union -> {
                for (var t : union.types()) {
                    if (t instanceof TemplateType template)
                        typeMap.put(template.className(), typeArguments[argC++]);
                    else {
                        Map<String, Type> uTypeMap = getTypeMap(t, Arrays.stream(typeArguments).skip(argC).toArray(Type[]::new));
                        argC += uTypeMap.size();
                        typeMap.putAll(uTypeMap);
                    }
                }
            }
            case Type _ -> {
                if (type.typeArguments().isEmpty())
                    break;

                var tTypeArgs = type.typeArguments().get();
                for (var typeArg : tTypeArgs) {
                    if (typeArg.bound() instanceof TemplateType template)
                        typeMap.put(template.className(), typeArguments[argC++]);
                    else {
                        Map<String, Type> argTypeMap = getTypeMap(typeArg.bound(), Arrays.stream(typeArguments).skip(argC).toArray(Type[]::new));
                        argC += argTypeMap.size();
                        typeMap.putAll(argTypeMap);
                    }
                }
            }
        }

        return typeMap;
    }

    public static Map<String, Type> getTypeMap(TypeArgument[] typeArguments) {
        Map<String, Type> typeMap = new HashMap<>();
        if (typeArguments == null)
            return typeMap;
        for (TypeArgument typeArgument : typeArguments) {
            typeMap.put(typeArgument.name(), typeArgument.bound());
        }
        return typeMap;
    }

    // Build mapping for a concrete type using its declared type parameters and provided arguments
    public static Map<String, Type> getTypeMap(Type type, TypeArgument[] typeArguments) {
        Map<String, Type> typeMap = new HashMap<>();
        if (typeArguments == null)
            return typeMap;
        if (type.typeParameters().isEmpty())
            return typeMap;
        TypeParameter[] tps = type.typeParameters().get();
        int n = Math.min(tps.length, typeArguments.length);
        for (int i = 0; i < n; i++) {
            typeMap.put(tps[i].name(), typeArguments[i].bound());
        }
        return typeMap;
    }

    // Convert raw type array to named type arguments based on the type's type parameters order
    public static TypeArgument[] toTypeArguments(Type type, Type[] typeArguments) {
        if (typeArguments == null || type.typeParameters().isEmpty())
            return new TypeArgument[0];
        TypeParameter[] tps = type.typeParameters().get();
        int n = Math.min(tps.length, typeArguments.length);
        TypeArgument[] args = new TypeArgument[n];
        for (int i = 0; i < n; i++) {
            args[i] = new TypeArgumentImpl(tps[i].name(), typeArguments[i]);
        }
        return args;
    }

    public static UnionType applyTypeArguments(UnionType type, TypeArgument[] typeArguments) {
        Map<String, Type> typeMap = Utils.getTypeMap(typeArguments);
        int typesLength = type.types().length;
        var newTypes = new Type[typesLength];
        for (int i = 0; i < typesLength; i++) {
            newTypes[i] = Utils.replaceTemplates(type.types()[i], typeMap);
        }
        return new UnionTypeImpl(newTypes);
    }

    public static UnionType applyTypeArguments(UnionType type, Type[] typeArguments) {
        Map<String, Type> typeMap = Utils.getTypeMap(type, typeArguments);
        int typesLength = type.types().length;
        var newTypes = new Type[typesLength];
        for (int i = 0; i < typesLength; i++) {
            newTypes[i] = Utils.replaceTemplates(type.types()[i], typeMap);
        }
        return new UnionTypeImpl(newTypes);
    }

    public static IntersectionType applyTypeArguments(IntersectionType type, TypeArgument[] typeArguments) {
        Map<String, Type> typeMap = Utils.getTypeMap(typeArguments);
        int typesLength = type.types().length;
        var newTypes = new Type[typesLength];
        for (int i = 0; i < typesLength; i++) {
            newTypes[i] = Utils.replaceTemplates(type.types()[i], typeMap);
        }
        return new IntersectionTypeImpl(newTypes);
    }

    public static IntersectionType applyTypeArguments(IntersectionType type, Type[] typeArguments) {
        Map<String, Type> typeMap = Utils.getTypeMap(type, typeArguments);
        int typesLength = type.types().length;
        var newTypes = new Type[typesLength];
        for (int i = 0; i < typesLength; i++) {
            newTypes[i] = Utils.replaceTemplates(type.types()[i], typeMap);
        }
        return new IntersectionTypeImpl(newTypes);
    }

    // Apply type arguments to a Type (class/interface implementation)
    public static Type applyTypeArguments(Type type, TypeArgument[] typeArguments) {
        if (typeArguments == null) return type;
        if (type.typeParameters().isEmpty()) return type;

        Map<String, Type> typeMap = getTypeMap(type, typeArguments);
        if (typeMap.isEmpty()) return type;

        Type newSuperClass = replaceTemplates(type.superClass(), typeMap);
        Optional<Type[]> newInterfaces = type.interfaces().map(intfs -> {
            Type[] arr = new Type[intfs.length];
            for (int i = 0; i < intfs.length; i++) arr[i] = replaceTemplates(intfs[i], typeMap);
            return arr;
        });

        Field[] oldFields = type.fields();
        Field[] newFields = new Field[oldFields.length];

        Method[] oldMethods = type.methods();
        Method[] newMethods = new Method[oldMethods.length];

        TypeImpl newType = new TypeImpl(
                type.className(),
                type.pkg(),
                newSuperClass,
                newInterfaces,
                newFields,
                newMethods,
                type.accessFlags(),
                type.attributes(),
                type.typeParameters(),
                Optional.of(typeArguments)
        );

        for (int i = 0; i < oldMethods.length; i++) {
            newMethods[i] = applyTypeArguments(oldMethods[i], newType, typeArguments);
        }

        for (int i = 0; i < oldFields.length; i++) {
            Field f = oldFields[i];
            Type newFieldType = replaceTemplates(f.type(), typeMap);
            newFields[i] = new FieldImpl(newType, f.name(), newFieldType, f.attributes(), f.accessFlags());
        }
        return newType;
    }

    public static Type applyTypeArguments(Type type, Type[] typeArguments) {
        if (typeArguments == null) return type;
        if (typeArguments.length == 0) return type;
        if (type.typeParameters().isEmpty()) return type;
        TypeArgument[] args = toTypeArguments(type, typeArguments);
        if (args.length == 0) return type;
        return applyTypeArguments(type, args);
    }

    // Apply type arguments to a Method signature
    public static Method applyTypeArguments(Method method, Type newOwner, TypeArgument[] typeArguments) {
        Map<String, Type> typeMap = getTypeMap(typeArguments);

        Type newReturnType = replaceTemplates(method.returnType(), typeMap);

        Parameter[] oldParams = method.parameters();
        Parameter[] newParams = new Parameter[oldParams.length];
        for (int j = 0; j < oldParams.length; j++) {
            var p = oldParams[j];
            Type newParamType = replaceTemplates(p.type(), typeMap);
            newParams[j] = new ParameterImpl(p.name(), newParamType, p.attributes());
        }

        Type[] oldEx = method.exceptions();
        Type[] newEx = new Type[oldEx.length];
        for (int j = 0; j < oldEx.length; j++) newEx[j] = replaceTemplates(oldEx[j], typeMap);

        Optional<TypeParameter[]> newMethodTypeParams = method.typeParameters().map(mTPs -> {
            TypeParameter[] res = new TypeParameter[mTPs.length];
            for (int j = 0; j < mTPs.length; j++) {
                var tp = mTPs[j];
                Type newBound = replaceTemplates(tp.bound(), typeMap);
                res[j] = new TypeParameterImpl(tp.name(), newBound);
            }
            return res;
        });

        return new MethodImpl(
                newOwner,
                method.name(),
                newReturnType,
                newParams,
                newEx,
                method.accessFlags(),
                newMethodTypeParams,
                method.typeArguments(),
                method.attributes()
        );
    }

    public static Method applyTypeArguments(Method method, Type newOwner, Type[] typeArguments) {
        if (typeArguments == null) return method;
        if (method.typeParameters().isEmpty()) return applyTypeArguments(method, newOwner, new TypeArgument[0]);
        TypeParameter[] tps = method.typeParameters().get();
        int n = Math.min(tps.length, typeArguments.length);
        TypeArgument[] args = new TypeArgument[n];
        for (int i = 0; i < n; i++) {
            args[i] = new TypeArgumentImpl(tps[i].name(), typeArguments[i]);
        }
        return applyTypeArguments(method, newOwner, args);
    }
}
