package link.locutus.discord.db.conflict;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.web.jooby.CloudItem;
import link.locutus.discord.web.jooby.CloudStorage;
import link.locutus.discord.web.jooby.JteUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VirtualConflictStorageManager {
    private final CloudStorage storage;

    public VirtualConflictStorageManager(CloudStorage storage) {
        this.storage = storage;
    }

    public List<ConflictUtil.VirtualConflictId> listIds(Integer nationIdOrNull) {
        List<ConflictUtil.VirtualConflictId> ids = new ArrayList<>();
        String prefix = nationIdOrNull == null ? "conflicts/n/" : "conflicts/n/" + nationIdOrNull + "/";

        for (CloudItem object : storage.getObjects(prefix)) {
            try {
                ConflictUtil.VirtualConflictId id = ConflictUtil.parseVirtualConflictObjectKey(object.key());
                if (nationIdOrNull == null || id.nationId() == nationIdOrNull) {
                    ids.add(id);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore unrelated S3 keys under the same prefix.
            }
        }

        ids.sort((a, b) -> a.toWebId().compareTo(b.toWebId()));
        return Collections.unmodifiableList(ids);
    }

    public Conflict loadConflict(ConflictUtil.VirtualConflictId id) {
        byte[] zipped;
        try {
            zipped = storage.getObject(id.toObjectKey());
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to read temporary conflict payload from `" + id.toObjectKey() + "`", e);
        }
        if (zipped == null || zipped.length == 0) {
            throw new IllegalArgumentException(
                    "Temporary conflict not found for id `" + id.toWebId() + "`");
        }
        byte[] unpacked = JteUtil.decompress(zipped);
        if (unpacked == null || unpacked.length == 0) {
            throw new IllegalArgumentException(
                    "Temporary conflict payload is empty for id `" + id.toWebId() + "`");
        }

        VirtualConflictPayload data;
        try {
            data = JteUtil.getSerializer().readValue(unpacked, VirtualConflictPayload.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to deserialize temporary conflict from `" + id.toObjectKey() + "`", e);
        }

        long startMs = data.start != null ? data.start : System.currentTimeMillis();
        long endMs = data.end != null ? data.end : -1L;
        long turnStart = link.locutus.discord.util.TimeUtil.getTurn(startMs);
        long turnEnd = endMs < 0 ? Long.MAX_VALUE : link.locutus.discord.util.TimeUtil.getTurn(endMs);

        Conflict conflict = new Conflict(-1, -1,
                data.name == null || data.name.isBlank() ? "Generated conflict" : data.name,
                turnStart, turnEnd, 0, 0, 0, false);
        conflict.setVirtualConflictId(id);

        String col1Name = "Coalition 1";
        String col2Name = "Coalition 2";
        if (data.coalitions != null && data.coalitions.size() >= 2) {
            col1Name = defaultIfBlank(data.coalitions.get(0).name, col1Name);
            col2Name = defaultIfBlank(data.coalitions.get(1).name, col2Name);
        } else {
            throw new IllegalArgumentException(
                    "Temporary conflict payload is missing coalition data for id `" + id.toWebId() + "`");
        }

        // Virtual conflicts are identified by UUID in their web path; they do not have
        // a creating guild id.
        conflict.setLoaded(col1Name, col2Name, 0L, ConflictCategory.GENERATED,
                defaultIfNull(data.wiki), defaultIfNull(data.cb), defaultIfNull(data.status));

        addParticipants(conflict, data.coalitions.get(0).allianceIds, true);
        addParticipants(conflict, data.coalitions.get(1).allianceIds, false);

        return conflict;
    }

    public void deleteConflict(ConflictUtil.VirtualConflictId id) {
        storage.deleteObject(id.toObjectKey());
    }

    private static void addParticipants(Conflict conflict, List<Integer> allianceIds, boolean isPrimary) {
        if (allianceIds == null) {
            return;
        }
        for (Integer allianceId : allianceIds) {
            if (allianceId != null) {
                conflict.addParticipant(allianceId, isPrimary, false, true, null, null);
            }
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String defaultIfNull(String value) {
        return value == null ? "" : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class VirtualConflictPayload {
        public String name;
        public String status;
        public String cb;
        public String wiki;
        public Long start;
        public Long end;
        public List<CoalitionPayload> coalitions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CoalitionPayload {
        public String name;
        @JsonProperty("alliance_ids")
        public List<Integer> allianceIds;
    }
}
