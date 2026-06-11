package aurum.lang.model.impl;

import aurum.lang.model.Type;
import aurum.lang.model.TypeArgument;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record TypeArgumentImpl(
        String name,
        Type bound
) implements TypeArgument {
    private static final Map<String, Map<Type, TypeArgumentImpl>> pool = new ConcurrentHashMap<>();

    public static TypeArgumentImpl of(String name, Type bound) {
        return pool.computeIfAbsent(name, _ -> new ConcurrentHashMap<>())
                   .computeIfAbsent(bound, b -> new TypeArgumentImpl(name, b));
    }
}
