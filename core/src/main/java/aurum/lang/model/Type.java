package aurum.lang.model;

import aurum.lang.model.factory.TypeFactory;
import aurum.lang.model.impl.ArrayTypeImpl;
import aurum.lang.model.impl.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.classfile.TypeKind;
import java.util.*;

public interface Type extends Accessible, Attributable, Generic {
    @NotNull String className();
    @NotNull String pkg();
    default @NotNull String fullName() {
        return pkg().isEmpty() ? className() : pkg() + "." + className();
    }
    @Nullable Type superClass();
    @NotNull Type @NotNull[] interfaces();
    default Member[] members() {
        int fieldsLen = fields().length;
        int methodsLen = methods().length;
        Member[] members = new Member[fieldsLen + methodsLen];
        System.arraycopy(fields(), 0, members, 0, fieldsLen);
        System.arraycopy(methods(), 0, members, fieldsLen, methodsLen);
        return members;
    }
    @NotNull Field[] fields();
    @NotNull Method[] methods();

    default boolean isPlainType() {
        return true;
    }
    default boolean isPrimitive() {
        return false;
    }
    default boolean isArray() {
        return false;
    }

    /// Calculates the inheritance distance between this type and the given type.
    /// <p>
    /// The distance represents the number of inheritance steps between two related types:
    /// <p>
    /// - `0` — both types are equal <p>
    /// - `1` — direct superclass or directly implemented/extended interface <p>
    /// - `n > 1` — indirect inheritance relationship <p>
    /// - `-1` — types are unrelated <p>
    /// <p>
    /// Examples:
    /// <p>
    /// ```java
    /// Integer -> Number = 1
    /// Integer -> Object = 2
    /// ArrayList -> List = 1
    /// ArrayList -> Collection = 2
    /// String -> Runnable = -1
    /// ```
    /// <p>
    /// Interface inheritance is resolved recursively through implemented or extended interfaces.
    ///
    /// @param other target type to calculate distance to
    /// @return inheritance distance between this type and {@code other},
    /// or {@code -1} if no inheritance relationship exists
    /// @throws NullPointerException if {@code other} is {@code null}
    ///
    default int getInheritanceDistance(Type other) {
        if (other == null)
            throw new NullPointerException("Cannot calculate inheritance distance from " + this.toUsageString() + " to null");

        if (this.equals(other))
            return 0;

        if (!this.isSubclassOf(other) && !this.isSuperclassOf(other))
            return -1;

        if (Arrays.asList(this.interfaces()).contains(other)
                || Arrays.asList(other.interfaces()).contains(this)
                || (this.superClass() != null && this.superClass().equals(other))
                || (other.superClass() != null && other.superClass().equals(this)))
            return 1;

        if (other.isInterface()) {
            return Arrays.stream(this.interfaces())
                         .reduce(
                                 0,
                                 (depth, type) -> depth + type.getInheritanceDistance(other),
                                 Integer::sum
                         );
        }

        return Optional.ofNullable(superClass()).map(t -> t.getInheritanceDistance(other) + 1).orElse(0);
    }

    default boolean isSubclassOf(Type other) {
        if (this.fullName().equals(other.fullName()))
            return true;

        if (this instanceof PrimitiveType primitiveType) {
            if (primitiveType.boxed().isSubclassOf(other))
                return true;
        } else if (other instanceof PrimitiveType primitiveType) {
            if (other.isSubclassOf(primitiveType.boxed()))
                return true;
        }

        switch (other) {
            case TemplateType _ -> {
                return true;
            }
            case UnionType union -> {
                return Arrays.stream(union.types()).anyMatch(this::isSubclassOf);
            }
            case IntersectionType intersection -> {
                return Arrays.stream(intersection.types()).allMatch(this::isSubclassOf);
            }
            default -> {}
        }

        if (other.fullName().equals("java.lang.Object"))
            return true;

        if (this.interfaces().length > 0) {
            if (Arrays.asList(this.interfaces()).contains(other))
                return true;
        }

        if (this.superClass() != null) {
            if (this.superClass().equals(other))
                return true;

            if (this.superClass().isSubclassOf(other))
                return true;
        }

        for (var inter : interfaces()) {
            if (inter.isSubclassOf(other))
                return true;
        }

        return false;
    }

    default boolean isSuperclassOf(Type other) {
        return other.isSubclassOf(this);
    }


