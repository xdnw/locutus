package link.locutus.discord.db.conflict;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.db.entities.conflict.ConflictColumn;
import link.locutus.discord.db.entities.conflict.ConflictMetric;
import link.locutus.discord.db.entities.conflict.DamageStatGroup;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.jooby.JteUtil;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static link.locutus.discord.util.IOUtil.writeMsgpackBytes;

public enum HeaderGroup {
    INDEX_META(List.of(
            "id",
            "name",
            "c1_name",
            "c2_name",
            "start",
            "end",
            "c1",
            "c2",
            "wiki",
            "status",
            "cb",
            "posts",
            "source",
            "category"
    )) {
        @Override
        public Map<String, Object> write(ConflictManager manager, Conflict conflict) {
            Map<String, Object> meta = new Object2ObjectLinkedOpenHashMap<>();

            meta.put("id", conflict.getId());
            meta.put("name", conflict.getName());
            meta.put("c1_name", conflict.getCoalitionName(true));
            meta.put("c2_name", conflict.getCoalitionName(false));
            meta.put("start", TimeUtil.getTimeFromTurn(conflict.getStartTurn()));
            meta.put("end", conflict.getEndTurn() == Long.MAX_VALUE
                    ? -1
                    : TimeUtil.getTimeFromTurn(conflict.getEndTurn()));
            meta.put("c1", new IntArrayList(conflict.getCoalition1()));
            meta.put("c2", new IntArrayList(conflict.getCoalition2()));
            meta.put("wiki", conflict.getWiki());
            meta.put("status", conflict.getStatusDesc());
            meta.put("cb", conflict.getCasusBelli());
            meta.put("posts", conflict.getAnnouncementsList());
            meta.put("source", conflict.getGuildId());
            meta.put("category", conflict.getCategory().name());

            return meta;
        }
    },

    INDEX_STATS(List.of(
            "wars",
            "active_wars",
            "c1_dealt",
            "c2_dealt"
    )) {
        @Override
        public Map<String, Object> write(ConflictManager manager, Conflict conflict) {
            Map<String, Object> stats = new Object2ObjectLinkedOpenHashMap<>();

            stats.put("wars", conflict.getTotalWars());
            stats.put("active_wars", conflict.getActiveWars());
            stats.put("c1_dealt", (long) conflict.getDamageConverted(true));
            stats.put("c2_dealt", (long) conflict.getDamageConverted(false));

            return stats;
        }
    },

    PAGE_META(List.of(
            "name",
            "start",
            "end",
            "wiki",
            "status",
            "cb",
            "posts",
            "coalitions"
    )) {
        @Override
        public Map<String, Object> write(ConflictManager manager, Conflict conflict) {
            CoalitionSide coalition1 = conflict.getSide1();
            CoalitionSide coalition2 = conflict.getSide2();

            Map<String, Object> meta = new Object2ObjectLinkedOpenHashMap<>();
            meta.put("name", conflict.getName());
            meta.put("start", TimeUtil.getTimeFromTurn(conflict.getStartTurn()));
            meta.put("end", conflict.getEndTurn() == Long.MAX_VALUE
                    ? -1
                    : TimeUtil.getTimeFromTurn(conflict.getEndTurn()));
            meta.put("wiki", conflict.getWiki());
            meta.put("status", conflict.getStatusDesc());
            meta.put("cb", conflict.getCasusBelli());
            meta.put("posts", conflict.getAnnouncementsList());

            List<Object> coalitions = new ObjectArrayList<>();
            coalitions.add(coalition1.toMetaMap(manager));
            coalitions.add(coalition2.toMetaMap(manager));
            meta.put("coalitions", coalitions);

            return meta;
        }
    },

