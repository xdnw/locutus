package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.util.io.BitBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TransactionNote {
    private static final TransactionNote EMPTY = new TransactionNote(Collections.emptyMap());
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final Map<DepositType, Object> parsed;
    private final int hashCode;

    private TransactionNote(Map<DepositType, Object> parsed) {
        this.parsed = parsed;
        this.hashCode = parsed.hashCode();
    }

    public static TransactionNote empty() {
        return EMPTY;
    }

    public static TransactionNote of(DepositType type) {
        if (type == null) {
            return EMPTY;
        }
        return of(type.toParsedNote());
    }

    public static TransactionNote of(Map<DepositType, Object> parsed) {
        if (parsed == null || parsed.isEmpty()) {
            return EMPTY;
        }
        return new TransactionNote(copyParsed(parsed));
    }

    @Deprecated
    public static TransactionNote parseLegacy(String note, long date) {
        if (note == null || note.isEmpty()) {
            return EMPTY;
        }
        Map<DepositType, Object> result = new EnumMap<>(DepositType.class);
        int i = 0;
        int length = note.length();
        while (i < length) {
            while (i < length && note.charAt(i) != '#') {
                i++;
            }
            if (i >= length) {
                break;
            }
            int hashtagStart = i++;
            while (i < length && note.charAt(i) != '#') {
                i++;
            }
            processHashtagSegment(note, hashtagStart, i, result, false, date);
        }
        return of(result);
    }

    public static TransactionNote parseStructuredQuery(String note) {
        if (note == null || note.isBlank()) {
            return EMPTY;
        }
        String normalized = normalizeLegacyQuery(note);
        if (!isPureStructuredQuery(normalized)) {
            return EMPTY;
        }
        Map<DepositType, Object> result = new EnumMap<>(DepositType.class);
        int i = 0;
        int length = normalized.length();
        while (i < length) {
            while (i < length && normalized.charAt(i) != '#') {
                i++;
            }
            if (i >= length) {
                break;
            }
            int hashtagStart = i++;
            while (i < length && normalized.charAt(i) != '#') {
                i++;
            }
            processHashtagSegment(normalized, hashtagStart, i, result, true, 0L);
        }
        return of(result);
    }

    public static Builder builder() {
        return new Builder(Collections.emptyMap());
    }

    public static Builder builder(Map<DepositType, Object> parsed) {
        return new Builder(parsed);
    }

    public static Builder builder(TransactionNote note) {
        return new Builder(note == null ? Collections.emptyMap() : note.parsed);
    }

    public Map<DepositType, Object> asMap() {
        return parsed;
    }

    public boolean isEmpty() {
        return parsed.isEmpty();
    }

    public boolean hasTag(DepositType type) {
        return parsed.containsKey(type);
    }

    public boolean containsAll(TransactionNote other) {
        if (other == null || other.parsed.isEmpty()) {
            return true;
        }
        for (Map.Entry<DepositType, Object> entry : other.parsed.entrySet()) {
            DepositType type = entry.getKey();
            if (!parsed.containsKey(type)) {
                return false;
            }
            if (!legacyValueEquals(type, parsed.get(type), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    public Object get(DepositType type) {
        return parsed.get(type);
    }

    @Deprecated
    public String getLegacyValue(DepositType type) {
        Object value = parsed.get(type);
        return value == null ? null : formatLegacyValue(type, value);
    }

    public Builder toBuilder() {
        return new Builder(parsed);
    }

    public TransactionNote without(DepositType... types) {
        if (parsed.isEmpty() || types == null || types.length == 0) {
            return this;
        }
        Builder builder = new Builder(parsed);
        builder.removeAll(types);
        return builder.build();
    }

    public DepositType primaryType() {
        if (parsed.isEmpty()) {
            return DepositType.DEPOSIT;
        }
        for (DepositType type : orderedTypes(parsed)) {
            if (type.getParent() == null && !type.isReserved()) {
                return type;
            }
        }
        if (parsed.containsKey(DepositType.DEPOSIT)) {
            return DepositType.DEPOSIT;
        }
        if (parsed.containsKey(DepositType.TAX)) {
            return DepositType.TAX;
        }
        if (parsed.containsKey(DepositType.LOAN)) {
            return DepositType.LOAN;
        }
        if (parsed.containsKey(DepositType.GRANT)) {
            return DepositType.GRANT;
        }
        if (parsed.containsKey(DepositType.IGNORE)) {
            return DepositType.IGNORE;
        }
        return DepositType.DEPOSIT;
    }

    public byte[] toBytes(BitBuffer buffer) {
        if (parsed.isEmpty()) {
            return null;
        }
        buffer.reset();
        DepositType.serialize(parsed, buffer);
        return buffer.getWrittenBytes();
    }

    public String toLegacyString() {
        if (parsed.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (DepositType type : orderedTypes(parsed)) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append('#').append(type.name().toLowerCase(Locale.ROOT));
            Object value = parsed.get(type);
            if (value != null) {
                builder.append('=').append(formatLegacyValue(type, value));
            }
        }
        return builder.toString();
    }

    public String toDisplayString() {
        return toLegacyString();
    }

    public Map<String, Object> toDataMap() {
        if (parsed.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        for (DepositType type : orderedTypes(parsed)) {
            data.put(type.name().toLowerCase(Locale.ROOT), formatDataValue(parsed.get(type)));
        }
        return Collections.unmodifiableMap(data);
    }

    public long stableHash() {
        if (parsed.isEmpty()) {
            return 0L;
        }
        long hash = FNV_OFFSET_BASIS;
        boolean first = true;
        for (DepositType type : orderedTypes(parsed)) {
            if (!first) {
                hash = updateStableHash(hash, ' ');
            }
            first = false;
            hash = updateStableHash(hash, '#');
            hash = updateStableHash(hash, type.name().toLowerCase(Locale.ROOT));
            Object value = parsed.get(type);
            if (value != null) {
                hash = updateStableHash(hash, '=');
                hash = updateStableHash(hash, formatLegacyValue(type, value));
            }
        }
        return hash;
    }

    public long stableHashWithout(DepositType... types) {
        return without(types).stableHash();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TransactionNote other)) {
            return false;
        }
        return parsed.equals(other.parsed);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return Objects.toString(toDisplayString(), "{}");
    }

    private static Map<DepositType, Object> copyParsed(Map<DepositType, Object> parsed) {
        Map<DepositType, Object> copy = new LinkedHashMap<>(parsed.size());
        for (Map.Entry<DepositType, Object> entry : parsed.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyValue(DepositType type, Object value) {
        Object copy = copyValue(value);
        return type == null ? copy : type.normalizeValue(copy);
    }

    private static Object copyValue(Object value) {
        if (value instanceof Set<?> set) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(set));
        }
        return value;
    }

    private static List<DepositType> orderedTypes(Map<DepositType, Object> data) {
        List<DepositType> ordered = new ArrayList<>(data.keySet());
        ordered.sort(java.util.Comparator.comparingInt(Enum::ordinal));
        return ordered;
    }

    private static Object formatDataValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof Set<?> set) {
            List<String> values = new ArrayList<>(set.size());
            for (Object entry : set) {
                Object formatted = formatDataValue(entry);
                values.add(formatted == null ? null : formatted.toString());
            }
            values.sort(String::compareTo);
            return Collections.unmodifiableList(values);
        }
        return value;
    }

    private static long updateStableHash(long hash, CharSequence sequence) {
        for (int i = 0; i < sequence.length(); i++) {
            hash = updateStableHash(hash, sequence.charAt(i));
        }
        return hash;
    }

    private static long updateStableHash(long hash, char value) {
        hash ^= value;
        hash *= FNV_PRIME;
        return hash;
    }

    private static boolean legacyValueEquals(DepositType type, Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(formatLegacyValue(type, left), formatLegacyValue(type, right));
    }

    private static String normalizeLegacyQuery(String note) {
        String trimmed = note.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        StringBuilder normalized = new StringBuilder(trimmed.length());
        boolean previousWhitespace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!previousWhitespace) {
                    normalized.append(' ');
                    previousWhitespace = true;
                }
            } else {
                normalized.append(c);
                previousWhitespace = false;
            }
        }
        return normalized.toString();
    }

    private static boolean isPureStructuredQuery(String normalized) {
        if (normalized.isEmpty() || normalized.charAt(0) != '#') {
            return false;
        }
        int tokenStart = 0;
        while (tokenStart < normalized.length()) {
            if (normalized.charAt(tokenStart) != '#') {
                return false;
            }
            int nextSpace = normalized.indexOf(' ', tokenStart);
            if (nextSpace == -1) {
                break;
            }
            tokenStart = nextSpace + 1;
        }
        return true;
    }

    @Deprecated
    private static String formatLegacyValue(DepositType type, Object value) {
        if ((type == DepositType.EXPIRE || type == DepositType.DECAY) && value instanceof Number n) {
            return "timestamp:" + n.longValue();
        }
        if (value instanceof Number n) {
            if (value instanceof Float || value instanceof Double) {
                double doubleValue = n.doubleValue();
                if (doubleValue == Math.rint(doubleValue)) {
                    return Long.toString((long) doubleValue);
                }
                return Double.toString(doubleValue);
            }
            return Long.toString(n.longValue());
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof Set<?> set) {
            List<String> values = new ArrayList<>(set.size());
            for (Object entry : set) {
                if (entry instanceof Enum<?> e) {
                    values.add(e.name());
                } else {
                    values.add(entry.toString());
                }
            }
            values.sort(String::compareTo);
            return String.join(",", values);
        }
        return value.toString();
    }

    private static void processHashtagSegment(String note, int start, int end, Map<DepositType, Object> result,
            boolean structuredQuery, long date) {
        if (end <= start + 1) {
            return;
        }
        int separatorIdx = -1;
        for (int i = start + 1; i < end; i++) {
            char c = note.charAt(i);
            if (c == '=' || c == ' ') {
                separatorIdx = i;
                break;
            }
        }

        String tag;
        String value = null;
        if (separatorIdx != -1) {
            tag = note.substring(start, separatorIdx).toLowerCase(Locale.ROOT);
            if (separatorIdx < end - 1) {
                String remainder = note.substring(separatorIdx + 1, end).trim();
                if (!remainder.isEmpty()) {
                    int spaceIdx = remainder.indexOf(' ');
                    value = spaceIdx != -1 ? remainder.substring(0, spaceIdx).trim() : remainder;
                }
            }
        } else {
            tag = note.substring(start, end).toLowerCase(Locale.ROOT);
        }

        DepositType type = DepositType.parse(tag);
        if (type != null) {
            result.put(type, structuredQuery ? resolveStructuredQueryValue(type, value) : type.resolve(value, date));
        }
    }

    private static Object resolveStructuredQueryValue(DepositType type, String value) {
        if ((type == DepositType.EXPIRE || type == DepositType.DECAY) && value != null && !value.isEmpty()) {
            if (value.regionMatches(true, 0, "timestamp:", 0, "timestamp:".length())) {
                String timestampValue = value.substring("timestamp:".length()).trim();
                if (timestampValue.isEmpty()) {
                    return null;
                }
                try {
                    return Long.parseLong(timestampValue);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return type.resolve(value, 0L);
    }

    public static final class Builder {
        private final LinkedHashMap<DepositType, Object> parsed;

        private Builder(Map<DepositType, Object> seed) {
            this.parsed = new LinkedHashMap<>();
            if (seed != null && !seed.isEmpty()) {
                for (Map.Entry<DepositType, Object> entry : seed.entrySet()) {
                    this.parsed.put(entry.getKey(), copyValue(entry.getKey(), entry.getValue()));
                }
            }
        }

        public Builder put(DepositType type) {
            return put(type, null);
        }

        public Builder put(DepositType type, Object value) {
            if (type != null) {
                parsed.put(type, copyValue(type, value));
            }
            return this;
        }

        public Builder putAll(Map<DepositType, Object> values) {
            if (values != null) {
                for (Map.Entry<DepositType, Object> entry : values.entrySet()) {
                    put(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        public Builder merge(TransactionNote note) {
            if (note != null) {
                putAll(note.parsed);
            }
            return this;
        }

        public Builder remove(DepositType type) {
            parsed.remove(type);
            return this;
        }

        public Builder removeAll(DepositType... types) {
            if (types != null) {
                Arrays.stream(types).forEach(parsed::remove);
            }
            return this;
        }

        public TransactionNote build() {
            return TransactionNote.of(parsed);
        }
    }
}
