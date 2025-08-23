package lang.aurum.model.impl;

import lang.aurum.model.*;

import java.util.*;

public record UnionTypeImpl(
        Type[] types,
        int arrayDimensions
) implements UnionType {
    @Override
    public Type superClass() {
        if (types == null || types.length == 0) return Type.ofClass(Object.class);

        // Try each ancestor ofMethod the first type (including itself) as a candidate
        for (Type candidate = types[0]; candidate != null; candidate = candidate.superClass()) {
            boolean ok = true;
            // Check that this candidate is a supertype ofMethod every other type in the union
            for (int i = 1; i < types.length; i++) {
                Type t = types[i];
                boolean found = false;
                for (Type anc = t; anc != null; anc = anc.superClass()) {
                    if (anc.equals(candidate)) {
                        found = true;
                        break;
                    }
                }
                if (!found) { // candidate is not common to all
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return arrayDimensions > 0 ? candidate.asArray(arrayDimensions) : candidate;
            }
        }
        return Type.ofClass(Object.class);
    }

    @Override
    public Field[] fields() {
        return Arrays.stream(types)
                .map(Type::fields)
                .reduce(
                        new HashSet<Field>(),
                        (c, elements) -> {
                            Collections.addAll(c, elements);
                            return c;
                        },
                        (set1, set2) -> {
                            if (set1.size() < set2.size()) {
                                set2.addAll(set1);
                                return set2;
                            } else {
                                set1.addAll(set2);
                                return set1;
                            }
                        }
                )
                .toArray(Field[]::new);
    }

    @Override
    public Method[] methods() {
        return Arrays.stream(types)
                .map(Type::methods)
                .reduce(
                        new HashSet<Method>(),
                        (c, elements) -> {
                            Collections.addAll(c, elements);
                            return c;
                        },
                        (set1, set2) -> {
                            if (set1.size() < set2.size()) {
                                set2.addAll(set1);
                                return set2;
                            } else {
                                set1.addAll(set2);
                                return set1;
                            }
                        }
                )
                .toArray(Method[]::new);
    }

    @Override
    public Type asArray(int dimensions) {
        return new UnionTypeImpl(types, arrayDimensions + dimensions);
    }

    @Override
    public Type withTypeArguments(TypeArgument[] typeArguments) {
        Map<String, Type> typeMap = Utils.getTypeMap(typeArguments);
        int typesLength = types.length;
        var newTypes = new Type[typesLength];
        for (int i = 0; i < typesLength; i++) {
            newTypes[i] = Utils.replaceTemplates(types[i], typeMap);
        }
        return new UnionTypeImpl(newTypes, arrayDimensions);
    }


    @Override
    public Type withTypeArguments(Type[] typeArguments) {
        Map<String, Type> typeMap = Utils.getTypeMap(this, typeArguments);
        int typesLength = types.length;
        var newTypes = new Type[typesLength];
        for (int i = 0; i < typesLength; i++) {
            newTypes[i] = Utils.replaceTemplates(types[i], typeMap);
        }
        return new UnionTypeImpl(newTypes, arrayDimensions);
    }

    @Override
    public Attribute[] attributes() {
        return Utils.EMPTY_ATTRIBUTES;
    }
}
