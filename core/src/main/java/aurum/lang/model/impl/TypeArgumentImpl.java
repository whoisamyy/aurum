package aurum.lang.model.impl;

import aurum.lang.model.Type;
import aurum.lang.model.TypeArgument;

public record TypeArgumentImpl(
        String name,
        Type bound
) implements TypeArgument {
}
