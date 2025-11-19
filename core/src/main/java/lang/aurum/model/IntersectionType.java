package lang.aurum.model;

import lang.aurum.model.factory.TypeFactory;
import lang.aurum.model.impl.IntersectionTypeImpl;
import lang.aurum.model.impl.Utils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

public interface IntersectionType extends Type {
    Type[] types();

    @Override
    @NotNull IntersectionType withTypeArguments(TypeArgument[] typeArguments);

    @Override
    @NotNull IntersectionType withTypeArguments(Type[] typeArguments);

    @NotNull
    @Override
    default IntersectionType asArrayWithTypeArguments(int dimensions, TypeArgument[] typeArguments) {
        return (IntersectionType) Type.super.asArrayWithTypeArguments(dimensions, typeArguments);
    }

    @NotNull
    @Override
    default AccessFlag[] accessFlags() {
        return Utils.DEFAULT_ACCESS_FLAGS;
    }

    @Override
    @NotNull
    default String fullName() {
        return String.join(" & ", Arrays.stream(types()).map(Type::fullName).toArray(String[]::new));
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

    @NotNull
    @Override
    default Optional<Type[]> interfaces() {
        return Optional.empty();
    }


    @NotNull
    @Override
    default Optional<TypeParameter[]> typeParameters() {
        return Optional.empty();
    }

    @NotNull
    @Override
    default Optional<TypeArgument[]> typeArguments() {
        return Optional.empty();
    }

    @NotNull
    @Override
    default Type superClass() {
        Type[] types = types();
        if (types == null || types.length == 0) return Type.ofClass(Object.class);

        // GLB: find a type among the components that is a subtype ofMethod all others
        for (Type candidate : types) {
            boolean ok = true;
            for (Type other : types) {
                if (candidate == other) continue;
                boolean isSubtype = false;
                for (Type anc = candidate; anc != null; anc = anc.superClass()) {
                    if (anc.equals(other)) {
                        isSubtype = true;
                        break;
                    }
                }
                if (!isSubtype) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return candidate;
            }
        }
        return Type.ofClass(Object.class);
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

    @NotNull
    @Override
    default Attribute[] attributes() {
        return Utils.EMPTY_ATTRIBUTES;
    }

    static @NotNull IntersectionType ofClasses(@NotNull Class<?>[] classes) {
        return new IntersectionTypeImpl(
                Arrays.stream(classes)
                        .map(TypeFactory::ofClass)
                        .toArray(Type[]::new)
        );
    }

    static @NotNull IntersectionType ofReferences(@NotNull TypeReference<?>[] typeReferences) {
        return new IntersectionTypeImpl(
                Arrays.stream(typeReferences)
                        .map(TypeFactory::ofReference)
                        .toArray(Type[]::new)
        );
    }

    static @NotNull IntersectionType ofTypes(@NotNull java.lang.reflect.Type[] types) {
        return new IntersectionTypeImpl(
                Arrays.stream(types)
                        .map(TypeFactory::ofType)
                        .toArray(Type[]::new)
        );
    }


    static @NotNull IntersectionType ofTypeModels(@NotNull Type[] types) {
        return new IntersectionTypeImpl(
                types
        );
    }
}
