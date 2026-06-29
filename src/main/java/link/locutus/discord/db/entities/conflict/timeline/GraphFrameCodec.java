package link.locutus.discord.db.entities.conflict.timeline;

import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;

public final class GraphFrameCodec {
    private static final int PATCH_MASK_BITS = 30;
    private static final long[] EMPTY_FRAME = new long[0];

    private GraphFrameCodec() {
    }

    public static long[] encodeSparsePatchFrame(
            long timeOffset,
            Map<Byte, Long> metricData,
            Byte2IntOpenHashMap cityIndexById
    ) {
        int estimatedChanges = metricData.size();
        if (estimatedChanges <= 0) {
            return EMPTY_FRAME;
        }

        int[] cityIndexes = new int[estimatedChanges];
        long[] cityValues = new long[estimatedChanges];
        int changeCount = collectChangedCities(metricData, cityIndexById, cityIndexes, cityValues);
        return encodeSparsePatchFrame(timeOffset, cityIndexes, cityValues, changeCount);
    }

    public static long[] encodeSparsePatchFrame(
            long timeOffset,
            int[] cityIndexes,
            long[] cityValues,
            int changeCount
    ) {
        if (changeCount <= 0) {
            return EMPTY_FRAME;
        }

        sortChangedCities(cityIndexes, cityValues, changeCount);

        int highestCityIndex = cityIndexes[changeCount - 1];
        int maskWordCount = highestCityIndex / PATCH_MASK_BITS + 1;
        long[] maskWords = buildMaskWords(cityIndexes, changeCount, maskWordCount);
        int indexedEncodedSize = estimateIndexedPatchFrameSize(timeOffset, cityIndexes, cityValues, changeCount);
        int maskedEncodedSize = estimateMaskedPatchFrameSize(timeOffset, maskWords, cityValues, changeCount);

        if (indexedEncodedSize <= maskedEncodedSize) {
            return encodeIndexedPatchFrame(timeOffset, cityIndexes, cityValues, changeCount);
        }

        return encodeMaskedPatchFrame(timeOffset, maskWords, cityValues, changeCount);
    }

    public static int[] applyFrame(long[] frame, int[] previousState) {
        if (frame.length <= 1) {
            return previousState;
        }

        int[] nextState = ensureCapacity(previousState, requiredStateLength(frame));
        long frameType = frame[1];
        if (frameType < 0L) {
            applyMaskedFrame(frame, nextState);
            return nextState;
        }

        applyIndexedFrame(frame, nextState);
        return nextState;
    }

    public static List<Long> view(long[] frame) {
        if (frame.length == 0) {
            return Collections.emptyList();
        }
        return new LongArrayFrameView(frame);
    }

    private static int[] ensureCapacity(int[] previousState, int requiredLength) {
        if (requiredLength <= previousState.length) {
            return previousState;
        }
        return Arrays.copyOf(previousState, requiredLength);
    }

    private static int requiredStateLength(long[] frame) {
        if (frame.length <= 1) {
            return 0;
        }

        long frameType = frame[1];
        if (frameType < 0L) {
            int maskWordCount = Math.toIntExact(-frameType);
            if (maskWordCount == 0) {
                return 0;
            }
            if (frame.length < 2 + maskWordCount) {
                throw new IllegalArgumentException("Masked frame is truncated");
            }
            long lastMaskWord = frame[1 + maskWordCount];
            if (lastMaskWord == 0L) {
                throw new IllegalArgumentException("Masked frame cannot end with an empty mask word");
            }
            int highestBit = 63 - Long.numberOfLeadingZeros(lastMaskWord);
            return (maskWordCount - 1) * PATCH_MASK_BITS + highestBit + 1;
        }

        if ((frame.length & 1) == 0) {
            throw new IllegalArgumentException("Indexed frame must contain time plus index/value pairs");
        }

        int maxIndex = -1;
        for (int cursor = 1; cursor < frame.length; cursor += 2) {
            int cityIndex = Math.toIntExact(frame[cursor]);
            if (cityIndex > maxIndex) {
                maxIndex = cityIndex;
            }
        }
        return maxIndex + 1;
    }

