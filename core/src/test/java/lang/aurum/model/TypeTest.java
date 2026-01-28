package lang.aurum.model;

import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypeTest {


    @Test
    public void getInheritanceDistance_Integer_Object_test() {
        assertEquals(2, Type.ofClass(Integer.class).getInheritanceDistance(Types.OBJECT));
    }

    @Test
    public void getInheritanceDistance_String_CharSequence_test() {
        assertEquals(1, Types.STRING.getInheritanceDistance(Type.ofClass(CharSequence.class)));
    }

    @Test
    public void withTypeArguments_Iterator_String_test() {
        var stringType = Types.STRING;
        var iteratorType = Type.ofClass(Iterator.class);

        var argedType = iteratorType.withTypeArguments(new Type[]{stringType});
        var optionalMethod = argedType.findMethod("next");
        assertTrue(optionalMethod.isPresent());
        assertEquals(Types.STRING, optionalMethod.get().returnType());
    }
}
