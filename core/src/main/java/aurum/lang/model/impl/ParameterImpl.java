package aurum.lang.model.impl;

import aurum.lang.model.Attribute;
import aurum.lang.model.Parameter;
import aurum.lang.model.Type;

public record ParameterImpl(
        String name,
        Type type,
        Attribute[] attributes
) implements Parameter {
    public ParameterImpl(String name, Type type) {
        this(name, type, Utils.EMPTY_ATTRIBUTES);
    }
}
