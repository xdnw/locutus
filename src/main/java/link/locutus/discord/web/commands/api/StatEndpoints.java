package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderRegistry;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.*;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Predicate;

public class StatEndpoints {
    @Command(desc = "Render a custom WebTable using a placeholder type and specified columns", viewable = true)
    @ReturnType(WebTable.class)
    public <T> WebTable table(ValueStore store, PlaceholderRegistry registry, @Me @Default User user,
                              @PlaceholderType Class<?> type,
                              String selection_str,
                              @TextArea List<String> columns) {
        Class<T> typeCasted = (Class<T>) type;
        Placeholders<T, Object> ph = registry.get(typeCasted);
        Object modifier = null;
        if (selection_str.startsWith("{") && selection_str.endsWith("}")) {
            JSONObject json = new JSONObject(selection_str);
            Map<String, Object> args = json.toMap();
            Map.Entry<String, ?> entry = ph.parseModifier(store, args);
            if (entry != null && entry.getValue() != null) {
                selection_str = entry.getKey();
                modifier = entry.getValue();
            }
        }

        Set<T> selection = ph.parseSet(store, selection_str, modifier);
        return renderTable(store, registry, typeCasted, selection, columns);
    }

    @Command(desc = "Render wars involving any selected nations or alliances as a WebTable", viewable = true)
    @ReturnType(WebTable.class)
    public WebTable warsInvolving(ValueStore store, PlaceholderRegistry registry, WarDB warDB,
                                  @AllowDeleted @Default("*") Set<NationOrAlliance> coalition1,
                      @TextArea List<String> columns,
                      @Default @Switch("start") @Timestamp Long startTime,
                      @Default @Switch("end") @Timestamp Long endTime,
                      @Switch("inactive") boolean includeInactiveWars,
                      @Switch("wartype") Set<WarType> allowedWarTypes,
                      @Switch("status") Set<WarStatus> allowedWarStatuses,
                      @Switch("off") boolean onlyOffensiveWars,
                      @Switch("def") boolean onlyDefensiveWars) {
        List<DBWar> wars = warsInvolving(warDB, coalition1, startTime, endTime, includeInactiveWars,
                allowedWarTypes, allowedWarStatuses, onlyOffensiveWars, onlyDefensiveWars);
        return renderTable(store, registry, DBWar.class, new LinkedHashSet<>(wars), columns);
    }

    @Command(desc = "Render wars between two selected nation or alliance coalitions as a WebTable", viewable = true)
    @ReturnType(WebTable.class)
    public WebTable warsBetween(ValueStore store, PlaceholderRegistry registry, WarDB warDB,
                                @AllowDeleted @Default("*") Set<NationOrAlliance> sideA,
                                @AllowDeleted @Default("*") Set<NationOrAlliance> sideB,
                    @TextArea List<String> columns,
                    @Default @Switch("start") @Timestamp Long startTime,
                    @Default @Switch("end") @Timestamp Long endTime,
                    @Switch("inactive") boolean includeInactiveWars,
                    @Switch("wartype") Set<WarType> allowedWarTypes,
                    @Switch("status") Set<WarStatus> allowedWarStatuses,
                    @Switch("off") boolean onlyOffensiveWars,
                    @Switch("def") boolean onlyDefensiveWars) {
        List<DBWar> wars = warsBetween(warDB, sideA, sideB, startTime, endTime, includeInactiveWars,
                allowedWarTypes, allowedWarStatuses, onlyOffensiveWars, onlyDefensiveWars);
        return renderTable(store, registry, DBWar.class, new LinkedHashSet<>(wars), columns);
    }

