package aurum.lang.model;

import aurum.lang.model.factory.TypeFactory;
import aurum.lang.model.impl.UnionTypeImpl;
import aurum.lang.model.impl.Utils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public interface UnionType extends Type {
    Type[] types();

    @Override
    default boolean isPlainType() {
        return false;
    }

    @NotNull
    @Override
    default AccessFlag[] accessFlags() {
        return Utils.DEFAULT_ACCESS_FLAGS;
    }

    @Override
    @NotNull
    default String fullName() {
        return String.join(" | ", Arrays.stream(types()).map(Type::fullName).toArray(String[]::new));
    }

    @NotNull
    @Override
    default String className() {
        return "";
    }

    @NotNull
    @Override
    default String pkg() {
        return "";
    }

    @Override
    default @NotNull Type @NotNull [] interfaces() {
        return Utils.EMPTY_TYPES;
    }

    @Override
    default @NotNull TypeParameter @NotNull [] typeParameters() {
        return Utils.EMPTY_TYPE_PARAMETERS;
    }

    @Override
    default @NotNull TypeArgument @NotNull [] typeArguments() {
        return Utils.EMPTY_TYPE_ARGUMENTS;
    }

    @Override
    @NotNull UnionType withDefaultTypeArguments();

    @NotNull
    @Override
    default Type superClass() {
        Type[] types = types();
        if (types == null || types.length == 0) return Types.OBJECT;

        // Try each ancestor of the first type (including itself) as a candidate
        for (Type candidate = types[0]; candidate != null; candidate = candidate.superClass()) {
            boolean ok = true;
            // Check that this candidate is a supertype of every other type in the union
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
                return candidate;
            }
        }
        return Types.OBJECT;
    }

    @NotNull
    @Override
    default Field[] fields() {
        return Arrays.stream(types())
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

    @NotNull
    @Override
    default Method[] methods() {
        return Arrays.stream(types())
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
    @NotNull UnionType withTypeArguments(TypeArgument @NotNull [] typeArguments);

    @Override
    @NotNull UnionType withTypeArguments(Type @NotNull [] typeArguments);

    @NotNull
    @Override
    default Type asArrayWithTypeArguments(int dimensions, TypeArgument[] typeArguments) {
        return Type.super.asArrayWithTypeArguments(dimensions, typeArguments);
    }

    @NotNull
    @Override
    default Attribute[] attributes() {
        return Utils.EMPTY_ATTRIBUTES;
    }

    static @NotNull UnionType ofClasses(@NotNull Class<?>[] classes) {
        return new UnionTypeImpl(
                Arrays.stream(classes)
                        .map(TypeFactory::ofClass)
                        .toArray(Type[]::new)
        );
    }

    static @NotNull UnionType ofReferences(@NotNull TypeReference<?>[] typeReferences) {
        return new UnionTypeImpl(
                Arrays.stream(typeReferences)
                        .map(TypeFactory::ofReference)
                        .toArray(Type[]::new)
        );
    }

    static @NotNull UnionType ofTypes(@NotNull java.lang.reflect.Type[] types) {
        return new UnionTypeImpl(
                Arrays.stream(types)
                        .map(TypeFactory::ofType)
                        .toArray(Type[]::new)
        );
    }

    static @NotNull UnionType ofTypeModels(@NotNull Type @NotNull [] types) {
        return new UnionTypeImpl(
                types
        );
    }
}
