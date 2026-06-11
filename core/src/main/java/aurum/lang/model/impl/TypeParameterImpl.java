package aurum.lang.model.impl;

import aurum.lang.model.Type;
import aurum.lang.model.TypeParameter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record TypeParameterImpl(
        String name,
        Type bound
) implements TypeParameter {
    private static final Map<String, Map<Type, TypeParameterImpl>> pool = new ConcurrentHashMap<>();

    public static TypeParameterImpl of(String name, Type bound) {
        return pool.computeIfAbsent(name, _ -> new ConcurrentHashMap<>())
                   .computeIfAbsent(bound, b -> new TypeParameterImpl(name, b));
    }
}
