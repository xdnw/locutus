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
import link.locutus.discord.util.math.StreamMerge;
import link.locutus.discord.web.jooby.JteUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public enum HeaderGroup {
    INDEX_META {
        @Override
        public long getHash() {
            List<String> headers = List.of(
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
            );
            return StringMan.hash(headers);
        }

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

    INDEX_STATS {
        @Override
        public long getHash() {
            // wars, active_wars, c1_dealt, c2_dealt
            List<String> headers = List.of(
                    "wars",
                    "active_wars",
                    "c1_dealt",
                    "c2_dealt"
            );
            return StringMan.hash(headers);
        }

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

    PAGE_META {
        @Override
        public long getHash() {
            // name, start, end, wiki, status, cb, posts, coalitions
            List<String> headers = List.of(
                    "name",
                    "start",
                    "end",
                    "wiki",
                    "status",
                    "cb",
                    "posts",
                    "coalitions"
            );
            return StringMan.hash(headers);
        }

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

    PAGE_STATS {
        @Override
        public long getHash() {
            // damage_header, header_desc, header_group, header_type, war_web, coalitions
            List<String> headers = List.of(
                    "damage_header",
                    "header_desc",
                    "header_group",
                    "header_type",
                    "war_web",
                    "coalitions"
            );
            return StringMan.hash(headers);
        }

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

    GRAPH_META {
        @Override
        public long getHash() {
            // name, start, end, coalitions
            List<String> headers = List.of(
                    "name",
                    "start",
                    "end",
                    "coalitions"
            );
            return StringMan.hash(headers);
        }

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

    GRAPH_DATA {
        @Override
        public long getHash() {
            // metric_names, metrics_turn, metrics_day, coalitions
            List<String> headers = List.of(
                    "metric_names",
                    "metrics_turn",
                    "metrics_day",
                    "coalitions"
            );
            return StringMan.hash(headers);
        }

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

    public static final HeaderGroup[] values = values();

    public abstract long getHash(); // StringMan.hash(List<String>) - of the header names

    public abstract Map<String, Object> write(ConflictManager manager, Conflict conflict);

    public void writeGroupWithCacheFlat(
            ConflictManager manager,
            JsonGenerator gen,
            int conflictId,
            long now,
            boolean forceUpdate,
            Conflict conflict
    ) throws IOException {
        ObjectMapper mapper = JteUtil.getSerializer();
        JsonFactory factory = mapper.getFactory();
        Map<String, Object> freshData = this.write(manager, conflict);
        byte[] freshBytes = mapper.writeValueAsBytes(freshData);

        if (conflictId <= 0) {
            copyJsonPayload(factory, freshBytes, gen);
            return;
        }

        Map.Entry<Long, byte[]> cachedEntry = null;
        if (!forceUpdate) {
            Map<HeaderGroup, Map.Entry<Long, byte[]>> cachedMap =
                    manager.loadConflictRowCache(conflictId, Set.of(this));
            cachedEntry = cachedMap.get(this);
        }

        if (cachedEntry == null) {
            copyJsonPayload(factory, freshBytes, gen);
            manager.saveConflictRowCache(conflictId, freshBytes, this, now);
            return;
        }

        byte[] cachedBytes = cachedEntry.getValue();

        if (Arrays.equals(cachedBytes, freshBytes)) {
            copyJsonPayload(factory, freshBytes, gen);
            return;
        }

        try (JsonParser cachedParser = factory.createParser(cachedBytes);
             JsonParser freshParser = factory.createParser(freshBytes)) {
            StreamMerge.merge(cachedParser, freshParser, gen);
        }

        manager.saveConflictRowCache(conflictId, freshBytes, this, now);
    }

    private static void copyJsonPayload(JsonFactory factory, byte[] payload, JsonGenerator gen) throws IOException {
        try (JsonParser parser = factory.createParser(payload)) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken == null) {
                gen.writeStartObject();
                gen.writeEndObject();
                return;
            }
            gen.copyCurrentStructure(parser);
        }
    }

    public static byte[] getBytesZip(
            ConflictManager manager,
            Conflict conflict,
            Map<HeaderGroup, Boolean> forceUpdate,
            long now
    ) {
        try {
            ObjectMapper mapper = JteUtil.getSerializer();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JsonGenerator gen = mapper.getFactory().createGenerator(out);

            gen.writeStartObject();

            for (Map.Entry<HeaderGroup, Boolean> entry : forceUpdate.entrySet()) {
                HeaderGroup group = entry.getKey();
                boolean force = entry.getValue();
                group.writeGroupWithCacheFlat(manager, gen, conflict.getId(), now, force, conflict);
            }

            gen.writeNumberField("update_ms", now);

            gen.writeEndObject();
            gen.close();

            return JteUtil.compress(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
