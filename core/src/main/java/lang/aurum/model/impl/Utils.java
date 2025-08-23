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
                int dims = templateType.arrayDimensions();
                return dims > 0 ? mapped.asArray(dims) : mapped;
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
                type.arrayDimensions(),
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
}