    static List<DBWar> warsInvolving(WarDB warDB, Set<NationOrAlliance> coalition1,
                                     Long startTime, Long endTime,
                                     boolean includeInactiveWars,
                                     Set<WarType> allowedWarTypes,
                                     Set<WarStatus> allowedWarStatuses,
                                     boolean onlyOffensiveWars,
                                     boolean onlyDefensiveWars) {
        CoalitionParticipants primary = CoalitionParticipants.of(coalition1, "Please provide at least one nation or alliance");
        WarTableFilters filters = new WarTableFilters(startTime, endTime, includeInactiveWars,
                allowedWarTypes, allowedWarStatuses, onlyOffensiveWars, onlyDefensiveWars);
        Predicate<DBWar> participantFilter;
        if (filters.onlyOffensiveWars()) {
            participantFilter = primary::matchesAttacker;
        } else if (filters.onlyDefensiveWars()) {
            participantFilter = primary::matchesDefender;
        } else {
            participantFilter = war -> primary.matchesAttacker(war) || primary.matchesDefender(war);
        }
        Collection<DBWar> sourceWars = filters.includeInactiveWars()
                ? warDB.getWarsForNationOrAlliance(primary.nationIdsOrNull(), primary.allianceIdsOrNull())
                : collectActiveWars(warDB, primary, filters.activeStatuses());
        return filterAndSort(sourceWars, filters, participantFilter);
    }

    static List<DBWar> warsBetween(WarDB warDB, Set<NationOrAlliance> sideA, Set<NationOrAlliance> sideB,
                                   Long startTime, Long endTime,
                                   boolean includeInactiveWars,
                                   Set<WarType> allowedWarTypes,
                                   Set<WarStatus> allowedWarStatuses,
                                   boolean onlyOffensiveWars,
                                   boolean onlyDefensiveWars) {
        CoalitionParticipants primary = CoalitionParticipants.of(sideA, "Please provide at least one sideA nation or alliance");
        CoalitionParticipants secondary = CoalitionParticipants.of(sideB, "Please provide at least one sideB nation or alliance");
        WarTableFilters filters = new WarTableFilters(startTime, endTime, includeInactiveWars,
                allowedWarTypes, allowedWarStatuses, onlyOffensiveWars, onlyDefensiveWars);
        Predicate<DBWar> participantFilter;
        if (filters.onlyOffensiveWars()) {
            participantFilter = war -> primary.matchesAttacker(war) && secondary.matchesDefender(war);
        } else if (filters.onlyDefensiveWars()) {
            participantFilter = war -> primary.matchesDefender(war) && secondary.matchesAttacker(war);
        } else {
            participantFilter = war ->
                    (primary.matchesAttacker(war) && secondary.matchesDefender(war))
                            || (primary.matchesDefender(war) && secondary.matchesAttacker(war));
        }
        Collection<DBWar> sourceWars;
        if (filters.includeInactiveWars()) {
            sourceWars = warDB.getWars(
                    primary.allianceIds(),
                    primary.nationIds(),
                    secondary.allianceIds(),
                    secondary.nationIds(),
                    filters.historyLookupStart(),
                    filters.historyLookupEnd()
            ).values();
        } else {
            CoalitionParticipants lookupSide = primary.lookupCost() <= secondary.lookupCost() ? primary : secondary;
            sourceWars = collectActiveWars(warDB, lookupSide, filters.activeStatuses());
        }
        return filterAndSort(sourceWars, filters, participantFilter);
    }

    private static Collection<DBWar> collectActiveWars(WarDB warDB, CoalitionParticipants coalition, WarStatus[] statuses) {
        if (statuses.length == 0) {
            return Collections.emptySet();
        }
        ObjectOpenHashSet<DBWar> result = new ObjectOpenHashSet<>();
        for (int nationId : coalition.nationIds()) {
            for (DBWar war : warDB.getActiveWars(nationId)) {
                if (isAllowedStatus(war, statuses)) {
                    result.add(war);
                }
            }
        }
        if (!coalition.allianceIds().isEmpty()) {
            result.addAll(warDB.getActiveWars(coalition.allianceIds(), statuses));
        }
        return result;
    }

    private static boolean isAllowedStatus(DBWar war, WarStatus[] statuses) {
        for (WarStatus status : statuses) {
            if (war.getStatus() == status) {
                return true;
            }
        }
        return false;
    }

    private static List<DBWar> filterAndSort(Collection<DBWar> wars,
                                             WarTableFilters filters,
                                             Predicate<DBWar> participantFilter) {
        List<DBWar> filtered = new ObjectArrayList<>(wars.size());
        for (DBWar war : wars) {
            if (filters.matches(war) && participantFilter.test(war)) {
                filtered.add(war);
            }
        }
        filtered.sort(Comparator.comparingInt(DBWar::getWarId));
        return filtered;
    }

