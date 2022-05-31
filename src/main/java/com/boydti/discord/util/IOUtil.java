package com.boydti.discord.util;

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
}
