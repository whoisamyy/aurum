package aurum.lang.model.impl;

import aurum.lang.model.Type;
import aurum.lang.model.Types;
import org.junit.jupiter.api.Test;

import java.lang.classfile.TypeKind;

import static org.junit.jupiter.api.Assertions.*;

public class PrimitiveTypeImplTest {

    @Test
    public void jvmName_mapping_test() {
        assertEquals("V", Types.VOID.jvmName());
        assertEquals("Z", Types.BOOLEAN.jvmName());
        assertEquals("B", Types.BYTE.jvmName());
        assertEquals("S", Types.SHORT.jvmName());
        assertEquals("C", Types.CHAR.jvmName());
        assertEquals("I", Types.INT.jvmName());
        assertEquals("F", Types.FLOAT.jvmName());
        assertEquals("J", Types.LONG.jvmName());
        assertEquals("D", Types.DOUBLE.jvmName());
    }

    @Test
    public void className_mapping_test() {
        assertEquals("void", Types.VOID.className());
        assertEquals("boolean", Types.BOOLEAN.className());
        assertEquals("byte", Types.BYTE.className());
        assertEquals("short", Types.SHORT.className());
        assertEquals("char", Types.CHAR.className());
        assertEquals("int", Types.INT.className());
        assertEquals("float", Types.FLOAT.className());
        assertEquals("long", Types.LONG.className());
        assertEquals("double", Types.DOUBLE.className());
    }

    @Test
    public void typeKind_mapping_test() {
        assertEquals(TypeKind.VOID, Types.VOID.typeKind());
        assertEquals(TypeKind.BOOLEAN, Types.BOOLEAN.typeKind());
        assertEquals(TypeKind.BYTE, Types.BYTE.typeKind());
        assertEquals(TypeKind.SHORT, Types.SHORT.typeKind());
        assertEquals(TypeKind.CHAR, Types.CHAR.typeKind());
        assertEquals(TypeKind.INT, Types.INT.typeKind());
        assertEquals(TypeKind.FLOAT, Types.FLOAT.typeKind());
        assertEquals(TypeKind.LONG, Types.LONG.typeKind());
        assertEquals(TypeKind.DOUBLE, Types.DOUBLE.typeKind());
    }

    @Test
    public void asArray_zero_dimensions_returns_self_test() {
        assertSame(Types.INT, Types.INT.asArray(0));
        assertSame(Types.CHAR, Types.CHAR.asArray(0));
    }

    @Test
    public void asArray_positive_dimensions_returns_array_type_test() {
        Type intArray = Types.INT.asArray(1);
        assertNotSame(Types.INT, intArray);
        assertEquals("int", intArray.className());

        Type long3dArray = Types.LONG.asArray(3);
        assertNotSame(Types.LONG, long3dArray);
        assertEquals("long", long3dArray.className());
    }

    @Test
    public void primitives_have_expected_defaults_test() {
        // package is empty
        assertEquals("", Types.INT.pkg());
        // super class is Object
        assertEquals(Types.OBJECT, Types.INT.superClass());
        // no interfaces, type params or args, default methods/fields/attributes
        assertEquals(0, Types.INT.interfaces().length);
        assertEquals(0, Types.INT.typeParameters().length);
        assertEquals(0, Types.INT.typeArguments().length);
        assertEquals(0, Types.INT.members().length);
        assertEquals(0, Types.INT.fields().length);
        assertEquals(0, Types.INT.methods().length);
        assertEquals(0, Types.INT.attributes().length);
        assertTrue(Types.INT.isPrimitive());
    }
}
