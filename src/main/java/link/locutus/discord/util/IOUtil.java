package link.locutus.discord.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public class IOUtil {

    public static void writeVarInt(OutputStream out, int i) throws IOException {
        while ((i & -128) != 0) {
            out.write(i & 127 | 128);
            i >>>= 7;
        }
        out.write(i);
    }

    public static int readVarInt(InputStream in) throws IOException {
        int i = 0;
        int offset = 0;
        int b;
        while ((b = in.read()) > 127) {
            i |= b - 128 << offset;
            offset += 7;
        }
        i |= b << offset;
        return i;
    }

    public static void writeVarLong(OutputStream out, long n) throws IOException {
        while ((n & 0xFFFF_FFFF_FFFF_FF80L) != 0) {// While we have more than 7 bits (0b0xxxxxxx)
            byte data = (byte) (n | 0x80);// Discard bit sign and set msb to 1 (VarInt byte prefix).
            out.write(data);
            n >>>= 7;
        }
        out.write((byte) n);
    }

    public static long readVarLong(InputStream in) throws IOException {
        long value = 0L;
        int i = 0;
        long b;
        while (((b = in.read()) & 0x80L) != 0) {
            value |= (b & 0x7F) << i;
            i += 7;
        }
        return value | (b << i);
    }

    public static void writeLong(byte[] data, long value, int offset) {
        for (int i = 0; i < Long.BYTES; i++) {
            data[offset + i] = (byte) (value >>> (8 * i));
        }
    }

    public static long readLong(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            value |= ((long) data[offset + i] & 0xFF) << (i * 8);
        }
        return value;
    }

    public static void main(String[] args) {
        Random RANDOM = new Random(0);

        {
            byte[] data = new byte[Long.BYTES];
            for (int i = 0; i < 1000; i++) {
                long value = RANDOM.nextLong();

                // prepare data with ByteBuffer
                ByteBuffer
                        .wrap(data)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putLong(0, value);

                long fromUtil = IOUtil.readLong(data, 0);

//                assertEquals(value, fromUtil,
//                        "IOUtil.readLong did not match ByteBuffer for value " + value);
                if (value != fromUtil) {
                    throw new AssertionError("IOUtil.readLong did not match ByteBuffer for value " + value);
                }
            }
        }
        {
            byte[] data = new byte[Long.BYTES];
            for (int i = 0; i < 1000; i++) {
                long value = RANDOM.nextLong();
                IOUtil.writeLong(data, value,0 );

                long fromBuffer = ByteBuffer
                        .wrap(data)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getLong(0);

//                assertEquals(value, fromBuffer,
//                        "IOUtil.writeLong did not match ByteBuffer for value " + value);
                if (value != fromBuffer) {
                    throw new AssertionError("IOUtil.writeLong did not match ByteBuffer for value " + value);
                }
            }
        }
        System.out.println("All tests passed!");
    }

}
