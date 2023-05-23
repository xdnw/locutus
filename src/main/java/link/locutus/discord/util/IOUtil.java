package link.locutus.discord.util;

import org.apache.tomcat.util.buf.ByteBufferUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
}
