package link.locutus.discord.util.io;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

public class BitBuffer {
    private static final byte[] MASKS = new byte[Long.SIZE];

    static {
        for (int i = 0; i < MASKS.length; i++)
            MASKS[i] = (byte) (Math.pow(2, i) - 1);
    }


    private ByteBuffer bytes;
    private int position = 0;
    private byte bit = 0;
    private long buffer;

    public BitBuffer(ByteBuffer bytes) {
        this.bytes = bytes;
        buffer = bytes.getLong(position);
    }


    public void writeBits(long value, int count) {
        buffer |= value >>> bit;

        //Save the full buffer to memory and pull out the next one.
        if ((bit += count) > 64)
            buffer = bytes.putLong(position++).getLong(position) | value << (bit += -64);
    }

    public long readBits(int count) {
        long value = buffer << bit;
        if ((bit += count) > 64)
            value |= (buffer = bytes.getLong(position++)) >>> (bit += -64);
        return value & MASKS[count];
    }

    public void setBytes(byte[] data) {
        // reset the bitbuffer to these bytes
        bytes.clear();
        bytes.put(data);
        bytes.position(0);

        position = 0;
        bit = 0;
        buffer = bytes.getLong(position);
    }

    public boolean readBit() {
        return readBits(1) == 1;
    }

    public void writeBit(boolean value) {
        writeBits(value ? 1 : 0, 1);
    }

    public int readInt() {
        return (int) readBits(Integer.SIZE);
    }

    public int readByte() {
        return (int) readBits(Byte.SIZE);
    }

    public char readChar() {
        return (char) readBits(Character.SIZE);
    }

    public short readShort() {
        return (short) readBits(Short.SIZE);
    }

    public long readLong() {
        return readBits(Long.SIZE);
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public void writeInt(int value) {
        writeBits(value, Integer.SIZE);
    }

    public void writeByte(int value) {
        writeBits(value, Byte.SIZE);
    }

    public void writeChar(char value) {
        writeBits(value, Character.SIZE);
    }

    public void writeShort(short value) {
        writeBits(value, Short.SIZE);
    }

    public void writeLong(long value) {
        writeBits(value, Long.SIZE);
    }

    public void writeFloat(float value) {
        writeInt(Float.floatToIntBits(value));
    }

    public void writeDouble(double value) {
        writeLong(Double.doubleToLongBits(value));
    }

    public BitBuffer position(int position) {
        this.position = position;
        return this;
    }

    public BitBuffer clear() {
        return position(0);
    }

    public void writeVarInt(int value) {
        while ((value & -128) != 0) {
            writeByte(value & 127 | 128);
            value >>>= 7;
        }
        writeByte(value);
    }

    public int readVarInt() {
        int value = 0;
        int i = 0;
        int b;
        while (((b = readByte()) & 128) != 0) {
            value |= (b & 127) << i;
            i += 7;
            if (i > 35)
                throw new RuntimeException("VarInt too big");
        }
        return value | b << i;
    }

    public void writeVarLong(long value) {
        while ((value & -128L) != 0L) {
            writeByte((int) (value & 127L) | 128);
            value >>>= 7;
        }
        writeByte((int) value);
    }

    public long readVarLong() {
        long value = 0L;
        int i = 0;
        long b;
        while (((b = readByte()) & 128L) != 0L) {
            value |= (b & 127L) << i;
            i += 7;
            if (i > 63)
                throw new RuntimeException("VarLong too big");
        }
        return value | b << i;
    }
}
