package aurum.lang.model;

import aurum.lang.model.factory.TypeFactory;
import aurum.lang.model.impl.IntersectionTypeImpl;
import aurum.lang.model.impl.Utils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public interface IntersectionType extends Type {
    Type[] types();

    @Override
    @NotNull IntersectionType withTypeArguments(TypeArgument @NotNull [] typeArguments);

    @Override
    @NotNull IntersectionType withTypeArguments(Type @NotNull [] typeArguments);

    @Override
    @NotNull IntersectionType withDefaultTypeArguments();

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

    @NotNull
    @Override
    default Type superClass() {
        Type[] types = types();
        //noinspection RedundantLengthCheck
        if (types == null || types.length == 0) return Types.OBJECT;

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
