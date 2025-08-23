package lang.aurum.model;

import java.lang.reflect.ParameterizedType;

/// Helper class that allows for a faster creation ofClass parametrized [Type] instances via factory methods ofClass [Type]
/// @param <T> Referenced type. Might have type arguments
@SuppressWarnings("unused")
public abstract class TypeReference<T> {
    public final java.lang.reflect.Type getType() {
        return ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
}
