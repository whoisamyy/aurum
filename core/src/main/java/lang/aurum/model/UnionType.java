package lang.aurum.model;

import lang.aurum.model.factory.TypeFactory;
import lang.aurum.model.impl.IntersectionTypeImpl;
import lang.aurum.model.impl.UnionTypeImpl;
import lang.aurum.model.impl.Utils;

import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.Optional;

public interface UnionType extends Type {
    Type[] types();

    @Override
    default AccessFlag[] accessFlags() {
        return Utils.DEFAULT_ACCESS_FLAGS;
    }

    @Override
    default String className() {
        return "";
    }

    @Override
    default String pkg() {
        return "";
    }

    @Override
    default Optional<Type[]> interfaces() {
        return Optional.empty();
    }

    @Override
    default Optional<TypeParameter[]> typeParameters() {
        return Optional.empty();
    }

    @Override
    default Optional<TypeArgument[]> typeArguments() {
        return Optional.empty();
    }


    static UnionType ofClasses(Class<?>[] classes) {
        return new UnionTypeImpl(
                Arrays.stream(classes)
                        .map(TypeFactory::ofClass)
                        .toArray(Type[]::new),
                0
        );
    }

    static UnionType ofReferences(TypeReference<?>[] typeReferences) {
        return new UnionTypeImpl(
                Arrays.stream(typeReferences)
                        .map(TypeFactory::ofReference)
                        .toArray(Type[]::new),
                0
        );
    }

    static UnionType ofTypes(java.lang.reflect.Type[] types) {
        return new UnionTypeImpl(
                Arrays.stream(types)
                        .map(TypeFactory::ofType)
                        .toArray(Type[]::new),
                0
        );
    }
}