    private static void applyIndexedFrame(long[] frame, int[] previousState) {
        for (int cursor = 1; cursor < frame.length; cursor += 2) {
            int cityIndex = Math.toIntExact(frame[cursor]);
            previousState[cityIndex] = Math.toIntExact(frame[cursor + 1]);
        }
    }

    private static void applyMaskedFrame(long[] frame, int[] previousState) {
        int maskWordCount = Math.toIntExact(-frame[1]);
        int valuesCursor = 2 + maskWordCount;
        int expectedValueCount = 0;
        for (int wordIndex = 0; wordIndex < maskWordCount; wordIndex++) {
            long maskWord = frame[2 + wordIndex];
            if ((maskWord >>> PATCH_MASK_BITS) != 0L) {
                throw new IllegalArgumentException("Masked frame uses bits outside the supported patch width");
            }
            expectedValueCount += Long.bitCount(maskWord);
        }
        if (valuesCursor + expectedValueCount != frame.length) {
            throw new IllegalArgumentException("Masked frame value count does not match mask bits");
        }

        int appliedValues = 0;

        for (int wordIndex = 0; wordIndex < maskWordCount; wordIndex++) {
            long maskWord = frame[2 + wordIndex];
            while (maskWord != 0L) {
                int bitIndex = Long.numberOfTrailingZeros(maskWord);
                int cityIndex = wordIndex * PATCH_MASK_BITS + bitIndex;
                previousState[cityIndex] = Math.toIntExact(frame[valuesCursor + appliedValues]);
                appliedValues++;
                maskWord &= maskWord - 1L;
            }
        }
    }

    private static long[] buildMaskWords(int[] cityIndexes, int changeCount, int maskWordCount) {
        long[] maskWords = new long[maskWordCount];
        for (int i = 0; i < changeCount; i++) {
            int cityIndex = cityIndexes[i];
            int wordIndex = cityIndex / PATCH_MASK_BITS;
            int bitIndex = cityIndex % PATCH_MASK_BITS;
            maskWords[wordIndex] |= 1L << bitIndex;
        }
        return maskWords;
    }

    private static int collectChangedCities(
            Map<Byte, Long> metricData,
            Byte2IntOpenHashMap cityIndexById,
            int[] cityIndexes,
            long[] cityValues
    ) {
        int changeCount = 0;

        if (metricData instanceof Byte2LongMap byteMetricData) {
            ObjectIterator<Byte2LongMap.Entry> iterator = byteMetricData.byte2LongEntrySet().iterator();
            while (iterator.hasNext()) {
                Byte2LongMap.Entry cityEntry = iterator.next();
                int cityIndex = cityIndexById.get(cityEntry.getByteKey());
                if (cityIndex < 0) {
                    continue;
                }

                cityIndexes[changeCount] = cityIndex;
                cityValues[changeCount] = cityEntry.getLongValue();
                changeCount++;
            }
        } else {
            for (Map.Entry<Byte, Long> cityEntry : metricData.entrySet()) {
                Byte cityId = cityEntry.getKey();
                Long value = cityEntry.getValue();
                if (cityId == null || value == null) {
                    continue;
                }

                int cityIndex = cityIndexById.get(cityId.byteValue());
                if (cityIndex < 0) {
                    continue;
                }

                cityIndexes[changeCount] = cityIndex;
                cityValues[changeCount] = value.longValue();
                changeCount++;
            }
        }

        return changeCount;
    }

