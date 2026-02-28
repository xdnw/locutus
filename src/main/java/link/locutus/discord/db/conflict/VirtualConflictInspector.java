package link.locutus.discord.db.conflict;

import com.fasterxml.jackson.core.JsonProcessingException;
import link.locutus.discord.web.jooby.CloudItem;
import link.locutus.discord.web.jooby.CloudStorage;
import link.locutus.discord.web.jooby.JteUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class VirtualConflictInspector {
    private static final Set<String> EXPECTED_TOP_LEVEL_KEYS = Set.of(
            "name", "status", "cb", "wiki", "start", "end", "coalitions"
    );

    public enum Classification {
        OBJECT_ROOT,
        SCALAR_ROOT,
        PARSE_ERROR,
        DECOMPRESS_ERROR,
        MISSING
    }

    public record InspectionResult(
            ConflictUtil.VirtualConflictId id,
            String objectKey,
            Classification classification,
            Integer compressedSize,
            Integer decompressedSize,
            String firstByteHex,
            String first16BytesHex,
            String rootType,
            String rootPreview,
            String structurePreview,
            Set<String> presentTopLevelKeys,
            Set<String> missingTopLevelKeys,
            Set<String> extraTopLevelKeys,
            Long lastModifiedEpochMs,
            String error
    ) {
        public boolean isConfirmedUnrecoverable() {
            return classification == Classification.SCALAR_ROOT
                    || classification == Classification.PARSE_ERROR
                    || classification == Classification.DECOMPRESS_ERROR;
        }

        public String lastModifiedIsoOrUnknown() {
            if (lastModifiedEpochMs == null || lastModifiedEpochMs <= 0) {
                return "unknown";
            }
            return Instant.ofEpochMilli(lastModifiedEpochMs).toString();
        }
    }

    public record PurgeOutcome(int candidates, int deleted, List<String> skipped) {
    }

    public List<InspectionResult> inspect(CloudStorage storage, Collection<ConflictUtil.VirtualConflictId> ids) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(ids, "ids");

        List<ConflictUtil.VirtualConflictId> sortedIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(ConflictUtil.VirtualConflictId::toWebId))
                .toList();

        Map<String, CloudItem> metadataByKey = indexMetadata(storage, sortedIds);
        List<InspectionResult> results = new ArrayList<>(sortedIds.size());
        for (ConflictUtil.VirtualConflictId id : sortedIds) {
            results.add(inspectOne(storage, id, metadataByKey.get(id.toObjectKey())));
        }
        return results;
    }

    private InspectionResult inspectOne(CloudStorage storage,
                                        ConflictUtil.VirtualConflictId id,
                                        CloudItem metadata) {
        String objectKey = id.toObjectKey();
        Long lastModified = metadata == null ? null : metadata.lastModified();

        byte[] compressed;
        try {
            compressed = storage.getObject(objectKey);
        } catch (IOException | RuntimeException e) {
            return new InspectionResult(id, objectKey, Classification.MISSING,
                    null, null, null, null,
                    null, null,
                    null,
                    Set.of(),
                    EXPECTED_TOP_LEVEL_KEYS,
                    Set.of(),
                    lastModified,
                    sanitizeError(e));
        }

        if (compressed == null || compressed.length == 0) {
            return new InspectionResult(id, objectKey, Classification.MISSING,
                    compressed == null ? null : compressed.length,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Set.of(),
                    EXPECTED_TOP_LEVEL_KEYS,
                    Set.of(),
                    lastModified,
                    "Object missing or empty");
        }

        byte[] decompressed;
        try {
            decompressed = JteUtil.decompress(compressed);
        } catch (RuntimeException e) {
            return new InspectionResult(id, objectKey, Classification.DECOMPRESS_ERROR,
                    compressed.length,
                    null,
                    firstByteHex(compressed),
                    firstBytesHex(compressed, 16),
                    null,
                    null,
                    null,
                    Set.of(),
                    EXPECTED_TOP_LEVEL_KEYS,
                    Set.of(),
                    lastModified,
                    sanitizeError(e));
        }

        Object root;
        try {
            root = JteUtil.getSerializer().readValue(decompressed, Object.class);
        } catch (IOException e) {
            return new InspectionResult(id, objectKey, Classification.PARSE_ERROR,
                    compressed.length,
                    decompressed.length,
                    firstByteHex(decompressed),
                    firstBytesHex(decompressed, 16),
                    null,
                    null,
                        null,
                        Set.of(),
                        EXPECTED_TOP_LEVEL_KEYS,
                        Set.of(),
                    lastModified,
                    sanitizeError(e));
        }

        Classification classification = root instanceof Map<?, ?>
                ? Classification.OBJECT_ROOT
                : Classification.SCALAR_ROOT;

                Set<String> presentKeys = extractTopLevelKeys(root);
                Set<String> missingKeys = subtract(EXPECTED_TOP_LEVEL_KEYS, presentKeys);
                Set<String> extraKeys = subtract(presentKeys, EXPECTED_TOP_LEVEL_KEYS);

        return new InspectionResult(id, objectKey, classification,
                compressed.length,
                decompressed.length,
                firstByteHex(decompressed),
                firstBytesHex(decompressed, 16),
                rootType(root),
                previewRoot(root),
                    structurePreview(root, 0),
                    presentKeys,
                    missingKeys,
                    extraKeys,
                lastModified,
                null);
    }

    private Map<String, CloudItem> indexMetadata(CloudStorage storage, Collection<ConflictUtil.VirtualConflictId> ids) {
        Map<String, CloudItem> byKey = new HashMap<>();
        Set<Integer> nationIds = new HashSet<>();
        for (ConflictUtil.VirtualConflictId id : ids) {
            nationIds.add(id.nationId());
        }

        for (int nationId : nationIds) {
            String prefix = "conflicts/n/" + nationId + "/";
            List<CloudItem> objects;
            try {
                objects = storage.getObjects(prefix);
            } catch (RuntimeException e) {
                continue;
            }

            for (CloudItem item : objects) {
                if (item != null && item.key() != null) {
                    byKey.put(item.key(), item);
                }
            }
        }

        return byKey;
    }

    private static String rootType(Object root) {
        return root == null ? "null" : root.getClass().getSimpleName();
    }

    private static String previewRoot(Object root) {
        if (root == null) {
            return "null";
        }
        if (root instanceof Map<?, ?> map) {
            List<String> keys = map.keySet().stream()
                    .limit(5)
                    .map(String::valueOf)
                    .toList();
            return truncate("map(size=" + map.size() + ", keys=" + keys + ")", 240);
        }
        if (root instanceof Collection<?> collection) {
            Object first = collection.stream().findFirst().orElse(null);
            return truncate("collection(size=" + collection.size() + ", first=" + first + ")", 240);
        }
        if (root instanceof CharSequence || root instanceof Number || root instanceof Boolean) {
            return truncate(String.valueOf(root), 240);
        }

        try {
            return truncate(JteUtil.getSerializer().writeValueAsString(root), 240);
        } catch (JsonProcessingException ignored) {
            return truncate(String.valueOf(root), 240);
        }
    }

    private static String structurePreview(Object root, int depth) {
        if (root == null) {
            return "null";
        }
        if (depth >= 3) {
            return rootType(root);
        }

        if (root instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder();
            out.append("object{");
            int shown = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (shown >= 8) {
                    out.append(", ...");
                    break;
                }
                if (shown > 0) {
                    out.append(", ");
                }
                out.append(String.valueOf(entry.getKey()))
                        .append(":")
                        .append(structurePreview(entry.getValue(), depth + 1));
                shown++;
            }
            out.append("}");
            return truncate(out.toString(), 500);
        }

        if (root instanceof Collection<?> collection) {
            Object first = collection.stream().findFirst().orElse(null);
            return truncate("array(size=" + collection.size() + ", item=" + structurePreview(first, depth + 1) + ")", 500);
        }

        return rootType(root);
    }

    private static Set<String> extractTopLevelKeys(Object root) {
        if (!(root instanceof Map<?, ?> map)) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (Object key : map.keySet()) {
            keys.add(String.valueOf(key));
        }
        return Set.copyOf(keys);
    }

    private static Set<String> subtract(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return Set.copyOf(result);
    }

    private static String firstByteHex(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        return String.format(Locale.ROOT, "0x%02X", data[0]);
    }

    private static String firstBytesHex(byte[] data, int maxLen) {
        if (data == null || data.length == 0 || maxLen <= 0) {
            return null;
        }
        int length = Math.min(data.length, maxLen);
        StringBuilder out = new StringBuilder(length * 3);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(String.format(Locale.ROOT, "%02X", data[i]));
        }
        return out.toString();
    }

    private static String sanitizeError(Throwable error) {
        if (error == null) {
            return null;
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return truncate(message.replace('\n', ' ').replace('\r', ' '), 300);
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen - 3) + "...";
    }
}