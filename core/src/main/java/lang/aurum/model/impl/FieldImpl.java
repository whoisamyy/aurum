package lang.aurum.model.impl;

import lang.aurum.model.*;

import java.lang.reflect.AccessFlag;

public record FieldImpl(
        Type owner,
        String name,
        Type type,
        Attribute[] attributes,
        AccessFlag[] accessFlags
) implements Field {}
