package lang.aurum.model.factory;

import lang.aurum.model.Method;
import lang.aurum.model.TypeParameter;
import lang.aurum.model.impl.TypeParameterImpl;

import java.lang.reflect.GenericDeclaration;
import java.util.Arrays;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;

public class Utils {
    public static Class<?> getComponent(Class<?> clazz) {
        while (clazz.isArray())
            clazz = clazz.componentType();
        return clazz;
    }

    public static Optional<TypeParameter[]> getTypeParameters(GenericDeclaration generic) {
        var typeParams = Arrays.stream(generic.getTypeParameters())
                .map(param ->
                        new TypeParameterImpl(param.getName(), TypeFactory.ofTypes(param.getBounds()))
                )
                .toArray(TypeParameter[]::new);

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
