package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import link.locutus.discord.util.StringMan;

import java.nio.charset.StandardCharsets;

public final class FrontCodedStringMap implements ICodedStringMap {
    private final int blockSize;
    private final ByteArrayList data = new ByteArrayList();
    private final IntArrayList entryOffsets = new IntArrayList();
    private final IntArrayList blockOffsets = new IntArrayList();
    private final Long2IntOpenHashMap hashes = new Long2IntOpenHashMap();

    private String currentAnchor = null; // transient: only for building current block
    private long totalChars = 0;
    private int duplicates = 0;

    public FrontCodedStringMap() {
        this(16);
    }

    public FrontCodedStringMap(int blockSize) {
        if (blockSize <= 0) throw new IllegalArgumentException("blockSize <= 0");
        this.blockSize = blockSize;
        hashes.defaultReturnValue(-1);
    }

    @Override
    public int insert(String value) {
        final long h = StringMan.hash(value);
        final int existing = hashes.get(h);
        if (existing >= 0) {
            duplicates++;
            return existing;
        }

        final int idx = entryOffsets.size();
        if ((idx % blockSize) == 0) {
            // Anchor
            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            final int off = data.size();
            blockOffsets.add(off);
            entryOffsets.add(off);
            writeVarInt(data, bytes.length);
            data.addElements(data.size(), bytes);
            currentAnchor = value;
        } else {
            // Non-anchor: store LCP-bytes (with anchor) + suffix bytes
            final Lcp l = lcpUtf8(currentAnchor, value);
            final String suffix = value.substring(l.lcpCharsInB);
            final byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);

            final int off = data.size();
            entryOffsets.add(off);
            writeVarInt(data, l.lcpBytes);
            writeVarInt(data, suffixBytes.length);
            data.addElements(data.size(), suffixBytes);
        }

        hashes.put(h, idx);
        totalChars += value.length();
        return idx;
    }

    @Override
    public String get(int index) {
        if (index < 0 || index >= size()) throw new IndexOutOfBoundsException();

        final byte[] a = data.elements();
        if ((index % blockSize) == 0) {
            // anchor
            int[] p = { entryOffsets.getInt(index) };
            final int len = readVarInt(a, p);
            final int start = p[0];
            return new String(a, start, len, StandardCharsets.UTF_8);
        } else {
            final int block = index / blockSize;
            // parse anchor header to get its data start
            int[] pa = { blockOffsets.getInt(block) };
            final int anchorLen = readVarInt(a, pa); // not used directly, but advances pa
            final int anchorStart = pa[0];

            // parse entry header
            int[] pe = { entryOffsets.getInt(index) };
            final int lcpBytes = readVarInt(a, pe);
            final int suffixLen = readVarInt(a, pe);
            final int suffixStart = pe[0];

            // assemble bytes
            final byte[] out = new byte[lcpBytes + suffixLen];
            if (lcpBytes > 0) System.arraycopy(a, anchorStart, out, 0, lcpBytes);
            if (suffixLen > 0) System.arraycopy(a, suffixStart, out, lcpBytes, suffixLen);
            return new String(out, StandardCharsets.UTF_8);
        }
    }

    @Override
    public int size() {
        return entryOffsets.size();
    }

    @Override
    public int countDuplicates() {
        return duplicates;
    }

    @Override
    public int stringLength() {
        return (int) Math.min(Integer.MAX_VALUE, totalChars);
    }

    // Helpers

    private static void writeVarInt(ByteArrayList out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.add((byte) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.add((byte) v);
    }

    private static int readVarInt(byte[] a, int[] posRef) {
        int pos = posRef[0];
        int result = 0;
        int shift = 0;
        int b;
        do {
            b = a[pos++] & 0xFF;
            result |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        posRef[0] = pos;
        return result;
    }

    private static int utf8LenOfCodePoint(int cp) {
        if (cp <= 0x7F) return 1;
        if (cp <= 0x7FF) return 2;
        if (cp <= 0xFFFF) return 3;
        return 4;
    }

    private static final class Lcp {
        int lcpCharsInB; // number of UTF-16 chars consumed in 'b'
        int lcpBytes;    // number of UTF-8 bytes in that LCP
    }

    // Computes the LCP between 'a' (anchor) and 'b' in code points,
    // returning bytes length of the LCP in UTF-8 and char index in 'b'.
    private static Lcp lcpUtf8(String a, String b) {
        int ia = 0, ib = 0;
        int ba = a.length(), bb = b.length();
        int bytes = 0;

        while (ia < ba && ib < bb) {
            int cpa = a.codePointAt(ia);
            int cpb = b.codePointAt(ib);
            if (cpa != cpb) break;
            bytes += utf8LenOfCodePoint(cpa);
            ia += Character.charCount(cpa);
            ib += Character.charCount(cpb);
        }
        Lcp l = new Lcp();
        l.lcpBytes = bytes;
        l.lcpCharsInB = ib;
        return l;
    }
}