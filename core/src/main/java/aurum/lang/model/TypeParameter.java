package aurum.lang.model;

import aurum.lang.model.impl.TypeParameterImpl;

public interface TypeParameter {
    String name();
    Type bound();

    default TemplateType toTemplate() {
        return TemplateType.of(name());
    }

    static TypeParameter of(String name, Type bound) {
        return TypeParameterImpl.of(name, bound);
    }

    static TypeParameter of(String name) {
        return TypeParameterImpl.of(name, Types.OBJECT);
    }
}