    private static void sortChangedCities(int[] cityIndexes, long[] cityValues, int changeCount) {
        for (int i = 1; i < changeCount; i++) {
            int cityIndex = cityIndexes[i];
            long cityValue = cityValues[i];
            int insertIndex = i - 1;
            while (insertIndex >= 0 && cityIndexes[insertIndex] > cityIndex) {
                cityIndexes[insertIndex + 1] = cityIndexes[insertIndex];
                cityValues[insertIndex + 1] = cityValues[insertIndex];
                insertIndex--;
            }
            cityIndexes[insertIndex + 1] = cityIndex;
            cityValues[insertIndex + 1] = cityValue;
        }
    }

    private static long[] encodeIndexedPatchFrame(
            long timeOffset,
            int[] cityIndexes,
            long[] cityValues,
            int changeCount
    ) {
        long[] encoded = new long[1 + changeCount * 2];
        encoded[0] = timeOffset;
        int cursor = 1;
        for (int i = 0; i < changeCount; i++) {
            encoded[cursor++] = cityIndexes[i];
            encoded[cursor++] = cityValues[i];
        }
        return encoded;
    }

    private static int estimateIndexedPatchFrameSize(
            long timeOffset,
            int[] cityIndexes,
            long[] cityValues,
            int changeCount
    ) {
        int size = estimateMsgpackArrayHeaderSize(1 + changeCount * 2);
        size += estimateMsgpackIntegerSize(timeOffset);
        for (int i = 0; i < changeCount; i++) {
            size += estimateMsgpackIntegerSize(cityIndexes[i]);
            size += estimateMsgpackIntegerSize(cityValues[i]);
        }
        return size;
    }

    private static int estimateMaskedPatchFrameSize(
            long timeOffset,
            long[] maskWords,
            long[] cityValues,
            int changeCount
    ) {
        int size = estimateMsgpackArrayHeaderSize(2 + maskWords.length + changeCount);
        size += estimateMsgpackIntegerSize(timeOffset);
        size += estimateMsgpackIntegerSize(-maskWords.length);
        for (long maskWord : maskWords) {
            size += estimateMsgpackIntegerSize(maskWord);
        }
        for (int i = 0; i < changeCount; i++) {
            size += estimateMsgpackIntegerSize(cityValues[i]);
        }
        return size;
    }

    private static int estimateMsgpackArrayHeaderSize(int elementCount) {
        if (elementCount <= 15) {
            return 1;
        }
        if (elementCount <= 0xFFFF) {
            return 3;
        }
        return 5;
    }

    private static int estimateMsgpackIntegerSize(long value) {
        if (value >= 0) {
            if (value <= 0x7FL) {
                return 1;
            }
            if (value <= 0xFFL) {
                return 2;
            }
            if (value <= 0xFFFFL) {
                return 3;
            }
            if (value <= 0xFFFF_FFFFL) {
                return 5;
            }
            return 9;
        }

        if (value >= -32L) {
            return 1;
        }
        if (value >= Byte.MIN_VALUE) {
            return 2;
        }
        if (value >= Short.MIN_VALUE) {
            return 3;
        }
        if (value >= Integer.MIN_VALUE) {
            return 5;
        }
        return 9;
    }

    private static long[] encodeMaskedPatchFrame(
            long timeOffset,
            long[] maskWords,
            long[] cityValues,
            int changeCount
    ) {
        long[] encoded = new long[2 + maskWords.length + changeCount];
        encoded[0] = timeOffset;
        encoded[1] = -maskWords.length;
        int cursor = 2;
        for (long maskWord : maskWords) {
            encoded[cursor++] = maskWord;
        }
        for (int i = 0; i < changeCount; i++) {
            encoded[cursor++] = cityValues[i];
        }
        return encoded;
    }

    private static final class LongArrayFrameView extends AbstractList<Long> implements RandomAccess {
        private final long[] frame;

        private LongArrayFrameView(long[] frame) {
            this.frame = frame;
        }

        @Override
        public Long get(int index) {
            return frame[index];
        }

        @Override
        public int size() {
            return frame.length;
        }
    }
}