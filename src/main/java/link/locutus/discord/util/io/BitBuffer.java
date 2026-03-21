package link.locutus.discord.util.io;

import link.locutus.discord.util.IOUtil;

import java.nio.charset.StandardCharsets;

public class BitBuffer {
    private static final long[] MASK = new long[Long.SIZE + 1];
    static {
        for (int i = 0; i < Long.SIZE; i++) {
            MASK[i] = (1L << i) - 1;
        }
        MASK[Long.SIZE] = -1L;
    }

    private long buffer;
    private int bitsInBuffer;
    private byte[] byteArray;
    private int offset;

    public BitBuffer(int capacity) {
        this(new byte[capacity]);
    }

    private BitBuffer(byte[] byteBuffer) {
        this.buffer = 0L;
        this.bitsInBuffer = 0;
        this.byteArray = byteBuffer;
    }

    public void writeBits(long value, int count) {
        int remainingBits = 64 - bitsInBuffer;

        // If the new value fits entirely within the buffer, write it directly.
        if (count <= remainingBits) {
            long mask = MASK[count];
            buffer |= (value & mask) << bitsInBuffer;
            bitsInBuffer += count;
            if (bitsInBuffer == 64) {
                flush();
            }
        } else {
            // Write as much as possible to the current buffer.
            buffer |= (value & MASK[remainingBits]) << bitsInBuffer;
            flush();

            // Write the remaining bits in another iteration.
            int remainingCount = count - remainingBits;
            buffer |= (value >>> remainingBits) & MASK[remainingCount];
            bitsInBuffer = remainingCount;
        }
    }

    public long readBits(int count) {
        long result;
        if (count <= bitsInBuffer) {
            result = buffer & MASK[count];
            buffer >>>= count;
            bitsInBuffer -= count;
        } else {
            int remainingCount = count - bitsInBuffer;
            result = buffer & MASK[bitsInBuffer];

            buffer = IOUtil.readLong(byteArray, offset);
            offset += Long.BYTES;

            result |= (buffer & MASK[remainingCount]) << bitsInBuffer;
            buffer >>>= remainingCount;
            bitsInBuffer = 64 - remainingCount;
        }

        return result;
    }

    private void flush() {
        IOUtil.writeLong(byteArray, buffer, offset);
        offset += Long.BYTES;

        buffer = 0;
        bitsInBuffer = 0;
    }

    public byte[] getWrittenBytes() {
        int numBytesInBuffer = (bitsInBuffer + 7) / 8;
        byte[] bytes = new byte[offset + numBytesInBuffer];
        System.arraycopy(byteArray, 0, bytes, 0, offset);
        if (numBytesInBuffer > 0) {
            int startIndex = offset;
            for (int i = 0; i < numBytesInBuffer; i++) {
                bytes[startIndex + i] = (byte) (buffer & 0xFF);
                buffer >>>= 8;
            }
        }
        return bytes;
    }

    public long readLong() {
        return readBits(Long.SIZE);
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

    public BitBuffer reset() {
        offset = 0;
        buffer = 0;
        bitsInBuffer = 0;
        return this;
    }

    public void setBytes(byte[] data) {
        System.arraycopy(data, 0, byteArray, 0, Math.min(data.length, byteArray.length));
        int mod = data.length % Long.BYTES;
        for (int i = data.length; i < data.length + mod; i++) {
            byteArray[i] = 0;
        }
        offset = 0;
        buffer = 0;
        bitsInBuffer = 0;
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

    public void writeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        for (byte current : bytes) {
            writeByte(Byte.toUnsignedInt(current));
        }
    }

    public String readString() {
        int length = readVarInt();
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) readByte();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

}
