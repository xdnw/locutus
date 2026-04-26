package link.locutus.discord.db.conflict;

import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.web.jooby.JteUtil;
import org.junit.jupiter.api.Test;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderGroupTest {
    @Test
    void appendMapEntriesRoundTripsFreshNestedMsgpackObject() throws Exception {
        ObjectMapper mapper = JteUtil.getSerializer();

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("name", "test");
        nested.put("metrics", List.of(1, 2, 3));
        nested.put("coalitions", List.of(
                Map.of("id", 1, "cities", List.of(10, 11)),
                Map.of("id", 2, "cities", List.of(12, 13))));

        byte[] groupBytes = mapper.writeValueAsBytes(nested);

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(compressedOut, 1024) {
            {
                def.setLevel(Deflater.BEST_COMPRESSION);
            }
        }; MessagePacker packer = MessagePack.newDefaultPacker(gzipOut)) {
            packer.packMapHeader(nested.size() + 1);
            HeaderGroup.appendMapEntries(packer, groupBytes, nested.size(), HeaderGroup.GRAPH_DATA);
            packer.packString("update_ms");
            packer.packLong(123L);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = mapper.readValue(
                JteUtil.decompress(compressedOut.toByteArray()),
                Map.class);

        assertEquals("test", decoded.get("name"));
        assertEquals(List.of(1, 2, 3), decoded.get("metrics"));
        assertEquals(
                List.of(
                        Map.of("id", 1, "cities", List.of(10, 11)),
                        Map.of("id", 2, "cities", List.of(12, 13))),
                decoded.get("coalitions"));
        assertEquals(123, ((Number) decoded.get("update_ms")).intValue());
    }

    @Test
    void appendMapEntriesRoundTripsMultipleGroupsWithMixedHeaderSizes() throws Exception {
        ObjectMapper mapper = JteUtil.getSerializer();

        // 20-field group (forces map16 header path: 0xDE)
        Map<String, Object> wide = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            wide.put("k" + i, "v" + i);
        }

        // 2-field group (fixmap path: 0x82)
        Map<String, Object> narrow = new LinkedHashMap<>();
        narrow.put("wars", 5);
        narrow.put("active_wars", 2);

        byte[] wideBytes = mapper.writeValueAsBytes(wide);
        byte[] narrowBytes = mapper.writeValueAsBytes(narrow);

        // Sanity: confirm the encoded headers we are exercising.
        assertEquals((byte) 0xDE, wideBytes[0]);
        assertEquals((byte) 0x82, narrowBytes[0]);

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(compressedOut, 1024) {
            {
                def.setLevel(Deflater.BEST_COMPRESSION);
            }
        }; MessagePacker packer = MessagePack.newDefaultPacker(gzipOut)) {
            packer.packMapHeader(wide.size() + narrow.size() + 1);
            HeaderGroup.appendMapEntries(packer, wideBytes, wide.size(), HeaderGroup.PAGE_META);
            HeaderGroup.appendMapEntries(packer, narrowBytes, narrow.size(), HeaderGroup.INDEX_STATS);
            packer.packString("update_ms");
            packer.packLong(456L);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = mapper.readValue(
                JteUtil.decompress(compressedOut.toByteArray()),
                Map.class);

        assertEquals(wide.size() + narrow.size() + 1, decoded.size());
        for (int i = 0; i < 20; i++) {
            assertEquals("v" + i, decoded.get("k" + i));
        }
        assertEquals(5, ((Number) decoded.get("wars")).intValue());
        assertEquals(2, ((Number) decoded.get("active_wars")).intValue());
        assertEquals(456, ((Number) decoded.get("update_ms")).intValue());
    }

    /**
     * Regression: nested {@code mapper.writeValueAsBytes(...)} calls inside the
     * outer write must not break the outer payload. The previous implementation
     * used {@code MessagePackGenerator}, which shares an
     * {@code OutputStreamBufferOutput} via a {@code ThreadLocal} when
     * {@code reuseResourceInGenerator} is true; an inner generator would
     * {@code reset()} that buffer onto the inner {@code ByteArrayOutputStream},
     * redirecting the outer packer's writes and producing a 20-byte
     * (header + trailer only) gzip payload.
     */
    @Test
    void nestedSerializationDoesNotEmptyOuterPayload() throws Exception {
        ObjectMapper mapper = JteUtil.getSerializer();

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("a", 1);
        first.put("b", List.of("x", "y", "z"));

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("c", Map.of("nested", List.of(1, 2, 3)));
        second.put("d", "hello");

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(compressedOut, 1024) {
            {
                def.setLevel(Deflater.BEST_COMPRESSION);
            }
        }; MessagePacker packer = MessagePack.newDefaultPacker(gzipOut)) {
            packer.packMapHeader(first.size() + second.size() + 1);

            // Serialize and append while the outer packer is alive; this used
            // to empty the outer stream via the ThreadLocal buffer hijack.
            byte[] firstBytes = mapper.writeValueAsBytes(first);
            HeaderGroup.appendMapEntries(packer, firstBytes, first.size(), HeaderGroup.GRAPH_META);

            byte[] secondBytes = mapper.writeValueAsBytes(second);
            HeaderGroup.appendMapEntries(packer, secondBytes, second.size(), HeaderGroup.GRAPH_DATA);

            packer.packString("update_ms");
            packer.packLong(789L);
        }

        byte[] compressed = compressedOut.toByteArray();
        // 20 bytes is exactly an empty gzip stream (10-byte header + 2 empty
        // deflate + 8-byte trailer) — guard against the regression directly.
        assertNotEquals(20, compressed.length);
        assertTrue(compressed.length > 20, "expected non-empty gzip payload, got " + compressed.length + " bytes");

        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = mapper.readValue(
                JteUtil.decompress(compressed),
                Map.class);

        assertEquals(1, ((Number) decoded.get("a")).intValue());
        assertEquals(List.of("x", "y", "z"), decoded.get("b"));
        assertEquals(Map.of("nested", List.of(1, 2, 3)), decoded.get("c"));
        assertEquals("hello", decoded.get("d"));
        assertEquals(789, ((Number) decoded.get("update_ms")).intValue());
    }
}
