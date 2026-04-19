package aurum.lang.model.factory;

import aurum.lang.model.Field;
import aurum.lang.model.impl.FieldImpl;
import aurum.lang.model.impl.Utils;

import java.lang.reflect.AccessFlag;

public final class FieldFactory {
    public static Field of(java.lang.reflect.Field field) {
        return new FieldImpl(
                TypeFactory.ofType(field.getDeclaringClass()),
                field.getName(),
                TypeFactory.ofType(field.getGenericType()),
                Utils.EMPTY_ATTRIBUTES,
                field.accessFlags().toArray(AccessFlag[]::new)
        );
    }
}