    /// @param dimensions dimensions or depth of the resulting ArrayType
    /// @return returns Type with array depth of dimensions
    default @NotNull Type asArray(int dimensions) {
        if (dimensions == 0)
            return this;

        return new ArrayTypeImpl<>(this, dimensions);
    }

    /// @return returns ArrayType with array depth of 1.
    default @NotNull Type asArray() {
        return this.asArray(1);
    }


    /// Creates a copy of this type with the given type arguments applied. For example, the following class:
    ///
    /// ```
    /// class Example<T, U> extends Other<T, String> {
    ///     private T field;
    ///
    ///     public <V extends U> V doThings(T param) {
    ///         // ...
    ///     }
    /// }
    /// ```
    /// after applying
    /// ```new TypeArgument[]{TypeArgument.of("T", Integer), TypeArgument.of("U", List<Integer>)}```
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
    @NotNull Type withTypeArguments(TypeArgument @NotNull ... typeArguments);
    @Override
    @NotNull Type withTypeArguments(Type @NotNull ... typeArguments);
    @Override
    @NotNull Type withDefaultTypeArguments();

    default @NotNull Type asArrayWithTypeArguments(int dimensions, TypeArgument[] typeArguments) {
        return asArray(dimensions).withTypeArguments(typeArguments);
    }

    default @NotNull Type getRawType() {
        return typeArguments().length > 0
                ? Objects.requireNonNull(TypeFactory.TypePool.get(this.fullName()))
                : this;
    }

    default @NotNull TypeKind typeKind() {
        return TypeKind.REFERENCE;
    }

