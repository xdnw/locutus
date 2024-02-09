package com.jpson.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 */
public final class ZigZag {

    public static int writeVarint(OutputStream stream, long value) throws IOException {
        int size = 0;
        while (value >= 0x80) {
            stream.write((byte) ((value & 0x7f) | 0x80));
            ++size;
            value >>= 7;
        }
        stream.write((byte) value);
        return size;
    }

    public static int writeVarLong(OutputStream stream, long value) throws IOException {
        int size = 0;
        while (value >= 0x80) {
            stream.write((byte) ((value & 0x7f) | 0x80));
            ++size;
            value >>= 7;
        }
        stream.write((byte) value);
        return size;
    }

    public static int readVarint32(InputStream stream) throws IOException {
        int value = 0;
        int count = 0;
        byte b;
        do {
            b = (byte) stream.read();
            if (count < 5)
                value |= ((b & 0x7f) << (7 * count));
            ++count;
        } while ((b & 0x80) != 0);
        return value;
    }

    public static long readVarint64(InputStream stream) throws IOException {
        long value = 0;
        int count = 0;
        byte b;
        do {
            b = (byte) stream.read();
            if (count < 10)
                value |= ((long) (b & 0x7f) << (7L * count));
            ++count;
        } while ((b & 0x80) != 0);
        return value;
    }

    public static final long encode(int value) {
        return ((((long) value) << 1) ^ (value >> 31));
    }

    public static final long encode(long value) {
        return (((value |= 0) << 1) ^ (value >> 63));
    }

    public static int decodeInt(long value) {
        return (int) ((value >> 1) ^ -(value & 1));
    }

    public static long decodeLong(long value) {
        return (long) ((value >> 1) ^ (long) -(long) (value & 1));
    }

}