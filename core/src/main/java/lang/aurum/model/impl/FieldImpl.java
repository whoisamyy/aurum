package lang.aurum.model.impl;

import lang.aurum.model.*;

import java.lang.reflect.AccessFlag;
import java.util.Arrays;

public record FieldImpl(
        String name,
        Type type,
        Attribute[] attributes,
        AccessFlag[] accessFlags
) implements Field {}