    private record CoalitionParticipants(IntOpenHashSet nationIds, IntOpenHashSet allianceIds) {
        static CoalitionParticipants of(Set<NationOrAlliance> coalition, String emptyMessage) {
            if (coalition == null || coalition.isEmpty()) {
                throw new IllegalArgumentException(emptyMessage);
            }
            IntOpenHashSet nationIds = new IntOpenHashSet(coalition.size());
            IntOpenHashSet allianceIds = new IntOpenHashSet(coalition.size());
            for (NationOrAlliance participant : coalition) {
                if (participant == null) {
                    continue;
                }
                if (participant.isNation()) {
                    nationIds.add(participant.getId());
                } else if (participant.isAlliance()) {
                    allianceIds.add(participant.getId());
                } else {
                    throw new IllegalArgumentException("Unsupported coalition entry: " + participant);
                }
            }
            if (nationIds.isEmpty() && allianceIds.isEmpty()) {
                throw new IllegalArgumentException(emptyMessage);
            }
            return new CoalitionParticipants(nationIds, allianceIds);
        }

        static CoalitionParticipants empty() {
            return new CoalitionParticipants(new IntOpenHashSet(0), new IntOpenHashSet(0));
        }

        Set<Integer> nationIdsOrNull() {
            return nationIds.isEmpty() ? null : nationIds;
        }

        Set<Integer> allianceIdsOrNull() {
            return allianceIds.isEmpty() ? null : allianceIds;
        }

        int lookupCost() {
            return nationIds.size() + allianceIds.size();
        }

        boolean matchesAttacker(DBWar war) {
            return nationIds.contains(war.getAttacker_id()) || allianceIds.contains(war.getAttacker_aa());
        }

        boolean matchesDefender(DBWar war) {
            return nationIds.contains(war.getDefender_id()) || allianceIds.contains(war.getDefender_aa());
        }
    }

