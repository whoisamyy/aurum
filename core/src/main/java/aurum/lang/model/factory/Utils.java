package aurum.lang.model.factory;

import aurum.lang.model.Generic;
import aurum.lang.model.Type;
import aurum.lang.model.TypeParameter;
import aurum.lang.model.impl.TypeParameterImpl;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.SequencedSet;

public final class Utils {
    public static Class<?> getComponent(Class<?> clazz) {
        while (clazz.isArray())
            clazz = clazz.componentType();
        return clazz;
    }

    private static final HashMap<Integer, Type> typeCache = new HashMap<>();

    public static void processTypeParameters(Generic type, GenericDeclaration generic) {
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
            type.typeParameters()[i] = apply;
        }
    }

    public static TypeParameter[] getTypeParameters(GenericDeclaration generic) {
        var typeParams = new TypeParameter[generic.getTypeParameters().length];

        if (typeParams.length == 0)
            return aurum.lang.model.impl.Utils.EMPTY_TYPE_PARAMETERS;

        return typeParams;
    }

    public static <T> void addAll(SequencedSet<T> set, T[] elements) {
        for (var el : elements) {
            if (el != null) {  // Filter out null elements
                set.addLast(el);
            }
        }
    }
}
