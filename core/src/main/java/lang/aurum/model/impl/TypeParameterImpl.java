package lang.aurum.model.impl;

import lang.aurum.model.Type;
import lang.aurum.model.TypeParameter;

public record TypeParameterImpl(
        String name,
        Type bound
) implements TypeParameter {
}