    private record WarTableFilters(Long startTime, Long endTime,
                                   boolean includeInactiveWars,
                                   Set<WarType> allowedWarTypes,
                                   Set<WarStatus> allowedWarStatuses,
                                   boolean onlyOffensiveWars,
                                   boolean onlyDefensiveWars) {
        private WarTableFilters {
            if (onlyOffensiveWars && onlyDefensiveWars) {
                throw new IllegalArgumentException("Cannot combine `onlyOffensiveWars` and `onlyDefensiveWars`");
            }
            if (startTime != null && endTime != null && endTime < startTime) {
                throw new IllegalArgumentException("endTime must be >= startTime");
            }
            if (allowedWarTypes != null) {
                allowedWarTypes = allowedWarTypes.isEmpty() ? EnumSet.noneOf(WarType.class) : EnumSet.copyOf(allowedWarTypes);
            }
            if (allowedWarStatuses != null) {
                allowedWarStatuses = allowedWarStatuses.isEmpty() ? EnumSet.noneOf(WarStatus.class) : EnumSet.copyOf(allowedWarStatuses);
            }
        }

        long historyLookupStart() {
            if (startTime == null || startTime <= 0L) {
                return 0L;
            }
            return startTime - 1L;
        }

        long historyLookupEnd() {
            if (endTime == null || endTime == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            return endTime + 1L;
        }

        WarStatus[] activeStatuses() {
            if (includeInactiveWars) {
                return new WarStatus[0];
            }
            EnumSet<WarStatus> statuses = EnumSet.of(WarStatus.ACTIVE, WarStatus.ATTACKER_OFFERED_PEACE, WarStatus.DEFENDER_OFFERED_PEACE);
            if (allowedWarStatuses != null) {
                statuses.retainAll(allowedWarStatuses);
            }
            return statuses.toArray(WarStatus[]::new);
        }

        boolean matches(DBWar war) {
            if (startTime != null && war.getDate() < startTime) {
                return false;
            }
            if (endTime != null && war.getDate() > endTime) {
                return false;
            }
            if (!includeInactiveWars && !war.getStatus().isActive()) {
                return false;
            }
            if (allowedWarTypes != null && !allowedWarTypes.contains(war.getWarType())) {
                return false;
            }
            if (allowedWarStatuses != null && !allowedWarStatuses.contains(war.getStatus())) {
                return false;
            }
            return true;
        }
    }

    private static <T> WebTable renderTable(ValueStore store, PlaceholderRegistry registry, Class<T> type,
                                            Set<T> selection, List<String> columns) {
        Map<Integer, List<WebTableError>> errors = new LinkedHashMap<>();
        int maxPerCol = 500;
        Placeholders<T, Object> ph = registry.get(type);
        ValueStore cacheStore = PlaceholderCache.createCache(store, selection, type);

        List<String> renderers = new ObjectArrayList<>(columns.size());
        List<TypedFunction<T, ?>> formatters = new ObjectArrayList<>(columns.size());
        boolean[] isEnum = new boolean[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            try {
                TypedFunction<T, ?> result = ph.formatRecursively(cacheStore, column, null, 0, false, true);
                Type rsType = result.getType();
                formatters.add(result);
                if (rsType instanceof Class clazz) {
                    renderers.add(switch (clazz.getSimpleName()) {
                        case "Map" -> "numeric_map";
                        case "WebGraph" -> "graph";
                        case "double", "Double", "int", "Integer", "float", "Float" -> "comma";
                        case "NationColor" -> "color";
                        case "String" -> "normal";
                        default -> {
                            if (clazz.isEnum()) {
                                isEnum[i] = true;
                                yield "enum:" + clazz.getSimpleName();
                            } else {
                                yield null;
                            }
                        }
                    });
                } else {
                    renderers.add(null);
                }
            } catch (Exception e) {
                e.printStackTrace();
                List<WebTableError> errList = errors.computeIfAbsent(i, k -> new ObjectArrayList<>(maxPerCol));
                if (errList.size() < maxPerCol) {
                    errList.add(new WebTableError(i, null, e.getMessage()));
                }
                formatters.add(null);
                renderers.add(null);
            }
        }

        boolean[] checkedIsJson = new boolean[columns.size()];
        List<List<Object>> data = new ObjectArrayList<>(selection.size());
        data.add(new ObjectArrayList<>(columns));
        int rowI = 0;
        for (T obj : selection) {
            List<Object> row = new ObjectArrayList<>(columns.size());
            for (int i = 0; i < formatters.size(); i++) {
                TypedFunction<T, ?> formatter = formatters.get(i);
                if (formatter == null) {
                    row.add(null);
                } else {
                    try {
                        Object td = formatter.apply(obj);
                        if (td != null && td.getClass().isEnum() && isEnum[i]) {
                            row.add(((Enum<?>) td).ordinal());
                        } else {
                            Object serialized = StringMan.toSerializable(td);
                            if (!checkedIsJson[i] && serialized != null) {
                                checkedIsJson[i] = true;
                                if (renderers.get(i) == null) {
                                    if (serialized instanceof Map sMap) {
                                        if (sMap.containsKey("title") && sMap.containsKey("x") && sMap.containsKey("y") && sMap.containsKey("labels") && sMap.containsKey("data")) {
                                            renderers.set(i, "graph");
                                        } else {
                                            renderers.set(i, "json");
                                        }
                                    } else if (serialized instanceof List list) {
                                        renderers.set(i, "json");
                                    }
                                }
                            }
                            row.add(serialized);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        String msg = StringMan.rootMessage(e);
                        if (msg.isEmpty()) {
                            System.out.println("Error with no message for column " + columns.get(i) + " for object " + obj);
                        } else {
                            System.out.println("Error for column " + columns.get(i) + " for object " + obj + ": `" + msg + "`");
                        }
                        List<WebTableError> errList = errors.computeIfAbsent(i, k -> new ObjectArrayList<>(maxPerCol));
                        if (errList.size() < maxPerCol) {
                            errList.add(new WebTableError(i, rowI, StringMan.stripApiKey(msg)));
                        }
                        row.add(null);
                    }
                }
            }
            data.add(row);
            rowI++;
        }
        List<WebTableError> errorsArr = errors.isEmpty() ? null : errors.values().stream().collect(ObjectArrayList::new, List::addAll, List::addAll);
        return new WebTable(data, errorsArr, renderers);
    }
}