    /// Searches for method with signature that exactly matches provided signature in [methods][#methods()] of this class
    /// @param name Name of the method
    /// @param returnType Return type of the method
    /// @param parameterTypes types of parameters of the method
    /// @return Returns [Method] with provided signature or none if no method found
    default @NotNull Optional<@NotNull Method> findMethodExact(@NotNull String name, @NotNull Type returnType, @NotNull Type @NotNull [] parameterTypes) {
        return Arrays.stream(methods())
                .filter(m -> name.equals(m.name()))
                .filter(m -> returnType.equals(m.returnType()))
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

    /// Searches for method with signature that exactly matches provided signature in [methods][#methods()] of this class
    /// @param name Name of the method
    /// @param returnType Return type of the method
    /// @param parameters parameters of the method
    /// @return Returns [Method] with provided signature or none if no method found
    default @NotNull Optional<@NotNull Method> findMethodExact(@NotNull String name, @NotNull Type returnType, @NotNull Parameter @NotNull [] parameters) {
        return findMethodExact(name, returnType, Arrays.stream(parameters).map(Parameter::type).toArray(Type[]::new));
    }

    /// Searches for method with signature that exactly matches provided signature in [methods][#methods()] of this class <br>
    /// @param name Name of the method
    /// @param parameters parameters of the method. If none are provided then parameters will be empty.
    /// @return Returns [Method] with provided signature or none if no method found
    default @NotNull Optional<@NotNull Method> findMethodExact(@NotNull String name, @NotNull Parameter @NotNull [] parameters) {
        return findMethodExact(name, Arrays.stream(parameters).map(Parameter::type).toArray(Type[]::new));
    }

    /// Searches for method with signature that exactly matches provided signature in [methods][#methods()] of this class <br>
    /// @param name Name of the method
    /// @param parameterTypes types of parameters of the method. If none are provided then parameter types will be empty.
    /// @return Returns [Method] with provided signature or none if no method found
    default @NotNull Optional<@NotNull Method> findMethodExact(@NotNull String name, @NotNull Type @NotNull [] parameterTypes) {
        return Arrays.stream(methods())
                     .filter(m -> name.equals(m.name()))
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

    /// Searches for method with signature that exactly matches provided signature in [methods][#methods()] of this class
    /// @param name Name of the method
    /// @param returnType Return type of the method
    /// @return Returns [Method] with provided signature or none if no method found
    default @NotNull Optional<@NotNull Method> findMethodExact(@NotNull String name, @NotNull Type returnType) {
        return findMethodExact(name, returnType, Utils.EMPTY_TYPES);
    }

    /// @param name Name of methods
    /// @param returnType Return type of methods
    /// @return Returns array of methods with given name and return type
    default @NotNull Method[] getMethodsExact(@NotNull String name, @NotNull Type returnType) {
        return Arrays.stream(methods())
                     .filter(m -> returnType.equals(m.returnType()))
                     .filter(m -> name.equals(m.name()))
                     .toArray(Method[]::new);
    }

    /// Searches for method with signature that matches provided signature in [methods][#methods()] of this class. <br>
    /// Note that returned method can contain superclasses of provided parameter types as its parameter types. <br>
    /// If exact signature is required then it is recommended to use [findMethodExact][#findMethodExact(String, Type, Type\[\])] method
    /// @param name Name of the method
    /// @param returnType Return type of the method
    /// @param parameterTypes types of parameters of the method
    /// @return Returns [Method] or none if no method found
    default @NotNull Optional<@NotNull Method> findMethod(@NotNull String name, @NotNull Type returnType, @NotNull Type @NotNull [] parameterTypes) {
        return Arrays.stream(methods())
                .filter(m -> name.equals(m.name()))
                .filter(m -> !m.isSynthetic())
                .filter(m -> !m.isBridge())
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

    /// Searches for method with signature that matches provided signature in [methods][#methods()] of this class. <br>
    /// Note that returned method can contain superclasses of provided parameter types as its parameter types. <br>
    /// If exact signature is required then it is recommended to use [findMethodExact][#findMethodExact(String, Type, Type\[\])] method
    /// @param name Name of the method
    /// @param returnType Return type of the method
    /// @param parameters parameters of the method
    /// @return Returns [Method] or none if no method found
    default @NotNull Optional<@NotNull Method> findMethod(@NotNull String name, @NotNull Type returnType, @NotNull Parameter @NotNull [] parameters) {
        return findMethod(name, returnType, Arrays.stream(parameters).map(Parameter::type).toArray(Type[]::new));
    }

    /// Searches for method with signature that matches provided signature in [methods][#methods()] of this class. <br>
    /// Note that returned method can contain superclasses of provided parameter types as its parameter types. <br>
    /// If exact signature is required then it is recommended to use [findMethodExact][#findMethodExact(String, Type, Type\[\]))] method
    /// @param name Name of the method
    /// @param parameterTypes types of parameters of the method
    /// @return Returns [Method] or none if no method found
    default @NotNull Optional<@NotNull Method> findMethod(@NotNull String name, @NotNull Type @NotNull [] parameterTypes) {
        return Arrays.stream(methods())
                     .filter(m -> name.equals(m.name()))
                     .filter(m -> !m.isSynthetic())
                     .filter(m -> !m.isBridge())
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

    /// Searches for method with signature that matches provided signature in [methods][#methods()] of this class. <br>
    /// Note that returned method can contain superclasses of provided parameter types as its parameter types. <br>
    /// If exact signature is required then it is recommended to use [findMethodExact][#findMethodExact(String, Type, Type\[\]))] method
    /// @param name Name of the method
    /// @param parameters parameters of the method
    /// @return Returns [Method] or none if no method found
    default @NotNull Optional<@NotNull Method> findMethod(@NotNull String name, @NotNull Parameter @NotNull [] parameters) {
        return findMethod(name, Arrays.stream(parameters).map(Parameter::type).toArray(Type[]::new));
    }

    /// Searches for method with signature that matches provided signature in [methods][#methods()] of this class. <br>
    /// Note that returned method can contain superclasses of provided parameter types as its parameter types. <br>
    /// If exact signature is required then it is recommended to use [findMethodExact][#findMethodExact(String, Type)] method
    /// @param name Name of the method
    /// @param returnType Return type of the method
    /// @return Returns [Method] or none if no method found
    default @NotNull Optional<@NotNull Method> findMethod(@NotNull String name, @NotNull Type returnType) {
        return findMethod(name, returnType, Utils.EMPTY_TYPES);
    }

    /// Searches for method with the same name in [methods][#methods()] of this class. <br>
    /// Note that returned method can have any return type and parameter types. <br>
    /// If exact signature is required then it is recommended to use [findMethodExact][#findMethodExact(String, Type)] method
    /// @param name Name of the method
    /// @return Returns [Method] or none if no method found
    default @NotNull Optional<@NotNull Method> findMethod(@NotNull String name) {
        return Arrays.stream(methods())
                     .filter(m -> name.equals(m.name()))
                     .filter(m -> !m.isSynthetic())
                     .filter(m -> !m.isBridge())
                     .findFirst();
    }

    /// @param name Name of methods
    /// @param returnType Return type of methods. Allows subclasses. If exact return type is required
    /// then use [getMethodsExact][#getMethodsExact(String, Type)] method
    /// @return Returns array of methods with given name and return type
    default @NotNull Method[] getMethods(@NotNull String name, @NotNull Type returnType) {
        return Arrays.stream(methods())
                     .filter(m -> returnType.isSubclassOf(m.returnType()))
                     .filter(m -> name.equals(m.name()))
                     .toArray(Method[]::new);
    }

    ///
    /// @param name Name of methods
    /// @return Returns array of methods with given name
    default @NotNull Method[] getMethods(@NotNull String name) {
        return Arrays.stream(methods())
                     .filter(m -> name.equals(m.name()))
                     .toArray(Method[]::new);
    }

    default @NotNull Optional<Field> findField(@NotNull String name) {
        return Arrays.stream(fields())
                .filter(f -> name.equals(f.name()))
                .findFirst();
    }

    default @NotNull List<@NotNull Type> getAllInterfaces() {
        return Arrays.stream(this.interfaces())
                     .flatMap(t -> t.getAllInterfaces().stream())
                     .toList();
    }

    /// Collects all supertypes of this type, including itself, its superclass chain, and all
    /// directly or transitively implemented/extended interfaces.
    ///
    /// @return a list containing this type followed by all of its ancestors
    default @NotNull List<@NotNull Type> getAllSupertypes() {
        List<Type> result = new ArrayList<>();
        Set<Type> seen = new HashSet<>();
        Deque<Type> queue = new ArrayDeque<>();
        queue.add(this);
        while (!queue.isEmpty()) {
            Type current = queue.poll();
            if (!seen.add(current)) continue;
            result.add(current);
            Type sup = current.superClass();
            if (sup != null) queue.add(sup);
            queue.addAll(Arrays.asList(current.interfaces()));
        }
        return result;
    }

    default String toUsageString() {
        return switch (this) {
            case IntersectionType intersection ->
                    "("
                  + String.join(" & ", Arrays.stream(intersection.types()).map(Type::toUsageString).toArray(String[]::new))
                  + ")";
            case UnionType union ->
                    "("
                  + String.join(" | ", Arrays.stream(union.types()).map(Type::toUsageString).toArray(String[]::new))
                  + ")";
            case Type type -> {
                String retString = type.fullName();
                if (type.typeArguments().length > 0) {
                    var args = type.typeArguments();
                    if (args.length != 0) {
                        retString += "<" + String.join(
                                ", ",
                                Arrays.stream(args)
                                      .map(TypeArgument::bound)
                                      .map(Type::toUsageString)
                                      .toArray(String[]::new)
                        ) + ">";
                    }
                }
                if (type instanceof ArrayType<?> arrayType)
                    retString += "[]".repeat(arrayType.arrayDimensions());

                yield retString;
            }
        };
    }

    default Type lowestCommonAncestorWith(Type other) {
        if (other == null) return Types.OBJECT;
        Set<Type> otherSet = new HashSet<>(other.getAllSupertypes());

        // Prefer the most specific common supertype. Walk this type's ancestors in order
        // (self first, then superclasses and interfaces) and pick the first one shared
        // with `other`, deferring java.lang.Object so a shared interface/class wins over it.
        for (Type candidate : this.getAllSupertypes()) {
            if (candidate.equals(Types.OBJECT)) {
                continue;
            }
            if (otherSet.contains(candidate)) {
                return candidate;
            }
        }

        return Types.OBJECT;
    }

    static @NotNull Type ofClass(Class<?> clazz) {
        return TypeFactory.ofClass(clazz);
    }

    static @NotNull Type ofReference(TypeReference<?> reference) {
        return TypeFactory.ofReference(reference);
    }

    static @NotNull Type ofType(java.lang.reflect.Type type) {
        return TypeFactory.ofType(type);
    }

    class Comparator implements java.util.Comparator<Type> {
        public static final Comparator INSTANCE = new Comparator();

        @Override
        public int compare(Type t1, Type t2) {
            if (t1.isSubclassOf(t2)) {
                return -t1.getInheritanceDistance(t2);
            }
            if (t1.isSuperclassOf(t2)) {
                return t1.getInheritanceDistance(t2);
            }

            return 0;
        }
    }
}
