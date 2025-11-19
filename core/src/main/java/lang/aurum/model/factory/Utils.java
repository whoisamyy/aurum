package lang.aurum.model.factory;

import lang.aurum.model.Type;
import lang.aurum.model.TypeParameter;
import lang.aurum.model.impl.TypeParameterImpl;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.TypeVariable;
import java.util.*;

public final class Utils {
    public static Class<?> getComponent(Class<?> clazz) {
        while (clazz.isArray())
            clazz = clazz.componentType();
        return clazz;
    }

    private static final HashMap<Integer, Type> typeCache = new HashMap<>();

    public static void processTypeParameters(Type type, GenericDeclaration generic) {
        TypeVariable<?>[] typeParameters = generic.getTypeParameters();
        for (int i = 0; i < typeParameters.length; i++) {
            TypeVariable<?> typeVariable = typeParameters[i];
            Type bound;
            int key = Arrays.hashCode(typeVariable.getBounds());
            if (typeCache.containsKey(key)) {
                bound = typeCache.get(key);
            } else {
                bound = TypeFactory.ofTypes(typeVariable.getBounds());
                typeCache.put(key, bound);
            }
            TypeParameterImpl apply = new TypeParameterImpl(typeVariable.getName(), bound);
            int finalI = i;
            type.typeParameters().ifPresent(
                    params -> params[finalI] = apply
            );
        }
    }

    public static Optional<TypeParameter[]> getTypeParameters(GenericDeclaration generic) {
        var typeParams = new TypeParameter[generic.getTypeParameters().length];

        if (typeParams.length == 0)
            return Optional.empty();

        return Optional.of(typeParams);
    }

    public static <T> void addAll(SequencedSet<T> set, T[] elements) {
        for (var el : elements) {
            set.addLast(el);
        }
    }
}
