package aurum.lang.model;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

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

        var argedType = iteratorType.withTypeArguments(stringType);
        var optionalMethod = argedType.findMethod("next");
        assertTrue(optionalMethod.isPresent());
        assertEquals(Types.STRING, optionalMethod.get().returnType());
    }

    @Test
    public void lowestCommonAncestorWith_sharedInterface_test() {
        assertEquals(
                Type.ofClass(Comparable.class),
                Type.ofClass(Integer.class).lowestCommonAncestorWith(Type.ofClass(String.class))
        );
    }

    @Test
    public void lowestCommonAncestorWith_sharedClass_test() {
        assertEquals(
                Type.ofClass(Number.class),
                Type.ofClass(Integer.class).lowestCommonAncestorWith(Type.ofClass(Long.class))
        );
    }

    @Test
    public void lowestCommonAncestorWith_unrelated_returns_object_test() {
        assertEquals(
                Types.OBJECT,
                Type.ofClass(Thread.class).lowestCommonAncestorWith(Type.ofClass(String.class))
        );
    }

    @Test
    public void lowestCommonAncestorWith_null_returns_object_test() {
        assertEquals(Types.OBJECT, Types.STRING.lowestCommonAncestorWith(null));
    }

    @Test
    public void unionType_superClass_sharedInterface_test() {
        Type union = UnionType.ofClasses(String.class, StringBuilder.class);
        Type result = union.superClass();
        assertNotEquals(Types.OBJECT, result);
        assertTrue(
                result.equals(Type.ofClass(CharSequence.class))
                        || result.equals(Type.ofClass(Comparable.class))
                        || result.equals(Type.ofClass(Serializable.class)),
                "expected a shared interface but was: " + result.fullName()
        );
    }

    @Test
    public void unionType_superClass_sharedClass_test() {
        // Integer | Long -> Number
        Type union = UnionType.ofClasses(Integer.class, Long.class);
        assertEquals(Type.ofClass(Number.class), union.superClass());
    }

    @Test
    public void unionType_superClass_unrelated_returns_object_test() {
        Type union = UnionType.ofClasses(Thread.class, String.class);
        assertEquals(Types.OBJECT, union.superClass());
    }
}
