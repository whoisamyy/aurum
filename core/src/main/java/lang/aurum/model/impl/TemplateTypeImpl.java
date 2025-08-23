package lang.aurum.model.impl;

import lang.aurum.model.TemplateType;
import lang.aurum.model.Type;

public record TemplateTypeImpl(
        String className,
        int arrayDimensions
) implements TemplateType {
    @Override
    public Type asArray(int dimensions) {
        return new TemplateTypeImpl(className, arrayDimensions + dimensions);
    }
}