    PAGE_STATS(List.of(
            "damage_header",
            "header_desc",
            "header_group",
            "header_type",
            "war_web",
            "coalitions"
    )) {
        @Override
        public Map<String, Object> write(ConflictManager manager, Conflict conflict) {
            CoalitionSide coalition1 = conflict.getSide1();
            CoalitionSide coalition2 = conflict.getSide2();

            Map<String, Object> stats = new Object2ObjectLinkedOpenHashMap<>();

            Map<ConflictColumn, Function<DamageStatGroup, Object>> damageHeader =
                    DamageStatGroup.createHeader();

            // header names
            stats.put(
                    "damage_header",
                    new ObjectArrayList<>(
                            damageHeader.keySet()
                                    .stream()
                                    .map(ConflictColumn::getName)
                                    .toList()
                    )
            );

            // header descriptions
            stats.put(
                    "header_desc",
                    new ObjectArrayList<>(
                            damageHeader.keySet()
                                    .stream()
                                    .map(ConflictColumn::getDescription)
                                    .toList()
                    )
            );

            // header group (type name)
            stats.put(
                    "header_group",
                    new ObjectArrayList<>(
                            damageHeader.keySet()
                                    .stream()
                                    .map(f -> f.getType().name())
                                    .toList()
                    )
            );

            // header type (1 if count, 0 otherwise)
            stats.put(
                    "header_type",
                    new ObjectArrayList<>(
                            damageHeader.keySet()
                                    .stream()
                                    .map(f -> f.isCount() ? 1 : 0)
                                    .toList()
                    )
            );

            Map<Integer, Map<Integer, DamageStatGroup>> warsVsAlliance =
                    conflict.getData().getSides().getDataWithWars();
            if (warsVsAlliance == null) {
                warsVsAlliance = new Int2ObjectOpenHashMap<>();
            }

            stats.put(
                    "war_web",
                    conflict.warsVsAllianceJson(coalition1, coalition2, warsVsAlliance)
            );

            List<Object> coalitions = new ObjectArrayList<>();
            coalitions.add(coalition1.get().toDataMap());
            coalitions.add(coalition2.get().toDataMap());
            stats.put("coalitions", coalitions);

            return stats;
        }
    },

    GRAPH_META(List.of(
            "name",
            "start",
            "end",
            "coalitions"
    )) {
        @Override
        public Map<String, Object> write(ConflictManager manager, Conflict conflict) {
            CoalitionSide coalition1 = conflict.getSide1();
            CoalitionSide coalition2 = conflict.getSide2();

            Map<String, Object> graphMeta = new Object2ObjectLinkedOpenHashMap<>();
            graphMeta.put("name", conflict.getName());
            graphMeta.put("start", TimeUtil.getTimeFromTurn(conflict.getStartTurn()));
            graphMeta.put("end", conflict.getEndTurn() == Long.MAX_VALUE
                    ? -1
                    : TimeUtil.getTimeFromTurn(conflict.getEndTurn()));

            List<Map<String, Object>> coalitions = new ObjectArrayList<>();
            coalitions.add(coalition1.toMetaMap(manager));
            coalitions.add(coalition2.toMetaMap(manager));
            graphMeta.put("coalitions", coalitions);

            return graphMeta;
        }
    },

    GRAPH_DATA(List.of(
            "metric_names",
            "metrics_turn",
            "metrics_day",
            "coalitions"
    )) {
        @Override
        public Map<String, Object> write(ConflictManager manager, Conflict conflict) {
            Map<String, Object> graphData = new Object2ObjectLinkedOpenHashMap<>();

            List<String> metricNames = new ObjectArrayList<>();

            List<Integer> metricsDay = new IntArrayList();
            List<Integer> metricsTurn = new IntArrayList();

            for (ConflictMetric metric : ConflictMetric.values) {
                (metric.isDay() ? metricsDay : metricsTurn).add(metricNames.size());
                metricNames.add(metric.name().toLowerCase(Locale.ROOT));
            }

            Map<ConflictColumn, Function<DamageStatGroup, Object>> damageHeaders =
                    DamageStatGroup.createRanking();
            List<ConflictColumn> columns = new ObjectArrayList<>(damageHeaders.keySet());
            List<Function<DamageStatGroup, Object>> valueFuncs =
                    columns.stream().map(damageHeaders::get).toList();

            int columnMetricOffset = metricNames.size();

            for (ConflictColumn column : columns) {
                // defender metric
                metricsDay.add(metricNames.size());
                String defPrefix = column.isCount() ? "def:" : "loss:";
                metricNames.add(defPrefix + column.getName());

                // attacker metric
                metricsDay.add(metricNames.size());
                String attPrefix = column.isCount() ? "off:" : "dealt:";
                metricNames.add(attPrefix + column.getName());
            }

            graphData.put("metric_names", metricNames);
            graphData.put("metrics_turn", metricsTurn);
            graphData.put("metrics_day", metricsDay);

            // Build coalition graph maps
            CoalitionSide coalition1 = conflict.getSide1();
            CoalitionSide coalition2 = conflict.getSide2();

            List<Map<String, Object>> coalitions = new ObjectArrayList<>();
            coalitions.add(coalition1.get().toGraphMap(metricsTurn, metricsDay, valueFuncs, columnMetricOffset));
            coalitions.add(coalition2.get().toGraphMap(metricsTurn, metricsDay, valueFuncs, columnMetricOffset));
            graphData.put("coalitions", coalitions);

            return graphData;
        }
    };

