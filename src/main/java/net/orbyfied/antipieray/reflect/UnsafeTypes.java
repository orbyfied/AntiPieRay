package net.orbyfied.antipieray.reflect;

public class UnsafeTypes {

    /* Primitive Type Identifiers */
    public static final byte PT_BYTE   = 0;
    public static final byte PT_SHORT  = 1;
    public static final byte PT_CHAR   = 2;
    public static final byte PT_INT    = 3;
    public static final byte PT_LONG   = 4;
    public static final byte PT_FLOAT  = 5;
    public static final byte PT_DOUBLE = 6;
    public static final byte PT_OBJ    = 7;

    /**
     * Get the primitive type of a holder of
     * the given type.
     *
     * @param klass The type to check.
     * @return The primitive type.
     */
    public static byte getPrimitiveType(Class<?> klass) {
        if (klass.isPrimitive()) {
            if      (klass == byte.class)   return PT_BYTE;
            else if (klass == short.class)  return PT_SHORT;
            else if (klass == char.class)   return PT_CHAR;
            else if (klass == int.class)    return PT_INT;
            else if (klass == long.class)   return PT_LONG;
            else if (klass == float.class)  return PT_FLOAT;
            else if (klass == double.class) return PT_DOUBLE;
        }

        // return reference type / object
        return PT_OBJ;
    }

}
