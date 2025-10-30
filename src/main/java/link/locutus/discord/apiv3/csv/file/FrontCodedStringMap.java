package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class FrontCodedStringMap implements ICodedStringMap {
    private final int blockSize;
    private final ByteArrayList data        = new ByteArrayList();
    private final IntArrayList  entryOffsets = new IntArrayList();
    private final IntArrayList  anchorStarts = new IntArrayList();
    private final IntArrayList  anchorLens   = new IntArrayList();
    private final Long2IntOpenHashMap hashes = new Long2IntOpenHashMap();

    private int currentAnchorStart = -1;
    private int currentAnchorLen   = 0;

    private long totalChars = 0;
    private int duplicates  = 0;

    private static final long FNV64_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV64_PRIME        = 0x100000001b3L;

    private static final ThreadLocal<EncodeContext> ENCODE_CTX =
            ThreadLocal.withInitial(EncodeContext::new);
    private static final ThreadLocal<DecodeContext> DECODE_CTX =
            ThreadLocal.withInitial(DecodeContext::new);

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
        final EncodeContext ec = ENCODE_CTX.get();
        encodeUtf8AndHash(value, ec);
        final byte[] encoded   = ec.buffer;
        final int    encodedLen = ec.length;
        final long   h          = ec.hash;

        final int existing = hashes.get(h);
        if (existing >= 0) {
            duplicates++;
            return existing;                // same behaviour as before: hash assumed unique
        }

        final int idx = entryOffsets.size();
        final int off = data.size();
        entryOffsets.add(off);

        if ((idx % blockSize) == 0) {
            // Anchor
            writeLen(data, encodedLen);
            final int anchorStart = data.size();
            if (encodedLen > 0) {
                data.addElements(anchorStart, encoded, 0, encodedLen);
            }
            currentAnchorStart = anchorStart;
            currentAnchorLen   = encodedLen;
            anchorStarts.add(anchorStart);
            anchorLens.add(encodedLen);
        } else {
            // Non-anchor: LCP with the current block anchor + suffix
            final byte[] store = data.elements();               // array after the anchor (no copy)
            final int lcp = lcpBytes(store, currentAnchorStart, currentAnchorLen,
                    encoded, encodedLen);
            final int suffixLen = encodedLen - lcp;

            writeLen(data, lcp);
            writeLen(data, suffixLen);
            if (suffixLen > 0) {
                data.addElements(data.size(), encoded, lcp, suffixLen);
            }
        }

        hashes.put(h, idx);
        totalChars += value.length();
        return idx;
    }

    @Override
    public String get(int index) {
        if (index < 0 || index >= size()) throw new IndexOutOfBoundsException();

        final byte[] store = data.elements();

        if ((index % blockSize) == 0) {
            final int block = index / blockSize;
            final int start = anchorStarts.getInt(block);
            final int len   = anchorLens.getInt(block);
            return new String(store, start, len, StandardCharsets.UTF_8);
        }

        final DecodeContext dc = DECODE_CTX.get();
        final byte[] out = materializeNonAnchor(index, dc, store);
        return new String(out, 0, dc.length, StandardCharsets.UTF_8);
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

    /* ---------------------------------------------------------------------- */
    /* Internals                                                              */
    /* ---------------------------------------------------------------------- */

    private static final int ONE_BYTE_LIMIT = 0x80;   // 0..127
    private static final int TWO_BYTE_LIMIT = 0x4000; // 0..16383

    private static void writeLen(ByteArrayList out, int value) {
        if (value < ONE_BYTE_LIMIT) {
            out.add((byte) value);
        } else if (value < TWO_BYTE_LIMIT) {
            out.add((byte) ((value >>> 8) | 0x80));
            out.add((byte) value);
        } else {
            out.add((byte) 0xFF);
            out.add((byte) (value >>> 24));
            out.add((byte) (value >>> 16));
            out.add((byte) (value >>> 8));
            out.add((byte) value);
        }
    }

    private static int readLen(byte[] a, int[] posRef) {
        int pos = posRef[0];
        int b = a[pos++] & 0xFF;
        if (b < 0x80) {
            posRef[0] = pos;
            return b;
        }
        if (b != 0xFF) {
            int hi = b & 0x7F;
            int lo = a[pos++] & 0xFF;
            posRef[0] = pos;
            return (hi << 8) | lo;
        }
        int v = ((a[pos] & 0xFF) << 24)
                | ((a[pos + 1] & 0xFF) << 16)
                | ((a[pos + 2] & 0xFF) << 8)
                |  (a[pos + 3] & 0xFF);
        posRef[0] = pos + 4;
        return v;
    }

    private static int lcpBytes(byte[] anchor, int anchorStart, int anchorLen,
                                byte[] candidate, int candidateLen) {
        final int limit = Math.min(anchorLen, candidateLen);
        int i = 0;
        int pos = anchorStart;
        while (i < limit && anchor[pos + i] == candidate[i]) {
            i++;
        }
        return i;
    }

    private static void encodeUtf8AndHash(String s, EncodeContext ctx) {
        final int needed = s.length() * 4;
        ensureEncodeCapacity(ctx, needed);
        final byte[] dst = ctx.buffer;

        long h = FNV64_OFFSET_BASIS;
        int dp = 0;

        for (int i = 0, len = s.length(); i < len; i++) {
            final char c = s.charAt(i);
            if (c < 0x80) {
                final int b = c;
                dst[dp++] = (byte) b;
                h ^= b;
                h *= FNV64_PRIME;
            } else if (c < 0x800) {
                final int b1 = 0xC0 | (c >>> 6);
                final int b2 = 0x80 | (c & 0x3F);
                dst[dp++] = (byte) b1;
                dst[dp++] = (byte) b2;
                h ^= b1; h *= FNV64_PRIME;
                h ^= b2; h *= FNV64_PRIME;
            } else if (!Character.isSurrogate(c)) {
                final int b1 = 0xE0 | (c >>> 12);
                final int b2 = 0x80 | ((c >>> 6) & 0x3F);
                final int b3 = 0x80 | (c & 0x3F);
                dst[dp++] = (byte) b1;
                dst[dp++] = (byte) b2;
                dst[dp++] = (byte) b3;
                h ^= b1; h *= FNV64_PRIME;
                h ^= b2; h *= FNV64_PRIME;
                h ^= b3; h *= FNV64_PRIME;
            } else {
                final int cp = Character.codePointAt(s, i);
                final int b1 = 0xF0 | (cp >>> 18);
                final int b2 = 0x80 | ((cp >>> 12) & 0x3F);
                final int b3 = 0x80 | ((cp >>> 6)  & 0x3F);
                final int b4 = 0x80 | (cp & 0x3F);
                dst[dp++] = (byte) b1;
                dst[dp++] = (byte) b2;
                dst[dp++] = (byte) b3;
                dst[dp++] = (byte) b4;
                h ^= b1; h *= FNV64_PRIME;
                h ^= b2; h *= FNV64_PRIME;
                h ^= b3; h *= FNV64_PRIME;
                h ^= b4; h *= FNV64_PRIME;
                i += Character.charCount(cp) - 1;
            }
        }

        ctx.length = dp;
        ctx.hash   = h;
    }

    private static void ensureEncodeCapacity(EncodeContext ctx, int needed) {
        if (ctx.buffer.length >= needed) return;
        int newLen = ctx.buffer.length == 0 ? 1 : ctx.buffer.length;
        while (newLen < needed) newLen <<= 1;
        ctx.buffer = Arrays.copyOf(ctx.buffer, newLen);
    }

    private byte[] materializeNonAnchor(int index, DecodeContext ctx, byte[] store) {
        final int block = index / blockSize;
        final int anchorStart = anchorStarts.getInt(block);

        final int[] pe = { entryOffsets.getInt(index) };
        final int lcp        = readLen(store, pe);
        final int suffixLen  = readLen(store, pe);
        final int suffixStart = pe[0];

        final int total = lcp + suffixLen;
        ensureDecodeCapacity(ctx, total);
        final byte[] out = ctx.buffer;

        System.arraycopy(store, anchorStart, out, 0, lcp);
        if (suffixLen > 0) {
            System.arraycopy(store, suffixStart, out, lcp, suffixLen);
        }

        ctx.length = total;
        return out;
    }

    private static void ensureDecodeCapacity(DecodeContext ctx, int needed) {
        if (ctx.buffer.length >= needed) return;
        int newLen = ctx.buffer.length == 0 ? 1 : ctx.buffer.length;
        while (newLen < needed) newLen <<= 1;
        ctx.buffer = Arrays.copyOf(ctx.buffer, newLen);
    }

    /* ---------------------------------------------------------------------- */
    /* Small helper holders for the thread-local scratch spaces               */
    /* ---------------------------------------------------------------------- */

    private static final class EncodeContext {
        byte[] buffer = new byte[64];
        int    length;
        long   hash;
    }

    private static final class DecodeContext {
        byte[] buffer = new byte[64];
        int    length;
    }
}