    private final List<String> headers;
    private final long hash;

    HeaderGroup(List<String> headers) {
        this.headers = headers;                 // created once per enum constant
        this.hash = StringMan.hash(headers);    // computed once per enum constant
    }

    /** Constant headers for this group (in stable order). */
    public final List<String> getHeaders() {
        return headers;
    }

    /** Cached hash based on {@link #getHeaders()}. */
    public final long getHash() {
        return hash;
    }

    public static final HeaderGroup[] values = values();

    public abstract Map<String, Object> write(ConflictManager manager, Conflict conflict);

    public static byte[] getBytesZip(
            ConflictManager manager,
            Conflict conflict,
            Map<HeaderGroup, Boolean> forceUpdate,
            long now
    ) {
        ObjectMapper mapper = JteUtil.getSerializer();

        int totalFields = 1; // update_ms
        for (HeaderGroup group : forceUpdate.keySet()) {
            totalFields += group.getHeaders().size();
        }

        try {
            ByteArrayOutputStream compressedOut = new ByteArrayOutputStream(16 * 1024);
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(compressedOut, 1_048_576) {
                {
                    def.setLevel(Deflater.BEST_COMPRESSION);
                }
            }; MessagePacker packer = MessagePack.newDefaultPacker(gzipOut)) {
                packer.packMapHeader(totalFields);

                for (Map.Entry<HeaderGroup, Boolean> entry : forceUpdate.entrySet()) {
                    HeaderGroup group = entry.getKey();
                    boolean force = entry.getValue();
                    byte[] groupBytes = group.getGroupBytes(manager, conflict.getId(), now, force, conflict, mapper);
                    appendMapEntries(packer, groupBytes, group.getHeaders().size(), group);
                }

                packer.packString("update_ms");
                packer.packLong(now);
            }

            return compressedOut.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] getGroupBytes(
            ConflictManager manager,
            int conflictId,
            long now,
            boolean forceUpdate,
            Conflict conflict,
            ObjectMapper mapper
    ) throws IOException {
        if (!forceUpdate && conflictId > 0) {
            Map<HeaderGroup, Map.Entry<Long, byte[]>> cachedMap =
                    manager.loadConflictRowCache(conflictId, Set.of(this));
            Map.Entry<Long, byte[]> cachedEntry = cachedMap.get(this);
            if (cachedEntry != null) {
                return cachedEntry.getValue();
            }
        }

        Map<String, Object> freshData = this.write(manager, conflict);
        byte[] freshBytes = writeMsgpackBytes(mapper, freshData);

        if (conflictId > 0) {
            manager.saveConflictRowCache(conflictId, freshBytes, this, now);
        }
        return freshBytes;
    }

    /**
     * Strip the leading msgpack map header from {@code mapBytes} and stream the
     * remaining {@code (key,value)} pairs through {@code packer}. The outer
     * container's {@code packMapHeader} already reserved {@code expectedFields}
     * slots for these entries.
     */
    static void appendMapEntries(MessagePacker packer, byte[] mapBytes, int expectedFields, HeaderGroup group)
            throws IOException {
        if (mapBytes == null || mapBytes.length == 0) {
            throw new IOException("Group " + group + " produced empty msgpack bytes");
        }
        int b = mapBytes[0] & 0xFF;
        int count;
        int offset;
        if ((b & 0xF0) == 0x80) {
            count = b & 0x0F;
            offset = 1;
        } else if (b == 0xDE) {
            if (mapBytes.length < 3) {
                throw new IOException("Group " + group + " has truncated map16 header");
            }
            count = ((mapBytes[1] & 0xFF) << 8) | (mapBytes[2] & 0xFF);
            offset = 3;
        } else if (b == 0xDF) {
            if (mapBytes.length < 5) {
                throw new IOException("Group " + group + " has truncated map32 header");
            }
            count = ((mapBytes[1] & 0xFF) << 24) | ((mapBytes[2] & 0xFF) << 16)
                    | ((mapBytes[3] & 0xFF) << 8) | (mapBytes[4] & 0xFF);
            offset = 5;
        } else {
            throw new IOException("Group " + group + " bytes do not start with a msgpack map header (first byte=0x"
                    + Integer.toHexString(b) + ")");
        }
        if (count != expectedFields) {
            throw new IOException("Group " + group + " field-count mismatch: encoded=" + count
                    + " expected=" + expectedFields);
        }
        packer.writePayload(mapBytes, offset, mapBytes.length - offset);
    }
}
