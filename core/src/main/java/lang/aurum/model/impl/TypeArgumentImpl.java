package lang.aurum.model.impl;

import lang.aurum.model.Type;
import lang.aurum.model.TypeArgument;

public record TypeArgumentImpl(
        String name,
        Type bound
) implements TypeArgument {
}
