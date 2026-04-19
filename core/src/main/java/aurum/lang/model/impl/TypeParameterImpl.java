package aurum.lang.model.impl;

import aurum.lang.model.Type;
import aurum.lang.model.TypeParameter;

public record TypeParameterImpl(
        String name,
        Type bound
) implements TypeParameter {
}
