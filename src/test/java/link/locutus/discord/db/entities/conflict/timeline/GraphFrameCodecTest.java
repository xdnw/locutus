package link.locutus.discord.db.entities.conflict.timeline;

import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphFrameCodecTest {
    @Test
    void applyIndexedFrameExpandsStateAndPreservesSparseEncoding() {
        Byte2IntOpenHashMap cityIndexById = new Byte2IntOpenHashMap();
        cityIndexById.defaultReturnValue(-1);
        for (byte city = 1; city <= 30; city++) {
            cityIndexById.put(city, city - 1);
        }

        Byte2LongOpenHashMap metricData = new Byte2LongOpenHashMap();
        metricData.put((byte) 28, 5L);
        metricData.put((byte) 29, 7L);
        metricData.put((byte) 30, 9L);

        long[] frame = GraphFrameCodec.encodeSparsePatchFrame(0L, metricData, cityIndexById);

        assertArrayEquals(new long[] { 0L, 27L, 5L, 28L, 7L, 29L, 9L }, frame);
        assertEquals(Arrays.asList(0L, 27L, 5L, 28L, 7L, 29L, 9L), GraphFrameCodec.view(frame));

        int[] previousState = new int[] { 3 };
        previousState = GraphFrameCodec.applyFrame(frame, previousState);

        assertEquals(30, previousState.length);
        assertEquals(5, previousState[27]);
        assertEquals(7, previousState[28]);
        assertEquals(9, previousState[29]);
        assertEquals(3, previousState[0]);
    }

    @Test
    void applyMaskedFrameUpdatesOnlyBitsPresentInMask() {
        Byte2IntOpenHashMap cityIndexById = new Byte2IntOpenHashMap();
        cityIndexById.defaultReturnValue(-1);
        cityIndexById.put((byte) 1, 0);
        cityIndexById.put((byte) 2, 1);
        cityIndexById.put((byte) 3, 2);
        cityIndexById.put((byte) 4, 3);

        Byte2LongOpenHashMap metricData = new Byte2LongOpenHashMap();
        metricData.put((byte) 1, 5L);
        metricData.put((byte) 3, 7L);
        metricData.put((byte) 4, 9L);

        long[] frame = GraphFrameCodec.encodeSparsePatchFrame(0L, metricData, cityIndexById);

        assertArrayEquals(new long[] { 0L, -1L, 13L, 5L, 7L, 9L }, frame);

        int[] previousState = new int[] { 1, 2, 3, 4 };
        previousState = GraphFrameCodec.applyFrame(frame, previousState);

        assertArrayEquals(new int[] { 5, 2, 7, 9 }, previousState);
    }

    @Test
    void applyFrameAllowsZeroValuePatches() {
        int[] previousState = new int[] { 8, 6, 4 };

        int[] nextState = GraphFrameCodec.applyFrame(new long[] { 4L, 1L, 0L }, previousState);

        assertArrayEquals(new int[] { 8, 0, 4 }, nextState);
    }
}