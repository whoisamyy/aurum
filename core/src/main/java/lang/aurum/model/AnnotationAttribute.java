package lang.aurum.model;

import java.lang.classfile.AnnotationValue;
import java.util.Map;

public interface AnnotationAttribute extends Attribute {
    Type annotationType();
    Map<String, AnnotationValue> values();
}
