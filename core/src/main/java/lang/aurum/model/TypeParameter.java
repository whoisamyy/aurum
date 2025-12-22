package lang.aurum.model;

import lang.aurum.model.impl.TypeParameterImpl;

public interface TypeParameter {
    String name();
    Type bound();

    static TypeParameter of(String name, Type bound) {
        return new TypeParameterImpl(name, bound);
    }

    static TypeParameter of(String name) {
        return new TypeParameterImpl(name, Type.ofClass(Object.class));
    }
}
