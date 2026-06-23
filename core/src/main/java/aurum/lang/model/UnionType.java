package aurum.lang.model;

import aurum.lang.model.factory.TypeFactory;
import aurum.lang.model.impl.UnionTypeImpl;
import aurum.lang.model.impl.Utils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;
import java.util.*;

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
        if (types.length == 1) return types[0];

        // The least upper bound of a union is the most specific type that is a supertype of
        // every member. Candidate supertypes include class inheritance (superclass chain)
        // as well as directly and transitively implemented/extended interfaces, since two
        // unrelated classes may still share a common interface (e.g. String | StringBuilder
        // -> CharSequence).
        List<Type> candidates = types[0].getAllSupertypes();
        Set<Type> common = new HashSet<>(candidates);

        // Intersect with the ancestor sets of every other member so only common ancestors remain.
        for (int i = 1; i < types.length; i++) {
            Set<Type> otherSet = new HashSet<>(types[i].getAllSupertypes());
            common.retainAll(otherSet);
        }

        // Pick the most specific common ancestor: the one that is a subtype of all the others.
        // Iterate in declaration order (self, then superclass chain and interfaces of the first
        // member) so that when several incomparable interfaces are common the first-declared one
        // wins deterministically.
        Type best = null;
        for (Type candidate : candidates) {
            if (!common.contains(candidate)) continue;
            if (candidate.equals(Types.OBJECT)) continue; // defer Object until last
            if (best == null || candidate.isSubclassOf(best)) {
                best = candidate;
            }
        }

        return best != null ? best : Types.OBJECT;
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

    static @NotNull UnionType ofClasses(@NotNull Class<?>... classes) {
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
