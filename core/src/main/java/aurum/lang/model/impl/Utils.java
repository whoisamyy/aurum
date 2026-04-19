package aurum.lang.model.impl;

import aurum.lang.model.*;
import aurum.lang.model.util.ParametrizedMethodPool;
import aurum.lang.model.util.ParametrizedTypePool;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Utils {
    public static final AccessFlag @NotNull [] DEFAULT_ACCESS_FLAGS = new AccessFlag[]{AccessFlag.PUBLIC, AccessFlag.FINAL};
    public static final Member @NotNull [] EMPTY_MEMBERS = new Member[0];
    public static final Field @NotNull [] EMPTY_FIELDS = new Field[0];
    public static final Method @NotNull [] EMPTY_METHODS = new Method[0];
    public static final Attribute @NotNull [] EMPTY_ATTRIBUTES = new Attribute[0];
    public static final TypeParameter @NotNull [] EMPTY_TYPE_PARAMETERS = new TypeParameter[0];
    public static final Type @NotNull [] EMPTY_TYPES = new Type[0];
    public static final TypeArgument @NotNull [] EMPTY_TYPE_ARGUMENTS = new TypeArgument[0];
    public static final Parameter[] DEFAULT_ARRAY_INIT_PARAMETERS = { new ParameterImpl("length", Types.INT) };

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
        if (tArgsOpt.length == 0) return type;

        var oldArgs = tArgsOpt;
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
                newArgs
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
            case Type typeParam -> {
                if (type.typeArguments().length == 0)
                    break;

                var tTypeArgs = type.typeArguments();
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
        if (type.typeParameters().length == 0)
            return typeMap;
        TypeParameter[] tps = type.typeParameters();
        int n = Math.min(tps.length, typeArguments.length);
        for (int i = 0; i < n; i++) {
            typeMap.put(tps[i].name(), typeArguments[i].bound());
        }
        return typeMap;
    }

    // Convert raw type array to named type arguments based on the type's type parameters order
    public static TypeArgument[] toTypeArguments(Type type, Type[] typeArguments) {
        if (typeArguments == null || type.typeParameters().length == 0)
            return new TypeArgument[0];
        TypeParameter[] tps = type.typeParameters();
        // Filter out null type parameters
        int nonNullCount = 0;
        for (TypeParameter tp : tps) if (tp != null) nonNullCount++;
        int n = Math.min(nonNullCount, typeArguments.length);
        TypeArgument[] args = new TypeArgument[n];
        int argIdx = 0;
        for (int i = 0; i < tps.length && argIdx < n; i++) {
            if (tps[i] != null) {
                args[argIdx] = new TypeArgumentImpl(tps[i].name(), typeArguments[argIdx]);
                argIdx++;
            }
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
        if (type.typeParameters().length == 0) return type;
//        if (type.typeArguments().isPresent()) type = type.getRawType();

        Map<String, Type> typeMap = getTypeMap(type, typeArguments);
        if (typeMap.isEmpty()) return type;

        Type newSuperClass = replaceTemplates(type.superClass(), typeMap);
        Type[] newInterfaces = new Type[type.interfaces().length];
        for (int i = 0; i < type.interfaces().length; i++) newInterfaces[i] = replaceTemplates(type.interfaces()[i], typeMap);

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
                typeArguments
        );

        for (int i = 0; i < oldMethods.length; i++) {
            if (oldMethods[i] == null) {
                continue; // Skip null methods
            }
            newMethods[i] = applyTypeArguments(oldMethods[i], newType, typeArguments);
        }

        for (int i = 0; i < oldFields.length; i++) {
            Field f = oldFields[i];
            if (f == null) {
                continue; // Skip null fields
            }
            Type newFieldType = replaceTemplates(f.type(), typeMap);
            newFields[i] = new FieldImpl(newType, f.name(), newFieldType, f.attributes(), f.accessFlags());
        }

        ParametrizedTypePool.addType(type, newType);

        return newType;
    }

    public static Type applyTypeArguments(Type type, Type[] typeArguments) {
        if (typeArguments == null) return type;
        if (typeArguments.length == 0) return type;
        if (type.typeParameters().length == 0) return type;
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

        TypeParameter[] newMethodTypeParams = new TypeParameter[method.typeParameters().length];
        for (int j = 0; j < method.typeParameters().length; j++) {
            var tp = method.typeParameters()[j];
            Type newBound = replaceTemplates(tp.bound(), typeMap);
            newMethodTypeParams[j] = new TypeParameterImpl(tp.name(), newBound);
        }

        MethodImpl newMethod = new MethodImpl(
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
        ParametrizedMethodPool.addMethod(method, newMethod);

        return newMethod;
    }

    public static Method applyTypeArguments(Method method, Type newOwner, Type[] typeArguments) {
        if (typeArguments == null) return method;
        if (method.typeParameters().length == 0) return applyTypeArguments(method, newOwner, new TypeArgument[0]);
        TypeParameter[] tps = method.typeParameters();
        int n = Math.min(tps.length, typeArguments.length);
        TypeArgument[] args = new TypeArgument[n];
        for (int i = 0; i < n; i++) {
            args[i] = new TypeArgumentImpl(tps[i].name(), typeArguments[i]);
        }
        return applyTypeArguments(method, newOwner, args);
    }
}
