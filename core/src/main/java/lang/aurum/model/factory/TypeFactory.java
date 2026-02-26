package lang.aurum.model.factory;

import kotlin.Pair;
import lang.aurum.model.*;
import lang.aurum.model.impl.IntersectionTypeImpl;
import lang.aurum.model.impl.PrimitiveTypeImpl;
import lang.aurum.model.impl.TypeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.stream.Collectors;

public final class TypeFactory {
    public static class TypePool {
        private TypePool() {}

        private static final Map<Pair<String, @NotNull TypeArgument @NotNull []>, Type> pool = new HashMap<>();

        public static boolean contains(String fullName, TypeArgument[] typeArguments) {
            return pool.containsKey(new Pair<>(fullName, typeArguments));
        }

        public static boolean contains(String className, String pkg, TypeArgument[] typeArguments) {
            return contains("%s.%s".formatted(pkg, className), typeArguments);
        }

        public static @Nullable Type get(String fullName) {
            return pool.entrySet().stream()
                       .filter(kv -> kv.getKey().getFirst().equals(fullName))
                       .min(Comparator.comparingInt(kv -> kv.getKey().getSecond().length))
                       .map(Map.Entry::getValue)
                       .orElse(null);
        }

        public static @Nullable Type get(String className, String pkg) {
            return get("%s.%s".formatted(pkg, className));
        }

        public static @Nullable Type get(String fullName, TypeArgument[] typeArgs) {
            return pool.entrySet().stream()
                       .filter(kv -> kv.getKey().getFirst().equals(fullName)
                               && Arrays.equals(kv.getKey().getSecond(), typeArgs))
                       .findFirst()
                       .map(Map.Entry::getValue)
                       .orElse(null);
        }

        public static @Nullable Type get(String className, String pkg, TypeArgument[] typeArgs) {
            return get("%s.%s".formatted(pkg, className), typeArgs);
        }

        public static Type add(Type type) {
            return pool.computeIfAbsent(new Pair<>(type.fullName(), type.typeArguments()), _ -> type);
        }
    }

    private static final Map<Class<?>, Type> cache = new HashMap<>();
    private static final Map<java.lang.reflect.Type, Type> cache1 = new HashMap<>();
    private static final Map<TypeReference<?>, Type> cache2 = new HashMap<>();
    private static final Map<Integer, Type> cache3 = new HashMap<>();

    public static Type ofClass(Class<?> clazz) {
        if (clazz == null)
            throw new NullPointerException("clazz is null");
        if (cache.containsKey(clazz))
            return cache.get(clazz);

        Class<?> arrayClass = clazz;
        int arrayDimensions = (int) arrayClass.descriptorString().chars().filter(c -> c == '[').count();
        clazz = Utils.getComponent(clazz);
        if (clazz.isPrimitive()) {
            Class<?> finalClazz = clazz;
            PrimitiveTypeImpl primitiveType = Arrays.stream(PrimitiveTypeImpl.values())
                                                    .filter(t -> t.className().equals(finalClazz.getSimpleName()))
                                                    .findFirst().orElseThrow();

            cache.put(clazz, primitiveType);
            if (arrayDimensions == 0)
                return primitiveType;

            Type arrayType = primitiveType.asArray(arrayDimensions);
            cache.put(arrayClass, arrayType);
            return arrayType;
        }

        Class<?>[] interfaces = clazz.getInterfaces();

        Type[] interfaceTypes = interfaces.length == 0
                ? lang.aurum.model.impl.Utils.EMPTY_TYPES
                : new Type[interfaces.length];

        TypeImpl type = new TypeImpl(
                clazz.getName().substring(clazz.getName().lastIndexOf('.')+1),
                clazz.getPackageName(),
                null,
                interfaceTypes,
                new Field[clazz.getFields().length],
                new Method[clazz.getMethods().length + clazz.getConstructors().length],
                clazz.accessFlags().toArray(AccessFlag[]::new),
                lang.aurum.model.impl.Utils.EMPTY_ATTRIBUTES,
                Utils.getTypeParameters(clazz),
                lang.aurum.model.impl.Utils.EMPTY_TYPE_ARGUMENTS
        );

        cache.put(arrayClass, type);

        populateType(clazz, type);

        return type.asArray(arrayDimensions);
    }

    private static void populateType(Class<?> clazz, TypeImpl type) {
        Utils.processTypeParameters(type, clazz);

        Type superClass = clazz.getSuperclass() != null
                ? ofType(clazz.getSuperclass())
                : null;
        if (!type.isPrimitive() && !type.fullName().equals("java.lang.Object"))
            type.setSuperClass(Objects.requireNonNullElse(superClass, Type.ofClass(Object.class)));

        Class<?>[] interfaces = clazz.getInterfaces();
        Type[] newInterfaces = Arrays.stream(interfaces)
              .map(TypeFactory::ofType)
              .toList().toArray(Type[]::new);
        Type[] dest = type.interfaces();
        System.arraycopy(newInterfaces, 0, dest, 0, Integer.min(newInterfaces.length, dest.length));

        processMembers(clazz, type);
    }

