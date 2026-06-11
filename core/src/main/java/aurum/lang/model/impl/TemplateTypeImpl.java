package aurum.lang.model.impl;

import aurum.lang.model.TemplateType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record TemplateTypeImpl(
        String className
) implements TemplateType {
    private static final Map<String, TemplateTypeImpl> pool = new ConcurrentHashMap<>();

    @Override
    public String toUsageString() {
        return className;
    }

    public static TemplateTypeImpl of(String name) {
        return pool.computeIfAbsent(name, TemplateTypeImpl::new);
    }
}
