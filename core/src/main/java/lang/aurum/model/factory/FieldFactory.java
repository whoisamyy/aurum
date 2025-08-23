package lang.aurum.model.factory;

import lang.aurum.model.Field;
import lang.aurum.model.impl.FieldImpl;
import lang.aurum.model.impl.Utils;

import java.lang.reflect.AccessFlag;

public class FieldFactory {
    public static Field of(java.lang.reflect.Field field) {
        return new FieldImpl(
                field.getName(),
                TypeFactory.ofClass(field.getType()),
                Utils.EMPTY_ATTRIBUTES,
                field.accessFlags().toArray(AccessFlag[]::new)
        );
    }
}