    private static void processMembers(Class<?> clazz, TypeImpl type) {
        SequencedSet<Field> fields = Arrays.stream(clazz.getFields())
                                           .map(FieldFactory::of)
                                           .collect(Collectors.toCollection(LinkedHashSet::new));

        SequencedSet<Method> methods = Arrays.stream(clazz.getMethods())
                                             .map(MethodFactory::ofMethod)
                                             .collect(Collectors.toCollection(LinkedHashSet::new));

        methods.addAll(Arrays.stream(clazz.getConstructors())
                             .map(MethodFactory::ofConstructor)
                             .collect(Collectors.toSet()));

        Type superClass = type.superClass();
        if (superClass != null) {
            Utils.addAll(methods, superClass.methods());
            Utils.addAll(fields, superClass.fields());
        }

        for (var inter : type.interfaces()) {
            Utils.addAll(methods, inter.methods());
        }

        Field[] fieldsArray = fields.toArray(Field[]::new);
        Method[] methodsArray = methods.toArray(Method[]::new);
        
        // Filter out any nulls that might have slipped through and create properly sized arrays
        Field[] nonNullFieldsArray = Arrays.stream(fieldsArray).filter(f -> f != null).toArray(Field[]::new);
        Method[] nonNullMethodsArray = Arrays.stream(methodsArray).filter(m -> m != null).toArray(Method[]::new);
        
        // Replace arrays with correctly sized ones using reflection (fields and methods are final)
        try {
            java.lang.reflect.Field fieldsField = TypeImpl.class.getDeclaredField("fields");
            fieldsField.setAccessible(true);
            // Remove final modifier using reflection
            java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            int fieldsModifiers = fieldsField.getModifiers();
            modifiersField.setInt(fieldsField, fieldsModifiers & ~java.lang.reflect.Modifier.FINAL);
            fieldsField.set(type, nonNullFieldsArray);
            
            java.lang.reflect.Field methodsField = TypeImpl.class.getDeclaredField("methods");
            methodsField.setAccessible(true);
            int methodsModifiers = methodsField.getModifiers();
            modifiersField.setInt(methodsField, methodsModifiers & ~java.lang.reflect.Modifier.FINAL);
            methodsField.set(type, nonNullMethodsArray);
        } catch (Exception e) {
            // Fallback: copy what we can, but this will leave nulls if arrays are mismatched
            // This should not happen if reflection works, but provides a safety net
            System.arraycopy(nonNullFieldsArray, 0, type.fields(), 0, Math.min(nonNullFieldsArray.length, type.fields().length));
            System.arraycopy(nonNullMethodsArray, 0, type.methods(), 0, Math.min(nonNullMethodsArray.length, type.methods().length));
        }
    }


    public static Type ofReference(TypeReference<?> reference) {
        return ofType(reference.getType());
    }

    public static Type ofType(java.lang.reflect.Type type) {
        if (cache1.containsKey(type))
            return cache1.get(type);

        switch (type) {
            case Class<?> clazz -> {
                Type ret = ofClass(clazz);
                cache1.put(type, ret);
                return ret;
            }
            case ParameterizedType parameterized -> {
                try {
                    Type auType = ofClass(Class.forName(parameterized.getRawType().getTypeName()));
                    Type retType = auType.withTypeArguments(
                            Arrays.stream(parameterized.getActualTypeArguments())
                                  .map(TypeFactory::ofType)
                                  .toArray(Type[]::new)
                    );
                    cache1.put(type, retType);
                    return retType;
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            case TypeVariable<?> typeVariable -> {
                Type ret = TemplateType.of(typeVariable.getName());
                cache1.put(type, ret);
                return ret;
            }
            default -> {
                try {
                    Type retType = ofClass(Class.forName(type.getTypeName()));
                    cache1.put(type, retType);
                    return retType;
                } catch (ClassNotFoundException e) {
                    Type retType = ofClass(Object.class).asArray((int) type.getTypeName().chars().filter(c -> c == '[').count());
                    cache1.put(type, retType);
                    return retType;
                }
            }
        }
    }

    /// Since this method is used only for handling generic type arguments based on Java classes
    /// resulting type will be an intersection of given types
    /// @param types Types to form an intersection type of
    /// @return Returns [IntersectionType] with [Type]s provided by types parameter
    static Type ofTypes(java.lang.reflect.Type[] types) {
        int key = Arrays.hashCode(types);
        if (cache3.containsKey(key))
            return cache3.get(key);

        if (types.length > 1) {
            IntersectionTypeImpl type = new IntersectionTypeImpl(
                    new Type[types.length]
            );
            cache3.put(key, type);

            Arrays.stream(types)
                  .map(java.lang.reflect.Type::getTypeName)
                  .map(name -> name.replaceAll("<.*>", ""))
                  .map(className -> {
                      try {
                          return Class.forName(className);
                      } catch (ClassNotFoundException e) {
                          throw new RuntimeException(e);
                      }
                  })
                  .map(TypeFactory::ofClass)
                  .toList().toArray(type.types());

            return type;
        }

        try {
            return TypeFactory.ofClass(Class.forName(types[0].getTypeName().replaceAll("<.*>", "")));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}
