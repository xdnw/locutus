package com.jpson;

/**
 * PSON byte types.
 */
public final class Token {

    // Small integer values
    public static final byte ZERO = 0x00;  // 0
    //                              0x01;  // -1
    //                              0x02;  // 1
    // zig-zag encoded varints
    public static final byte MAX = (byte) 0xef; // -120, max. zig-zag encoded varint
    public static final int MAX_INT = 0xef; // -120, max. zig-zag encoded varint

    // Special objects
    public static final byte NULL = (byte) 0xf0; // null
    public static final byte TRUE = (byte) 0xf1; // true
    public static final byte FALSE = (byte) 0xf2; // false
    public static final byte EOBJECT = (byte) 0xf3; // {}
    public static final byte EARRAY = (byte) 0xf4; // []
    public static final byte ESTRING = (byte) 0xf5; // ""

    // Objects
    public static final byte OBJECT = (byte) 0xf6; // {……}

    // Arrays
    public static final byte ARRAY = (byte) 0xf7; // [……]

    // Values
    public static final byte INTEGER = (byte) 0xf8; // number (zig-zag encoded varint32)
    public static final byte LONG = (byte) 0xf9;    // Long   (zig-zag encoded varint64)
    public static final byte FLOAT = (byte) 0xfa;   // number (float32)
    public static final byte DOUBLE = (byte) 0xfb;  // number (float64)

    // Strings
    public static final byte STRING = (byte) 0xfc;  // string (varint length + data)
    public static final byte STRING_ADD = (byte) 0xfd;  // string (varint length + data, add to dictionary)
    public static final byte STRING_GET = (byte) 0xfe;  // string (varint index to get from dictionary)

    // Binary
    public static final byte BINARY = (byte) 0xff;  // bytes (varint length + data)
}