package lang.aurum.model.impl;

import lang.aurum.model.Attribute;
import lang.aurum.model.Parameter;
import lang.aurum.model.Type;

public record ParameterImpl(
        String name,
        Type type,
        Attribute[] attributes
) implements Parameter {}
