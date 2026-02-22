package link.locutus.discord.pnw;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.dv8tion.jda.api.entities.Guild;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.MethodEnum;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.ScopedPlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface NationList extends NationFilter {
    default AllianceList toAllianceList() {
        return new AllianceList(getAllianceIds());
    }

    @Override
    default String getFilter() {
        return null;
    }

    @Override
    default boolean test(DBNation dbNation) {
        return getNations().contains(dbNation);
    }

    Set<DBNation> getNations();

    default Set<Integer> getAllianceIds() {
        return getNations().stream().map(DBNation::getAlliance_id).collect(Collectors.toSet());
    }

    default DBNation getTotal() {
        return DBNation.createFromList(null, getNations(), false);
    }

    default DBNation getAverage() {
        return DBNation.createFromList(null, getNations(), true);
    }

    default Set<Integer> getNationIds() {
        return getNations().stream().map(DBNation::getNation_id).collect(Collectors.toSet());
    }

    default void updateCities(boolean events) {
        if (events) {
            Locutus.imp().runEventsAsync(
                    f -> Locutus.imp().getNationDB().updateCitiesOfNations(getNationIds(), true, true, f));
        } else {
            Locutus.imp().getNationDB().updateCitiesOfNations(getNationIds(), true, true, null);
        }
    }

    @Command
    default NationList getNations(ValueStore store, Predicate<DBNation> filter, @Default @Timestamp Long timestamp) {
        if (timestamp == null || timestamp >= TimeUtil.getTimeFromDay(TimeUtil.getDay())) {
            Set<DBNation> result = new ObjectOpenHashSet<>();
            for (DBNation nation : getNations()) {
                if (filter.test(nation)) {
                    result.add(nation);
                }
            }
            return new SimpleNationList(result);
        }

        ScopedPlaceholderCache<NationList> scoped = PlaceholderCache.getScopedAssignableTo(
                store,
                NationList.class,
                NationList.class,
                getClass(),
                MethodEnum.getNationsAt.of(timestamp, filter));

        return scoped.getMap(this, scopes -> {
            Map<NationList, NationList> result = new Object2ObjectOpenHashMap<>(scopes.size());
            Map<Integer, DBNation> uniqueNationsById = new Int2ObjectOpenHashMap<>();
            Set<String> scopeFilters = new ObjectLinkedOpenHashSet<>();
            Map<Integer, List<NationList>> nationToScopes = new Int2ObjectOpenHashMap<>();
            Map<NationList, Set<DBNation>> redistributed = new Object2ObjectOpenHashMap<>(scopes.size());
            String invocationFilter = filter instanceof NationFilter nf ? nf.getFilter() : null;
            boolean usePredicatePostFilter = invocationFilter == null || invocationFilter.isBlank();
            for (NationList scope : scopes) {
                Set<DBNation> scopeNations = scope.getNations();
                String scopeFilter = scope.getFilter();
                if (scopeFilter != null && !scopeFilter.isBlank()) {
                    scopeFilters.add(scopeFilter);
                }
                redistributed.put(scope, new ObjectOpenHashSet<>());
                for (DBNation nation : scopeNations) {
                    int nationId = nation.getNation_id();
                    uniqueNationsById.putIfAbsent(nationId, nation);
                    nationToScopes.computeIfAbsent(nationId, k -> new ObjectArrayList<>()).add(scope);
                }
            }
            String scopeUnionFilter = scopeFilters.isEmpty() ? null : "((" + String.join(")|(", scopeFilters) + "))";
            String combinedFilter;
            if (usePredicatePostFilter) {
                combinedFilter = scopeUnionFilter;
            } else if (scopeUnionFilter == null) {
                combinedFilter = invocationFilter;
            } else {
                // Preserve original scope union semantics, then intersect with invocation filter.
                combinedFilter = scopeUnionFilter + ",(" + invocationFilter + ")";
            }

            Set<DBNation> snapshot = PW.getNationsSnapshot(uniqueNationsById.values(), combinedFilter, timestamp,
                    (Guild) null);

            for (DBNation nation : snapshot) {
                List<NationList> nationScopes = nationToScopes.get(nation.getNation_id());
                if (nationScopes == null) {
                    continue;
                }
                if (usePredicatePostFilter && !filter.test(nation)) {
                    continue;
                }
                for (NationList scope : nationScopes) {
                    redistributed.get(scope).add(nation);
                }
            }

            for (NationList scope : scopes) {
                result.put(scope, new SimpleNationList(redistributed.get(scope)));
            }
            return result;
        });
    }

    @Command
    default double[] getAverageMMRUnit() {
        double[] total = getTotalMMRUnit();
        double num = total[4];
        return new double[] {
                total[0] / (num * Buildings.BARRACKS.getUnitCap()),
                total[1] / (num * Buildings.FACTORY.getUnitCap()),
                total[2] / (num * Buildings.HANGAR.getUnitCap()),
                total[3] / (num * Buildings.DRYDOCK.getUnitCap())
        };
    }

    @Command
    default double[] getAverageMMR(@Default Boolean update) {
        double[] total = getTotalMMR(update == Boolean.TRUE);
        double num = total[4];
        return new double[] { total[0] / num, total[1] / num, total[2] / num, total[3] / num };
    }

    default <T> Map<T, NationList> groupBy(Function<DBNation, T> groupBy) {
        Map<T, List<DBNation>> mapList = new HashMap<>();
        for (DBNation nation : getNations()) {
            T group = groupBy.apply(nation);
            mapList.computeIfAbsent(group, f -> new ArrayList<>()).add(nation);
        }
        Map<T, NationList> result = new HashMap<>();
        for (Map.Entry<T, List<DBNation>> entry : mapList.entrySet()) {
            result.put(entry.getKey(), new SimpleNationList(entry.getValue()));
        }
        return result;
    }

    default Map<Integer, NationList> byTier() {
        return groupBy(DBNation::getCities);
    }

    default <T> Stream<T> stream(Function<? super DBNation, ? extends T> map) {
        return getNations().stream().map(map);
    }

    default Stream<DBNation> stream() {
        return getNations().stream();
    }

    /**
     * in the form [barracks, factories, hangars, drydocks, cities]
     * 
     * @param update
     * @return
     */
    default double[] getTotalMMR(boolean update) {
        double barracks = 0;
        double factories = 0;
        double hangars = 0;
        double drydocks = 0;

        int numCities = 0;

        if (update) {
            Locutus.imp().getNationDB().markDirtyIncorrectCities(getNations(), true, true);
            Locutus.imp().returnEventsAsync(
                    events -> Locutus.imp().getNationDB().updateDirtyCities(false, events, Integer.MAX_VALUE));
        }
        for (DBNation nation : getNations()) {
            Map<Integer, JavaCity> cities = nation.getCityMap(false, false, false);
            numCities += cities.size();
            for (Map.Entry<Integer, JavaCity> cityEntry : cities.entrySet()) {
                barracks += cityEntry.getValue().getBuilding(Buildings.BARRACKS);
                factories += cityEntry.getValue().getBuilding(Buildings.FACTORY);
                hangars += cityEntry.getValue().getBuilding(Buildings.HANGAR);
                drydocks += cityEntry.getValue().getBuilding(Buildings.DRYDOCK);
            }
        }

        return new double[] { barracks, factories, hangars, drydocks, numCities };
    }

    /**
     * in the form [soldiers, tanks, aircraft, ships, cities]
     * 
     * @return
     */
    default double[] getTotalMMRUnit() {
        double soldiers = 0;
        double tanks = 0;
        double aircraft = 0;
        double ships = 0;

        int numCities = 0;
        for (DBNation nation : getNations()) {
            soldiers += nation.getSoldiers();
            tanks += nation.getTanks();
            aircraft += nation.getAircraft();
            ships += nation.getShips();
            numCities += nation.getCities();
        }
        return new double[] { soldiers, tanks, aircraft, ships, numCities };
    }

    default String toMarkdown() {
        return toMarkdown(true, true, true, true, true, true, true);
    }

    // arguments for controlling which information to include
    default String toMarkdown(boolean includeAlliance, boolean includeRevenue, boolean includeWars,
            boolean includeColor, boolean includePositions, boolean includeData, boolean includeMMR) {
        StringBuilder body = new StringBuilder();
        Set<DBNation> nations = getNations();
        body.append("> " + nations.size() + " nations ");
        int countNone = nations.stream().filter(f -> f.getAlliance_id() == 0).collect(Collectors.toSet()).size();
        if (includeAlliance) {
            Set<DBAlliance> alliances = nations.stream().map(DBNation::getAlliance).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (alliances.isEmpty()) {
                body.append("(No alliance)\n");
            } else if (alliances.size() == 1) {
                DBAlliance aa = alliances.iterator().next();
                body.append(" in " + aa.getMarkdownUrl() + "\n");
            } else if (alliances.size() <= 5) {
                body.append(" in\n");
                for (DBAlliance aa : alliances) {
                    int count = (int) nations.stream().filter(f -> f.getAlliance_id() == aa.getId()).count();
                    body.append("> - ").append(aa.getMarkdownUrl()).append("x").append(count).append("\n");
                }
            } else {
                body.append(" in " + alliances.size() + " alliances\n");
            }
            if (countNone > 0 && !alliances.isEmpty()) {
                body.append("(" + countNone + " in None)\n");
            }
        }
        if (includeColor) {
            Map<NationColor, Integer> colors = ArrayUtil.sortMap(
                    nations.stream().collect(Collectors.groupingBy(DBNation::getColor, Collectors.summingInt(f -> 1))),
                    true);
            if (colors.size() == 1) {
                NationColor color = colors.keySet().iterator().next();
                body.append("**Color:** `" + color + "`\n");
            } else {
                body.append("**Colors:** `" + StringMan.getString(colors) + "`\n");
            }
        }
        // Map<WarPolicy, Integer> warPolicy =
        // ArrayUtil.sortMap(nations.stream().collect(Collectors.groupingBy(DBNation::getWarPolicy,
        // Collectors.summingInt(f -> 1))), true);
        // if (warPolicy.size() == 1) {
        // WarPolicy policy = warPolicy.keySet().iterator().next();
        // body.append("**War Policy:** `" + policy + "`\n");
        // } else {
        // body.append("**War Policies:** `" + StringMan.getString(warPolicy) + "`\n");
        // }

        if (includePositions || includeData) {
            body.append("```\n");
            Set<DBNation> members = nations.stream()
                    .filter(n -> n.getPosition() > Rank.APPLICANT.id && n.getVm_turns() == 0)
                    .collect(Collectors.toSet());
            if (includePositions) {
                // Number of members / applicants (active past day)
                Set<DBNation> activeMembers = members.stream().filter(n -> n.active_m() < 7200)
                        .collect(Collectors.toSet());
                Set<DBNation> taxableMembers = members.stream().filter(DBNation::isTaxable).collect(Collectors.toSet());
                Set<DBNation> applicants = nations.stream()
                        .filter(n -> n.getPosition() == Rank.APPLICANT.id && n.getVm_turns() == 0)
                        .collect(Collectors.toSet());
                Set<DBNation> activeApplicants = applicants.stream().filter(n -> n.active_m() < 7200)
                        .collect(Collectors.toSet());
                // 5 members (3 active/2 taxable) | 2 applicants (1 active)
                body.append(members.size()).append(" members (").append(activeMembers.size()).append(" active/")
                        .append(taxableMembers.size()).append(" taxable)");
                if (!applicants.isEmpty()) {
                    body.append(" | ").append(applicants.size()).append(" applicants (").append(activeApplicants.size())
                            .append(" active)");
                }
                body.append("\n");
            }
            if (includeData) {
                // Off, Def, Cities (total/average), Score, Color
                int off = nations.stream().mapToInt(DBNation::getOff).sum();
                int def = nations.stream().mapToInt(DBNation::getDef).sum();
                int cities = members.stream().mapToInt(DBNation::getCities).sum();
                double avgCities = cities / (double) members.size();
                double score = members.stream().mapToDouble(DBNation::getScore).sum();
                body.append(off).append("\uD83D\uDDE1 | ")
                        .append(def).append("\uD83D\uDEE1 | ")
                        .append(cities).append("\uD83C\uDFD9").append(" (avg:").append(MathMan.format(avgCities))
                        .append(") | ")
                        .append(MathMan.format(score)).append("ns");
            }
            body.append("\n```\n");
        }

        if (includeMMR) {
            double[] mmrBuild = this.getAverageMMR(false);
            double[] mmrUnit = this.getAverageMMRUnit();
            // Convert to e.g. MMR[Build]=1.5/2.5/1.1/3.0 | MMR[Unit]=1.5/2.5/1.1/3.0
            // append with each number on newline
            body.append("\n**MMR[Build]**: `")
                    .append(MathMan.format(mmrBuild[0])).append("/")
                    .append(MathMan.format(mmrBuild[1])).append("/")
                    .append(MathMan.format(mmrBuild[2])).append("/")
                    .append(MathMan.format(mmrBuild[3])).append("`")
                    .append("\n**MMR[Unit]**: `")
                    .append(MathMan.format(mmrUnit[0])).append("/")
                    .append(MathMan.format(mmrUnit[1])).append("/")
                    .append(MathMan.format(mmrUnit[2])).append("/")
                    .append(MathMan.format(mmrUnit[3])).append("`\n");
        }

        if (includeWars) {
            Map<DBAlliance, Integer> warsByAlliance = new HashMap<>();
            for (DBWar war : getActiveWars()) {
                DBNation attacker = war.getNation(true);
                DBNation defender = war.getNation(false);
                if (attacker == null || attacker.active_m() > 7200)
                    continue;
                if (defender == null || defender.active_m() > 7200)
                    continue;
                int otherAAId = nations.contains(attacker) ? defender.getAlliance_id() : attacker.getAlliance_id();
                if (otherAAId > 0) {
                    DBAlliance otherAA = DBAlliance.getOrCreate(otherAAId);
                    warsByAlliance.put(otherAA, warsByAlliance.getOrDefault(otherAA, 0) + 1);
                }
            }
            if (!warsByAlliance.isEmpty()) {
                List<Map.Entry<DBAlliance, Integer>> sorted = warsByAlliance.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .toList();
                body.append("\n**Alliance Wars:**\n");
                String cappedMsg = null;
                if (sorted.size() > 20) {
                    cappedMsg = "- +" + (sorted.size() - 20) + " more";
                    sorted = sorted.stream().limit(20).collect(Collectors.toList());
                }
                for (Map.Entry<DBAlliance, Integer> entry : sorted) {
                    body.append("- ").append(PW.getMarkdownUrl(entry.getKey().getId(), true))
                            .append(": ").append(entry.getValue()).append(" wars\n");
                }
                if (cappedMsg != null) {
                    body.append(cappedMsg).append("\n");
                }
            }
        }
        if (includeRevenue) {
            // Revenue
            Map<ResourceType, Double> revenue = getRevenue();
            if (revenue.isEmpty()) {
                body.append("`No taxable revenue`\n");
            } else {
                body.append("\n**Taxable Nation Revenue:**");
                body.append("`").append(ResourceType.toString(revenue)).append("`\n");
                body.append("- worth: `$" + MathMan.format(ResourceType.convertedTotal(revenue)) + "`\n");
            }
        }
        return body.toString();
    }

    default Set<DBWar> getActiveWars() {
        Set<DBWar> wars = new ObjectOpenHashSet<>();
        for (DBNation nation : getNations()) {
            wars.addAll(nation.getActiveWars());
        }
        return wars;
    }

    default Set<DBNation> getNationsMatching(Predicate<DBNation> nations) {
        return getNations().stream().filter(nations).collect(Collectors.toSet());
    }

    @Command(desc = "Sum of nation attribute for the selected nations")
    default double getTotal(@NoFormat TypedFunction<DBNation, Double> attribute,
            @NoFormat @Default NationFilter filter) {
        if (filter == null) {
            return getNations().stream().mapToDouble(attribute::apply).sum();
        }
        Predicate<DBNation> predicate = filter.toCached(Long.MAX_VALUE);
        double total = 0;
        for (DBNation nation : getNations()) {
            if (predicate.test(nation)) {
                total += attribute.apply(nation);
            }
        }
        return total;
    }

    @Command(desc = "Average of nation attribute for the selected nations")
    default double getAverage(@NoFormat TypedFunction<DBNation, Double> attribute,
            @NoFormat @Default NationFilter filter) {
        if (filter == null) {
            return getNations().stream().mapToDouble(attribute::apply).average().orElse(0);
        }
        Predicate<DBNation> predicate = filter.toCached(Long.MAX_VALUE);
        double total = 0;
        int count = 0;
        for (DBNation nation : getNations()) {
            if (predicate.test(nation)) {
                total += attribute.apply(nation);
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    @Command(desc = "Average of one nation attribute per another attribute")
    default double getAveragePer(@NoFormat TypedFunction<DBNation, Double> attribute,
            @NoFormat TypedFunction<DBNation, Double> per,
            @Default NationFilter filter) {
        Predicate<DBNation> predicate = filter == null ? null : filter.toCached(Long.MAX_VALUE);
        double total = 0;
        double perTotal = 0;
        for (DBNation nation : getNations()) {
            if (predicate != null && !predicate.test(nation)) {
                continue;
            }
            total += attribute.apply(nation);
            perTotal += per.apply(nation);
        }
        return total / perTotal;
    }

    @Command(desc = "Number of members, not including VM")
    default int countMembers() {
        int count = 0;
        for (DBNation nation : getNations()) {
            if (nation.getPosition() > Rank.APPLICANT.id && nation.getVm_turns() == 0) {
                count++;
            }
        }
        return count;
    }

    @Command(desc = "Count of selected nations matching a filter")
    default int countNations(@NoFormat @Default NationFilter filter) {
        if (filter == null) {
            return getNations().size();
        }
        Predicate<DBNation> predicate = filter.toCached(Long.MAX_VALUE);
        int count = 0;
        for (DBNation nation : getNations()) {
            if (predicate.test(nation)) {
                count++;
            }
        }
        return count;
    }

    @Command(desc = "Market value of taxable nation revenue")
    default double getRevenueConverted() {
        return ResourceType.convertedTotal(getRevenue());
    }

    @Command(desc = "Revenue of taxable nations")
    default Map<ResourceType, Double> getRevenue() {
        return getRevenue(getNationsMatching(DBNation::isTaxable));
    }

    private Map<ResourceType, Double> getRevenue(Set<DBNation> nations) {
        double[] total = ResourceType.getBuffer();
        ValueStore<DBNation> cacheStore = PlaceholderCache.createCache(nations, DBNation.class);
        for (DBNation nation : nations) {
            total = ResourceType.add(total, nation.getRevenue(cacheStore));
        }
        return ResourceType.resourcesToMap(total);
    }

    default double[] getMilitaryBuyPct(boolean update) {
        DBNation total = getTotal();

        double oldSoldier = 0;
        double oldTanks = 0;
        double oldAir = 0;
        double oldSea = 0;

        long yesterday = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);

        Collection<DBNation> aaNations = getNations();
        for (DBNation nation : aaNations) {
            oldSoldier += nation.getUnitsAt(MilitaryUnit.SOLDIER, yesterday);
            oldTanks += nation.getUnitsAt(MilitaryUnit.TANK, yesterday);
            oldAir += nation.getUnitsAt(MilitaryUnit.AIRCRAFT, yesterday);
            oldSea += nation.getUnitsAt(MilitaryUnit.SHIP, yesterday);
        }

        double soldierBuyTotal = (total.getSoldiers() - oldSoldier) / oldSoldier;
        double tankBuyTotal = (total.getTanks() - oldTanks) / oldTanks;
        double airBuyTotal = (total.getAircraft() - oldAir) / oldAir;
        double navyBuyTotal = (total.getShips() - oldSea) / oldSea;

        return new double[] { soldierBuyTotal, tankBuyTotal, airBuyTotal, navyBuyTotal };
    }

    default double getScore() {
        Collection<DBNation> nations = new ArrayList<>(getNations());
        nations.removeIf(f -> f.getPosition() <= 0 || f.getVm_turns() != 0);
        double total = 0;
        for (DBNation nation : nations) {
            total += nation.getScore();
        }
        return total;
    }

    default Set<Integer> updateSpies(boolean updateManually) {
        Set<DBNation> toUpdate = new HashSet<>(getNations());
        return toUpdate.stream().map(DBNation::getNation_id).collect(Collectors.toSet());
        // toUpdate.removeIf(f -> f.getVm_turns() > 0 || f.active_m() > 7200);
        // Set<Integer> alliances = new HashSet<>();
        // for (DBNation nation : toUpdate) {
        // if (nation.getPosition() > Rank.APPLICANT.id) {
        // alliances.add(nation.getAlliance_id());
        // }
        // }
        // Set<Integer> updated = new HashSet<>();
        // boolean hasUpdated = false;
        // for (Integer allianceId : alliances) {
        // Set<Integer> result = DBAlliance.getOrCreate(allianceId).updateSpies(false);
        // updated.addAll(result);
        // toUpdate.removeIf(f -> result.contains(f.getId()));
        // }
        // if (updateManually) {
        // for (DBNation nation : toUpdate) {
        // nation.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK);
        // updated.add(nation.getId());
        // }
        // }
        // return updated;
    }

    default boolean contains(DBNation f) {
        return getNations().contains(f);
    }
}
