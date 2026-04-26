package link.locutus.discord.db.conflict;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
        JsonFactory factory = mapper.getFactory();

        try {
            ByteArrayOutputStream compressedOut = new ByteArrayOutputStream(16 * 1024);
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(compressedOut, 1_048_576) {
                {
                    def.setLevel(Deflater.BEST_COMPRESSION);
                }
            }; JsonGenerator out = factory.createGenerator(gzipOut)) {
                out.writeStartObject();
                for (Map.Entry<HeaderGroup, Boolean> entry : forceUpdate.entrySet()) {
                    HeaderGroup group = entry.getKey();
                    boolean force = entry.getValue();
                    group.writeGroupEntries(manager, conflict.getId(), now, force, conflict, mapper, factory, out);
                }
                out.writeNumberField("update_ms", now);
                out.writeEndObject();
            }

            byte[] compressed = compressedOut.toByteArray();
            System.out.println("Generated conflict " + conflict.getId() +
                    " compressed=" + compressed.length + " (streamed msgpack)");
            return compressed;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeGroupEntries(
            ConflictManager manager,
            int conflictId,
            long now,
            boolean forceUpdate,
            Conflict conflict,
            ObjectMapper mapper,
            JsonFactory factory,
            JsonGenerator out
    ) throws IOException {
        if (!forceUpdate && conflictId > 0) {
            Map<HeaderGroup, Map.Entry<Long, byte[]>> cachedMap =
                    manager.loadConflictRowCache(conflictId, Set.of(this));
            Map.Entry<Long, byte[]> cachedEntry = cachedMap.get(this);
            if (cachedEntry != null) {
                writeSerializedMapEntries(factory, out, cachedEntry.getValue());
                return;
            }
        }

        Map<String, Object> freshData = this.write(manager, conflict);
        writeMapEntries(out, freshData);

        if (conflictId <= 0) {
            return;
        }

        byte[] freshBytes = writeMsgpackBytes(mapper, freshData);
        manager.saveConflictRowCache(conflictId, freshBytes, this, now);
    }

    private static void writeMapEntries(JsonGenerator out, Map<String, Object> map)
            throws IOException {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            out.writeFieldName(entry.getKey());
            out.writeObject(entry.getValue());
        }
    }

    static void writeSerializedMapEntries(JsonFactory factory, JsonGenerator out, byte[] mapBytes)
            throws IOException {
        try (JsonParser parser = factory.createParser(mapBytes)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Cached part is not an object");
            }

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                if (fieldName == null) {
                    throw new IOException("Serialized object entry missing field name");
                }

                if (parser.nextToken() == null) {
                    throw new IOException("Serialized object entry missing value");
                }

                out.writeFieldName(fieldName);
                out.copyCurrentStructure(parser);
            }
        }
    }
}
