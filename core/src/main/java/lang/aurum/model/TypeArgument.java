package lang.aurum.model;

import lang.aurum.model.impl.TypeArgumentImpl;

public interface TypeArgument {
    String name();
    Type bound();

    static TypeArgument of(String name, Type bound) {
        return new TypeArgumentImpl(name, bound);
    }
}
