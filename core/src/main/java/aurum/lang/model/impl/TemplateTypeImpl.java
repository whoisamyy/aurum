package aurum.lang.model.impl;

import aurum.lang.model.TemplateType;
import aurum.lang.model.Type;
import org.jetbrains.annotations.NotNull;

public record TemplateTypeImpl(
        String className,
        int arrayDimensions
) implements TemplateType {
    @NotNull
    @Override
    public Type asArray(int dimensions) {
        return new TemplateTypeImpl(className, arrayDimensions + dimensions);
    }

    @Override
    public String toUsageString() {
        return className + "[]".repeat(arrayDimensions);
    }
}
