package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.imp.CoalitionMetricsGraph;
import link.locutus.discord.commands.manager.v2.table.imp.EntityTable;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.CoalitionGraph;
import link.locutus.discord.web.commands.binding.value_types.CoalitionGraphs;
import link.locutus.discord.web.commands.binding.value_types.GraphType;
import link.locutus.discord.web.commands.binding.value_types.WebGraph;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CoalitionGraphEndpoints {
    @Command()
    @ReturnType(CoalitionGraphs.class)
    public CoalitionGraphs globalStats(Set<AllianceMetric> metrics, @Timestamp long start, @Timestamp long end, int topX) {
        if (topX > 250) throw new IllegalArgumentException("Treaty information is not available for those alliances (outside top 250)");

        long startTurn = TimeUtil.getTurn(start);
        long endTurn = TimeUtil.getTurn(end);

        SphereGenerator spheres = new SphereGenerator(topX);
        Set<DBAlliance> alliances = spheres.getAlliances();

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(metrics, startTurn, endTurn, alliances);

        List<CoalitionGraph> sphereMil = new ObjectArrayList<>();

        for (int sphereId : spheres.getSpheres()) {
            String name = spheres.getSphereName(sphereId);
            List<DBAlliance> sphereAlliances = spheres.getAlliances(sphereId);

            Map<DBAlliance, Integer> alliancesMap = sphereAlliances.stream()
                    .collect(Collectors.toMap(alliance -> alliance, DBAlliance::getId));

            WebGraph overall = null;
            if (sphereAlliances.size() > 1) {
                overall = new CoalitionMetricsGraph(metricMap, metrics, startTurn, endTurn, name, new ObjectLinkedOpenHashSet<>(sphereAlliances))
                        .toHtmlJson();
            }

            Map<Integer, WebGraph> byAlliance = new Int2ObjectOpenHashMap<>();
            for (DBAlliance alliance : sphereAlliances) {
                WebGraph graph = new CoalitionMetricsGraph(metricMap, metrics, startTurn, endTurn, alliance.getName(), Collections.singleton(alliance))
                        .toHtmlJson();
                byAlliance.put(alliance.getId(), graph);
            }

            CoalitionGraph sphereMilitarization = new CoalitionGraph(name, alliancesMap, overall, byAlliance);
            sphereMil.add(sphereMilitarization);
        }

        CoalitionGraphs globalMil = new CoalitionGraphs(sphereMil);
        return globalMil;
    }

    @Command()
    @NoFormat
    @ReturnType(CoalitionGraphs.class)
    public CoalitionGraphs globalTierStats(Set<NationAttributeDouble> metrics, int topX, @Default("getCities") NationAttributeDouble groupBy, @Switch("t") boolean total) {
        if (topX > 250) throw new IllegalArgumentException("Treaty information is not available for those alliances (outside top 80)");

        boolean removeVM = true;
        int removeActiveM = 7200;
        boolean removeApps = true;

        SphereGenerator spheres = new SphereGenerator(topX);
        List<CoalitionGraph> sphereMil = new ObjectArrayList<>();

        for (int sphereId : spheres.getSpheres()) {
            String name = spheres.getSphereName(sphereId);
            List<DBAlliance> sphereAlliances = spheres.getAlliances(sphereId);

            Map<DBAlliance, Integer> alliancesMap = sphereAlliances.stream()
                    .collect(Collectors.toMap(alliance -> alliance, DBAlliance::getId));

            WebGraph overall = null;
            if (sphereAlliances.size() > 1) {
                overall = EntityTable.create(spheres.getSphereName(sphereId) + ": ", metrics,
                        spheres.getAlliances(sphereId), groupBy, total, removeVM, removeActiveM, removeApps
                ).toHtmlJson();
            }

            Map<Integer, WebGraph> byAlliance = new Int2ObjectOpenHashMap<>();
            for (DBAlliance alliance : sphereAlliances) {
                EntityTable<DBNation> table = EntityTable.create(alliance.getName() + ": ", metrics, Collections.singleton(alliance),
                        groupBy, total, removeVM, removeActiveM, removeApps
                );
                WebGraph graph = table.toHtmlJson();
                byAlliance.put(alliance.getId(), graph);
            }

            CoalitionGraph sphereMilitarization = new CoalitionGraph(name, alliancesMap, overall, byAlliance);
            sphereMil.add(sphereMilitarization);
        }

        CoalitionGraphs globalMil = new CoalitionGraphs(sphereMil);
        return globalMil;
    }
}
