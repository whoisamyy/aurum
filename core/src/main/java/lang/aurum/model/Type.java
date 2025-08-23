package lang.aurum.model;

import lang.aurum.model.factory.TypeFactory;
import lang.aurum.model.impl.Utils;

import java.lang.classfile.TypeKind;
import java.util.Arrays;
import java.util.Optional;

public interface Type extends Accessible, Attributable, Generic {
    String className();
    String pkg();
    default String fullName() {
        return pkg().isEmpty() ? className() : pkg() + "." + className();
    }
    Type superClass();
    Optional<Type[]> interfaces();
    int arrayDimensions();
    default Member[] members() {
        int fieldsLen = fields().length;
        int methodsLen = methods().length;
        Member[] members = new Member[fieldsLen + methodsLen];
        System.arraycopy(fields(), 0, members, 0, fieldsLen);
        System.arraycopy(methods(), 0, members, fieldsLen, methodsLen);
        return members;
    }
    Field[] fields();
    Method[] methods();

    default boolean isPrimitive() {
        return false;
    }
    default boolean isArray() {
        return arrayDimensions() > 0;
    }

    default boolean isSubclassOf(Type other) {
        if (other.fullName().equals("java.lang.Object"))
            return true;

        if (this.interfaces().isPresent()) {
            if (Arrays.asList(this.interfaces().get()).contains(other))
                return true;
        }

        if (this.superClass().equals(other))
            return true;

        if (this.superClass().isSubclassOf(other))
            return true;

        if (interfaces().isPresent()) {
            for (var inter : interfaces().get()) {
                if (inter.isSubclassOf(other))
                    return true;
            }
        }

        return false;
    }


    Type asArray(int dimensions);



    /// Creates a copy ofMethod this type with the given type arguments applied. For example, the following class:
    ///
    /// ```
    /// class Example<T, U> extends Other<T, String> {
    ///     private T field;
    ///
    ///     public <V extends U> V doThings(T t) {
    ///         // ...
    ///     }
    /// }
    /// ```
    /// after applying
    /// ```new TypeArgument[]{TypeArgument.ofMethod("T", Integer), TypeArgument.ofMethod("U", List<Integer>)}```
    /// turns into
    ///
    /// ```
    /// class Example<Integer, List<Integer>> extends Other<Integer, String> {
    ///     private Integer field;
    ///
    ///     public <V extends List<Integer>> V doThings(Integer t) {
    ///         // ...
    ///     }
    /// }
    /// ```
    /// @param typeArguments type arguments
    /// @return Type with type arguments applied to it.
    @Override
    Type withTypeArguments(TypeArgument[] typeArguments);
    @Override
    Type withTypeArguments(Type[] typeArguments);

    default Type asArrayWithTypeArguments(int dimensions, TypeArgument[] typeArguments) {
        return asArray(dimensions).withTypeArguments(typeArguments);
    }


    default TypeKind typeKind() {
        return TypeKind.REFERENCE;
    }

    default Optional<Method> findMethodExact(String name, Type returnType, Type... parameterTypes) {
        return Arrays.stream(methods())
                .filter(m -> m.name().equals(name))
                .filter(m -> m.returnType().equals(returnType))
                .filter(m -> {
                    Type[] array = Arrays.stream(m.parameters()).map(Parameter::type).toArray(Type[]::new);
                    int arrayLength = array.length;
                    if (arrayLength != parameterTypes.length)
                        return false;
                    for (int i = 0; i < arrayLength; i++) {
                        var type = array[i];
                        if (!type.equals(parameterTypes[i]))
                            return false;
                    }
                    return true;
                })
                .findFirst();
    }

    default Optional<Method> findMethodExact(String name, Type returnType) {
        return findMethodExact(name, returnType, Utils.EMPTY_TYPES);
    }


    default Optional<Method> findMethod(String name, Type returnType, Type... parameterTypes) {
        return Arrays.stream(methods())
                .filter(m -> m.name().equals(name))
                .filter(m -> returnType.isSubclassOf(m.returnType()))
                .filter(m -> {
                    Type[] array = Arrays.stream(m.parameters()).map(Parameter::type).toArray(Type[]::new);
                    int arrayLength = array.length;
                    if (arrayLength != parameterTypes.length)
                        return false;
                    for (int i = 0; i < arrayLength; i++) {
                        var type = array[i];
                        if (!parameterTypes[i].isSubclassOf(type))
                            return false;
                    }
                    return true;
                })
                .findFirst();
    }

    default Optional<Method> findMethod(String name, Type returnType) {
        return findMethod(name, returnType, Utils.EMPTY_TYPES);
    }


    default Optional<Field> findField(String name) {
        return Arrays.stream(fields())
                .filter(f -> f.name().equals(name))
                .findFirst();
    }

    static Type ofClass(Class<?> clazz) {
        return TypeFactory.ofClass(clazz);
    }

    static Type ofReference(TypeReference<?> reference) {
        return TypeFactory.ofReference(reference);
    }

    static Type ofType(java.lang.reflect.Type type) {
        return TypeFactory.ofType(type);
    }
}
