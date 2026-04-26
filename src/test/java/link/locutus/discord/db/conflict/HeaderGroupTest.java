package link.locutus.discord.db.conflict;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.web.jooby.JteUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeaderGroupTest {
    @Test
    void writeSerializedMapEntriesRoundTripsMergedMsgpackObject() throws Exception {
        ObjectMapper mapper = JteUtil.getSerializer();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", "test");
        meta.put("start", 12L);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("wars", 5);
        stats.put("active_wars", 2);

        byte[] metaBytes = mapper.writeValueAsBytes(meta);
        byte[] statsBytes = mapper.writeValueAsBytes(stats);

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(compressedOut, 1024) {
            {
                def.setLevel(Deflater.BEST_COMPRESSION);
            }
        }; JsonGenerator out = mapper.getFactory().createGenerator(gzipOut)) {
            out.writeStartObject();
            HeaderGroup.writeSerializedMapEntries(mapper.getFactory(), out, metaBytes);
            HeaderGroup.writeSerializedMapEntries(mapper.getFactory(), out, statsBytes);
            out.writeNumberField("update_ms", 123L);
            out.writeEndObject();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = mapper.readValue(
                JteUtil.decompress(compressedOut.toByteArray()),
                Map.class);

        assertEquals("test", decoded.get("name"));
        assertEquals(12, ((Number) decoded.get("start")).intValue());
        assertEquals(5, ((Number) decoded.get("wars")).intValue());
        assertEquals(2, ((Number) decoded.get("active_wars")).intValue());
        assertEquals(123, ((Number) decoded.get("update_ms")).intValue());
    }
}