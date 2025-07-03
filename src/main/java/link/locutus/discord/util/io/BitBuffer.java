package link.locutus.discord.util.io;

import link.locutus.discord.util.IOUtil;

public class BitBuffer {
    static void testReadWriteRandom() {
        BitBuffer bitBuffer = new BitBuffer(1024 * 1024); // 1 MB buffer
        for (int i = 0; i < 10_000_000; i++) {
            bitBuffer.reset();
            if (i % 100_000 == 0) {
                System.out.println("Iteration " + i);
            }
            int amtBits = (int) (Math.random() * 128);
            // random between 1 and 16
            int amtInts = (int) (Math.random() * 64);
            // random between 1 and 8
            int amtLongs = (int) (Math.random() * 64);

            // pad to nearest long boundary
            boolean[] expectedBits = new boolean[amtBits];
            for (int j = 0; j < amtBits; j++) {
                boolean bit = Math.random() < 0.5;
                expectedBits[j] = bit;
                bitBuffer.writeBit(bit);
            }
            int[] expectedInts = new int[amtInts];
            for (int j = 0; j < amtInts; j++) {
                int value = (int) (Math.random() * Integer.MAX_VALUE);
                expectedInts[j] = value;
                bitBuffer.writeVarInt(value);
            }
            long[] expectedLongs = new long[amtLongs];
            for (int j = 0; j < amtLongs; j++) {
                long value = (long) (Math.random() * Long.MAX_VALUE);
                expectedLongs[j] = value;
                bitBuffer.writeVarLong(value);
            }
            byte[] data = bitBuffer.getWrittenBytes();
            // test reading back
            bitBuffer.setBytes(data);
            for (int j = 0; j < amtBits; j++) {
                boolean bit = bitBuffer.readBit();
                if (expectedBits[j] != bit) {
                    String bitsStr = "new boolean[] {";
                    for (boolean b : expectedBits) {
                        bitsStr += b + ", ";
                    }
                    bitsStr += "}";
                    System.err.println("Expected bits: " + bitsStr);
                    throw new AssertionError("Bit " + j + " should match, but " + expectedBits[j] + " != " + bit + " | itertation " + i + " | len " + expectedBits.length);
                }
            }
            for (int j = 0; j < amtInts; j++) {
                if (expectedInts[j] != bitBuffer.readVarInt()) {
                    String intsStr = "new int[] {";
                    for (int value : expectedInts) {
                        intsStr += value + ", ";
                    }
                    intsStr += "} | " + expectedBits.length;
                    System.err.println("Expected ints: " + intsStr);
                    throw new AssertionError("Int " + j + " should match");
                }
            }
            for (int j = 0; j < amtLongs; j++) {
                if (expectedLongs[j] != bitBuffer.readVarLong()) {
                    String intsStr = "new int[] {";
                    for (int value : expectedInts) {
                        intsStr += value + ", ";
                    }
                    intsStr += "} | " + expectedBits.length;
                    System.err.println("Expected ints (correct): " + intsStr);
                    String longsStr = "new long[] {";
                    for (long value : expectedLongs) {
                        longsStr += value + ", ";
                    }
                    longsStr += "} | " + expectedBits.length;
                    System.err.println("Expected longs: " + longsStr);
                    throw new AssertionError("Long " + j + " should match");
                }
            }
        }
    }

    public static void main(String[] args) {
        testReadWriteRandom();

        System.out.println("All tests passed!");
    }


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

}
