package lang.aurum.model.factory;

import lang.aurum.model.*;
import lang.aurum.model.impl.IntersectionTypeImpl;
import lang.aurum.model.impl.PrimitiveTypeImpl;
import lang.aurum.model.impl.TypeImpl;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;

public final class TypeFactory {
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
        int arrayDimensions = (int) clazz.descriptorString().chars().filter(c -> c == '[').count();
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

        Optional<Type[]> interfaceTypes = interfaces.length == 0
                ? Optional.empty()
                : Optional.of(new Type[interfaces.length]);

        TypeImpl type = new TypeImpl(
                clazz.getSimpleName(),
                clazz.getPackageName(),
                null,
                interfaceTypes,
                new Field[clazz.getFields().length],
                new Method[clazz.getMethods().length],
                clazz.accessFlags().toArray(AccessFlag[]::new),
                lang.aurum.model.impl.Utils.EMPTY_ATTRIBUTES,
                Utils.getTypeParameters(clazz),
                Optional.empty()
        );

        cache.put(arrayClass, type);

        populateType(clazz, type);

        return type;
    }

    private static void populateType(Class<?> clazz, TypeImpl type) {
        Utils.processTypeParameters(type, clazz);

        Type superClass = clazz.getSuperclass() != null
                ? ofClass(clazz.getSuperclass())
                : null;
        if (!type.isPrimitive() && !type.fullName().equals("java.lang.Object"))
            type.setSuperClass(Objects.requireNonNullElse(superClass, Type.ofClass(Object.class)));

        Class<?>[] interfaces = clazz.getInterfaces();
        Arrays.stream(interfaces)
              .map(TypeFactory::ofClass)
              .toList().toArray(type.interfaces().orElse(lang.aurum.model.impl.Utils.EMPTY_TYPES));

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

        for (var inter : type.interfaces().orElse(lang.aurum.model.impl.Utils.EMPTY_TYPES)) {
            Utils.addAll(methods, inter.methods());
        }

        System.arraycopy(fields.toArray(Field[]::new), 0, type.fields(), 0, type.fields().length);
        System.arraycopy(methods.toArray(Method[]::new), 0, type.methods(), 0, type.methods().length);
    }


    public static Type ofReference(TypeReference<?> reference) {
        return ofType(reference.getType());
    }

    public static Type ofType(java.lang.reflect.Type type) {
        if (cache1.containsKey(type))
            return cache1.get(type);

        if (type instanceof ParameterizedType parameterized) {
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
        } else {
            try {
                Type retType = ofClass(Class.forName(type.getTypeName()));
                cache1.put(type, retType);
                return retType;
            } catch (ClassNotFoundException e) {
                Type retType = ofClass(Object.class);
                cache1.put(type, retType);
                return retType;
            }
        }
    }

    /// Since this method is used only for handling generic type arguments based on Java classes
    /// it is totally fine to assume that resulting type will be intersection of given types
    /// @param types Types to form an intersection type
    /// @return Returns [IntersectionType] with [Type]s provided by types parameter
    static Type ofTypes(java.lang.reflect.Type[] types) {
        int key = Arrays.hashCode(types);
        if (cache3.containsKey(key))
            return cache3.get(key);

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

}
