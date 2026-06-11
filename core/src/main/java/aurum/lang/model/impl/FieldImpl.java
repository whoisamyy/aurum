package aurum.lang.model.impl;

import aurum.lang.model.Attribute;
import aurum.lang.model.Field;
import aurum.lang.model.Type;

import java.lang.reflect.AccessFlag;

public record FieldImpl(
        Type owner,
        String name,
        Type type,
        Attribute[] attributes,
        AccessFlag[] accessFlags
) implements Field {}
