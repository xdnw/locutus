package com.jpson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import com.jpson.internal.*;
import link.locutus.discord.config.Settings;
import link.locutus.discord.web.jooby.AwsManager;
import link.locutus.discord.web.jooby.JteUtil;

/**
 * A low-level PSON writer.
 */
class PsonWriter {

    protected OutputStream output;


    public PsonWriter() {
    }

    public PsonWriter(OutputStream output) {
        if (output == null) {
            throw new IllegalArgumentException("output");
        }
        this.output = output;
    }

    protected void writeNull() throws IOException {
        output.write(Token.NULL);
    }

    protected void writeInt(int num) throws IOException {
        long value = ZigZag.encode(num);
        if (value <= Token.MAX_INT) {
            output.write((byte) value);
        } else if (value <= (2L << 32) - 1) {
            output.write(Token.INTEGER);
            ZigZag.writeVarint(output, value);
        } else {
            output.write(Token.LONG);
            ZigZag.writeVarLong(output, value);
        }
    }

    protected void writeLong(long num) throws IOException {
        long value = ZigZag.encode(num);
        if (value <= Token.MAX_INT) {
            output.write((byte) value);
        } else {
            output.write(Token.LONG);
            ZigZag.writeVarLong(output, value);
        }
    }

    protected void writeFloat(float flt) throws IOException {
        byte[] bytes = BitConverter.toBytes(flt);
        /**
         */
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            ArrayUtils.reverse(bytes);
        }
        output.write(Token.FLOAT);
        output.write(bytes, 0, 4);
    }

    protected void writeDouble(double dbl) throws IOException {
        byte[] bytes = BitConverter.toBytes(dbl);
        /**
         */
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            ArrayUtils.reverse(bytes);
        }
        output.write(Token.DOUBLE);
        output.write(bytes, 0, 8);

    }

    protected void writeBool(boolean b) throws IOException {
        output.write(b ? Token.TRUE : Token.FALSE);

    }

    protected void writeEmptyString() throws IOException {
        output.write(Token.ESTRING);

    }

    protected void writeString(String str) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("str");
        }
        if (str.length() == 0) {
            output.write(Token.ESTRING);
            return;
        }
        output.write(Token.STRING);
        writestringdelimited(str);
    }

    protected void writeStringAdd(String str) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("str");
        }
        if (str.length() == 0)
            throw new IllegalArgumentException("str must not be empty");
        output.write(Token.STRING_ADD);
        writestringdelimited(str);
    }

    protected void writestringdelimited(String str) throws IOException {
        byte[] bytes = str.getBytes("UTF-8");
        ZigZag.writeVarint(output, bytes.length);
        output.write(bytes, 0, bytes.length);

    }

    protected void writeStringGet(int index) throws IOException {
        output.write(Token.STRING_GET);
        ZigZag.writeVarint(output, index);

    }

    protected void writeEmptyArray() throws IOException {
        output.write(Token.EARRAY);

    }

    protected void writeStartArray(int count) throws IOException {
        if (count < 0)
            throw new IllegalArgumentException("count");
        if (count == 0) {
            writeEmptyArray();
            return;
        }
        output.write(Token.ARRAY);
        ZigZag.writeVarint(output, count);

    }

    protected void writeEmptyObject() throws IOException {
        output.write(Token.EOBJECT);

    }

    protected void writeStartObject(int count) throws IOException {
        if (count < 0)
            throw new IllegalArgumentException("count");
        if (count == 0) {
            writeEmptyObject();
            return;
        }
        output.write(Token.OBJECT);
        ZigZag.writeVarint(output, count);

    }
}