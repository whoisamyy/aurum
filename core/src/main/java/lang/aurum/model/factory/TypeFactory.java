package lang.aurum.model.factory;

import lang.aurum.model.*;
import lang.aurum.model.impl.IntersectionTypeImpl;
import lang.aurum.model.impl.TypeImpl;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;

public class TypeFactory {
    private static final Map<Class<?>, Type> cache = new HashMap<>();

    public static Type ofClass(Class<?> clazz) {
        if (cache.containsKey(clazz))
            return cache.get(clazz);
        Class<?> arrayClass = clazz;
        int arrayDimensions = (int) clazz.descriptorString().chars().filter(c -> c == '[').count();
        clazz = Utils.getComponent(clazz);
        Class<?>[] interfaces = clazz.getInterfaces();

        SequencedSet<Field> fields = Arrays.stream(clazz.getFields())
                .map(FieldFactory::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));


        SequencedSet<Method> methods = Arrays.stream(clazz.getMethods())
                .map(MethodFactory::ofMethod)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        methods.addAll(Arrays.stream(clazz.getConstructors())
                .map(MethodFactory::ofConstructor)
                .collect(Collectors.toSet()));

        Type superClass = ofClass(clazz.getSuperclass());

        // custom implementation is used to ensure order of addition so that member searches are returning
        // implemented / declared in current Type member and only then returning superclass or interfaces'
        // implementations
        Utils.addAll(methods, superClass.methods());
        Utils.addAll(fields, superClass.fields());


        Optional<Type[]> interfaceTypes = interfaces.length == 0
                ? Optional.empty()
                : Optional.of(Arrays.stream(interfaces).map(TypeFactory::ofClass).toArray(Type[]::new));

        for (var inter : interfaceTypes.orElse(lang.aurum.model.impl.Utils.EMPTY_TYPES)) {
            Utils.addAll(methods, inter.methods());
        }

        TypeImpl type = new TypeImpl(
                clazz.getSimpleName(),
                clazz.getPackageName(),
                superClass,
                interfaceTypes,
                arrayDimensions,
                fields.toArray(Field[]::new),
                methods.toArray(Method[]::new),
                clazz.accessFlags().toArray(AccessFlag[]::new),
                lang.aurum.model.impl.Utils.EMPTY_ATTRIBUTES,
                Utils.getTypeParameters(clazz),
                Optional.empty()
        );
        cache.put(arrayClass, type);
        return type;
    }


    public static Type ofReference(TypeReference<?> reference) {
        return ofType(reference.getType());
    }

    public static Type ofType(java.lang.reflect.Type type) {
        if (type instanceof ParameterizedType parameterized) {
            Type auType = ofType(parameterized.getRawType());
            return auType.withTypeArguments(
                    Arrays.stream(parameterized.getActualTypeArguments())
                            .map(TypeFactory::ofType)
                            .toArray(Type[]::new)
            );
        } else {
            try {
                return ofClass(Class.forName(type.getTypeName()));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /// Since this method is used only for handling generic type arguments based on Java classes
    /// it is totally fine to assume that resulting type will be intersection of given types
    /// @param types Types to form an intersection type
    /// @return Returns [IntersectionType] with [Type]s provided by types parameter
    static Type ofTypes(java.lang.reflect.Type[] types) {
        return new IntersectionTypeImpl(
                Arrays.stream(types)
                        .map(TypeFactory::ofType)
                        .toArray(Type[]::new),
                0
        );
    }

}
