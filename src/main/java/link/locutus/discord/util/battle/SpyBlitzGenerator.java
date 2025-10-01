package link.locutus.discord.util.battle;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.Safety;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.pnw.Spyop;
import link.locutus.discord.util.Operation;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.sheet.SpreadSheet;

import java.nio.ByteBuffer;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class SpyBlitzGenerator {

    private final Map<DBNation, Double> attList;
    private final Map<DBNation, Double> defList;
    private final Set<Operation> allowedTypes;
    private final int maxDef;
    private final boolean checkEspionageSlots;
    private final Integer minRequiredSpies;
    private final boolean prioritizeKills;
    private final Map<Integer, Integer> attSpyCountOverride;
    private boolean forceUpdate;

    private Map<Integer, Double> allianceWeighting = new HashMap<>();
    private TypedFunction<DBNation, Double> attackerPriority;
    private TypedFunction<DBNation, Double> defenderPriority;

    public SpyBlitzGenerator setAllianceWeighting(DBAlliance alliance, double weight) {
        allianceWeighting.put(alliance.getAlliance_id(), weight);
        return this;
    }

    public SpyBlitzGenerator(Set<DBNation> attackers, Set<DBNation> defenders, Set<Operation> allowedTypes, boolean forceUpdate, int maxDef, boolean checkEspionageSlots, Integer minRequiredSpies, boolean prioritizeKills) {
        this.allowedTypes = allowedTypes;
        this.maxDef = maxDef;
        this.checkEspionageSlots = checkEspionageSlots;
        this.minRequiredSpies = minRequiredSpies;
        this.prioritizeKills = prioritizeKills;
        this.forceUpdate = forceUpdate;

        this.attSpyCountOverride = new Int2IntOpenHashMap();

        if (minRequiredSpies != null && minRequiredSpies >= 0) {
            for (DBNation nation : attackers) {
                int spies = nation.getSpies();
                if (spies == 0 && !nation.hasBoughtSpiesToday()) {
                    int dailyBuy = MilitaryUnit.SPIES.getMaxPerDay(nation.getCities(), nation::hasProject, nation::getResearch);
                    attSpyCountOverride.put(nation.getId(), dailyBuy);
                }
            }
        }

        this.attList = sortNations(attackers, true, attSpyCountOverride);
        this.defList = sortNations(defenders, false, attSpyCountOverride);

        System.out.println("Attackers: " + attList.size() + " Defenders: " + defList.size());
    }

    public Map<DBNation, List<Spyop>> assignTargets(boolean isDayChange, Map<DBNation, Integer> subtractOffensiveSlots, Map<DBNation, Integer> subtractDefensiveSlots) {
        BiFunction<Double, Double, Integer> attRange = PW.getIsNationsInSpyRange(attList.keySet());
        BiFunction<Double, Double, Integer> defSpyRange = PW.getIsNationsInSpyRange(defList.keySet());

        BiFunction<Double, Double, Integer> attScoreRange = PW.getIsNationsInScoreRange(attList.keySet());
        BiFunction<Double, Double, Integer> defScoreRange = PW.getIsNationsInScoreRange(defList.keySet());

        defList.entrySet().removeIf(n -> attScoreRange.apply(n.getKey().getScore() * PW.SPY_RANGE_MIN_MODIFIER, n.getKey().getScore() * PW.SPY_RANGE_MAX_MODIFIER) == 0);

        BiFunction<Double, Double, Double> attSpyGraph = PW.getXInRange(attList.keySet(), n -> Math.pow(attList.get(n), 3));
        BiFunction<Double, Double, Double> defSpyGraph = PW.getXInRange(defList.keySet(), n -> Math.pow(defList.get(n), 3));

        if (forceUpdate) {
            forceUpdate = false;
            List<DBNation> allNations = new ArrayList<>();
            allNations.addAll(attList.keySet());
            allNations.addAll(defList.keySet());

            SimpleNationList list = new SimpleNationList(allNations);
            list.updateSpies(true);
        }

        List<Spyop> ops = new ObjectArrayList<>();

        Set<Operation> allowedOpTypes = new HashSet<>(allowedTypes);

        Function<Double, Double> enemySpyRatio = new Function<Double, Double>() {
            @Override
            public Double apply(Double scoreAttacker) {
                double attSpies = attSpyGraph.apply(scoreAttacker * PW.SPY_RANGE_MIN_MODIFIER, scoreAttacker * PW.SPY_RANGE_MAX_MODIFIER);
                double defSpies = defSpyGraph.apply(scoreAttacker * PW.SPY_RANGE_MIN_MODIFIER, scoreAttacker * PW.SPY_RANGE_MAX_MODIFIER);
                return defSpies / attSpies;
            }
        };

        double spyRatioFactor = 0.1;

        for (Map.Entry<DBNation, Double> entry : attList.entrySet()) {
            DBNation attacker = entry.getKey();
            int mySpies = attSpyCountOverride.computeIfAbsent(attacker.getId(), _ -> attacker.getSpies());
//            double attValue = entry.getValue();
//
//            Double attWeight = allianceWeighting.get(attacker.getAlliance_id());
//            if (attWeight != null && (attacker.active_m() < 1440 || attWeight < 1)) {
//                attValue *= attWeight;
//            }

            for (Map.Entry<DBNation, Double> entry2 : defList.entrySet()) {
                DBNation defender = entry2.getKey();
                if (!attacker.isInSpyRange(defender)) continue;

                double defValue = entry2.getValue();

                Double defWeight = allianceWeighting.get(defender.getAlliance_id());
                if (defWeight != null && (defender.active_m() < 1440 || defWeight < 1)) {
                    defValue *= defWeight;
                }

                double spyRatio = enemySpyRatio.apply(defender.getScore());
                for (Operation operation : allowedOpTypes) {
                    if (mySpies >= 30 && defender.getSpies() > 9 && operation != Operation.SPIES && allowedOpTypes.contains(Operation.SPIES)) {
                        continue;
                    }
//                    if (defender.getSpies() > Math.min(6, attacker.getSpies() / 3d) && operation != SpyCount.Operation.SPIES && mySpies >= 30) {
//                        continue;
//                    }
                    if (operation.unit == null) continue;
                    if (operation != Operation.SPIES) {
                        int units = defender.getUnits(operation.unit);
                        if (units == 0) continue;
                        switch (operation.unit) {
                            case SOLDIER:
                            case TANK:
                            case AIRCRAFT:
                            case SHIP:
                            case MONEY:
                                if (units * operation.unit.getConvertedCost(defender.getResearchBits()) * 0.05 < 300000) continue;
                                break;
                        }
                    }
                    Operation[] opTypes = new Operation[]{operation};
                    Map.Entry<Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(!prioritizeKills, mySpies, defender, attacker.hasProject(Projects.SPY_SATELLITE), opTypes);

                    if (best == null) {
                        System.out.println("Best is null for " + attacker.getMarkdownUrl() + " -> " + defender.getMarkdownUrl() + " | " + operation);
                        continue;
                    }

                    Map.Entry<Integer, Double> bestValue = best.getValue();
                    double opNetDamage = bestValue.getValue();

                    opNetDamage *= defValue;

                    if (operation == Operation.SPIES) {
                        opNetDamage = opNetDamage * (1 - spyRatioFactor) + opNetDamage * spyRatio * spyRatioFactor;
                    }
                    if (operation == Operation.NUKE) {
                        int perDay = MilitaryUnit.NUKE.getMaxPerDay(defender.getCities(), defender::hasProject, defender::getResearch);
                        if (defender.getNukes() <= perDay) {
                            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                            int minute = now.getHour() * 60 + now.getMinute();
                            if (minute > 30) {
                                if (defender.active_m() < minute) {
                                    continue;
                                }
                            }
                        } else {
                            opNetDamage *= 2;
                        }
                    }
                    if (operation == Operation.MISSILE) {
                        Integer missileCap = MilitaryUnit.MISSILE.getMaxPerDay(defender.getCities(), defender::hasProject, defender::getResearch);
                        if (defender.getMissiles() == missileCap) {
                            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                            int minute = now.getHour() * 60 + now.getMinute();
                            if (minute > 30) {
                                if (defender.active_m() < minute) {
                                    continue;
                                }
                            }
                        } else {
                            opNetDamage *= 2;
                        }
                    }
                    if (defender.getNukes() > 3) {
                        opNetDamage *= 2;
                    }
                    if (defender.getMissiles() > 3) {
                        opNetDamage *= 1.5;
                    }

                    if (operation.unit != MilitaryUnit.AIRCRAFT && operation.unit != MilitaryUnit.SPIES) opNetDamage /= 2;

                    Integer defSpies = defender.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, false, false);
                    if (defSpies < 48 && operation == Operation.NUKE) defSpies += 3;
                    int safety = bestValue.getKey();
                    int numSpies = (int) Math.ceil(Math.min(mySpies, SpyCount.getRequiredSpies(defSpies, safety, operation, defender)));

                    double odds = SpyCount.getOdds(numSpies, defSpies, safety, operation, defender);
                    if (odds < 50) continue;

                    double opCost = SpyCount.opCost(numSpies, safety);
                    // todo check if they can afford it
                    Spyop spyOp = new Spyop(attacker, defender, numSpies, best.getKey(), opNetDamage, safety);
                    ops.add(spyOp);
                }
            }
        }

        Collections.sort(ops, new Comparator<Spyop>() {
            @Override
            public int compare(Spyop o1, Spyop o2) {
                return Double.compare(o2.netDamage, o1.netDamage);
            }
        });

        Map<DBNation, List<Spyop>> opsAgainstNations = new LinkedHashMap<>();
        Map<DBNation, List<Spyop>> opsByNations = new LinkedHashMap<>();

        Function<DBNation, Integer> getNumOps = new Function<DBNation, Integer>() {
            @Override
            public Integer apply(DBNation nation) {
                int numOps = (nation.hasProject(Projects.INTELLIGENCE_AGENCY) ? 2 : 1);
                if (isDayChange) numOps *= 2;
                if (subtractOffensiveSlots != null) {
                    numOps -= subtractOffensiveSlots.getOrDefault(nation, 0);
                }
                return numOps;
            }
        };

        for (Spyop op : ops) {
            List<Spyop> attOps = opsByNations.computeIfAbsent(op.attacker, f -> new ArrayList<>());
            if (attOps.size() >= getNumOps.apply(op.attacker)) {
                continue;
            }
            List<Spyop> defOps = opsAgainstNations.computeIfAbsent(op.defender, f -> new ArrayList<>());
            int numFree = maxDef - defOps.size();
            if (subtractDefensiveSlots != null) {
                numFree -= subtractDefensiveSlots.getOrDefault(op.defender, 0);
            }
            if (numFree <= 0) {
                System.out.println("Skipping def full " + op.defender.getMarkdownUrl());
                continue;
            }
            if (!defOps.isEmpty()) {
                int units = op.defender.getUnits(op.operation.unit);
                for (Spyop other : defOps) {
                    if (other.operation != op.operation) continue;
                    units -= (int) Math.ceil(SpyCount.getKills(other.spies, other.defender, other.operation, other.attacker.hasProject(Projects.SPY_SATELLITE)));
                }
                int kills = (int) Math.ceil(SpyCount.getKills(op.spies, op.defender, op.operation, op.attacker.hasProject(Projects.SPY_SATELLITE)));
                if (units < kills || kills == 0) {
                    System.out.println("Skipping def no units " + op.defender.getMarkdownUrl() + " | " + op.operation + " | " + units + " < " + kills);
                    continue;
                }

            }

            defOps.add(op);
            attOps.add(op);
        }

        return opsAgainstNations;
    }
    public static double estimateValue(DBNation nation, boolean isAttacker, TypedFunction<DBNation, Double> priority) {
        return estimateValue(nation, nation.getSpies(), isAttacker, priority);
    }
    public static double estimateValue(DBNation nation, int spies, boolean isAttacker, TypedFunction<DBNation, Double> priority) {
        String mmrBuilding = nation.getMMRBuildingStr();
        int barracks = (mmrBuilding.charAt(0) - '0');
        int factories = (mmrBuilding.charAt(1) - '0');
        int hangars = (mmrBuilding.charAt(2) - '0');

        double strength = (nation.getAircraftPct() + nation.getTankPct()) / 2d;
        double login = nation.avg_daily_login_week();
        double login_dc = nation.login_daychange();
        int[] allTimeWars = nation.getAllTimeOffDefWars();
        double off_def_ratio = (double) allTimeWars[0] / (double) allTimeWars[1];
        int totalOff = allTimeWars[0];

        ByteBuffer spyOpsDay = nation.getMeta(NationMeta.SPY_OPS_DAY);

        double mmrBuildValue = 0.5;
        double mmrUnitValue = 0.1;
        double login_value = 1;
        double login_dc_value = 0.1;
        double off_def_ratio_value = 0.2;
        double off_threshold = 20;
        double off_threshold_value = 0.2;
        double ia_value = 0.5;
        double pb_value = 0.05;
        double mlp_value = 0.025;
        double nrf_value = 0.05;
        double spy_sat_value = 0.5;
        double covert_value = 0.15;
        double arcane_value = isAttacker ? 0 : -0.05;
        double off_current_value = 0.4;
        double blockaded_value = -0.1;
        double valueSpyOps = 0.2;
        double valueToday = 0.2;

        double nukeMissileBonus = Math.min(6, (Math.max(nation.getMissiles() + nation.getNukes() - 5, 0) / 10d));

        double perSpyValue = spies >= 6 ? Math.pow(spies, 1) : (3 * strength + nukeMissileBonus);

        if (nation.getOff() > 0) {
            perSpyValue = perSpyValue * (1 + off_current_value);
        }

        if (spyOpsDay != null) {
            perSpyValue = perSpyValue * (1 + valueSpyOps);
            long day = spyOpsDay.getLong();
            if (TimeUtil.getDay() - day <= 2) {
                perSpyValue = perSpyValue * (1 + valueToday);
            }
        }

        if (nation.isBlockaded()) {
            perSpyValue = perSpyValue * (1 + blockaded_value);
        }

        perSpyValue = perSpyValue * (1 - login_value) + perSpyValue * login * login_value;
        perSpyValue = perSpyValue * (1 - login_dc_value) + perSpyValue * login_dc * login_dc_value;

        perSpyValue = perSpyValue * (1 - mmrBuildValue) + (perSpyValue * (barracks / 15d + factories / 15d + hangars / 15d)) * mmrBuildValue;

        perSpyValue = perSpyValue * (1 - mmrUnitValue) + perSpyValue * strength * mmrUnitValue;

        if (totalOff < 50 && off_def_ratio < 2) {
            perSpyValue = perSpyValue * (1 - off_def_ratio_value) + (perSpyValue * (off_def_ratio / 2d)) * off_def_ratio_value;
        }

        if (totalOff < off_threshold) {
            perSpyValue = perSpyValue * (1 - off_threshold_value) + (perSpyValue * (totalOff / off_threshold) * off_threshold_value);
        }

        if (nation.hasProject(Projects.INTELLIGENCE_AGENCY)) {
            perSpyValue *= (1 + ia_value);
        }
        if (nation.hasProject(Projects.PROPAGANDA_BUREAU)) {
            perSpyValue *= (1 + pb_value);
        }
        if (nation.hasProject(Projects.SPY_SATELLITE)) {
            perSpyValue *= (1 + spy_sat_value);
        }
        if (nation.hasProject(Projects.MISSILE_LAUNCH_PAD) && nation.getMissiles() > 2) {
            perSpyValue *= (1 + mlp_value);
        }
        if (nation.hasProject(Projects.NUCLEAR_RESEARCH_FACILITY) && nation.getNukes() > 2) {
            perSpyValue *= (1 + nrf_value);
        }

        if (nation.getWarPolicy() == WarPolicy.COVERT) {
            perSpyValue *= (1 + covert_value);
        }
        if (nation.getWarPolicy() == WarPolicy.ARCANE) {
            perSpyValue *= (1 + arcane_value);
        }

        if (priority != null) {
            Double modifier = priority.apply(nation);
            if (modifier != null) {
                perSpyValue *= modifier;
            }
        }

        return perSpyValue;
    }

    private Map<DBNation, Double> sortNations(Collection<DBNation> nations, boolean isAttacker, Map<Integer, Integer> spyCountOverride) {
        List<DBNation> list = new ArrayList<>(nations);

        list.removeIf(DBNation::hasUnsetMil);
        list.removeIf(f -> f.active_m() > 2880);
        list.removeIf(f -> f.getVm_turns() > 0);
        list.removeIf(f -> spyCountOverride.computeIfAbsent(f.getId(), _ -> f.getSpies()) <= 0);
        list.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id);
        if (checkEspionageSlots && !isAttacker) {
            list.removeIf(DBNation::isEspionageFull);
        }
        list.removeIf(f -> f.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, false, false) == null);

        Map<DBNation, Double> spyValueMap = new LinkedHashMap<>();
        for (DBNation nation : list) {
            int spies = attSpyCountOverride.computeIfAbsent(nation.getId(), _ -> nation.getSpies());
            double perSpyValue = estimateValue(nation, spies, isAttacker, isAttacker ? attackerPriority : defenderPriority);

            spyValueMap.put(nation, perSpyValue);
        }

        return new SummedMapRankBuilder<>(spyValueMap).sort().get();
    }

    public static Map<DBNation, Set<Spyop>> getTargets(SpreadSheet sheet, int headerRow) {
        return getTargets(sheet, headerRow, true);
    }

    public static Map<DBNation, List<Spyop>> getTargetsTKR(SpreadSheet sheet, boolean groupByAttacker, boolean forceUpdate, Consumer<String> warnings) {
        List<List<Object>> rows = sheet.fetchRange(null, "A:Z", f -> f.setValueRenderOption("FORMULA"));

        List<Spyop> allOps = new ArrayList<>();
        Set<DBNation> update = forceUpdate ? new HashSet<>() : null;

        List<Object> header = rows.get(0);
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.size() < 7) continue;

            DBNation attacker = DiscordUtil.parseNation(row.get(0).toString(), true, null);

            Spyop op1 = createOp(attacker, row.get(3) + "", "COVERT", update);
            Spyop op2 = createOp(attacker, row.get(6) + "", "COVERT", update);

            if (op1 == null) {
                warnings.accept("Operation is null " + attacker.getMarkdownUrl() + " | " + row.get(3) + " | " + row.get(6));
            }

            if (op1 != null) allOps.add(op1);
            if (op2 != null) allOps.add(op2);
        }

        Map<DBNation, List<Spyop>> spyOpsFiltered = new LinkedHashMap<>();
        for (Spyop op : allOps) {
            if (groupByAttacker) {
                spyOpsFiltered.computeIfAbsent(op.attacker, f -> new ArrayList<>()).add(op);
            } else {
                spyOpsFiltered.computeIfAbsent(op.defender, f -> new ArrayList<>()).add(op);
            }
        }
        return spyOpsFiltered;
    }

    public static Map<DBNation, List<Spyop>> getTargetsDTC(SpreadSheet sheet, boolean groupByAttacker, boolean forceUpdate, Consumer<String> warnings) {
        List<List<Object>> rows = sheet.fetchRange(null,"A:Z", f -> f.setValueRenderOption("UNFORMATTED_VALUE"));

        List<Spyop> allOps = new ArrayList<>();
        Set<DBNation> update = forceUpdate ? new HashSet<>() : null;

        // Spy Slot 1
        int SpySlot1Index = -1;
        // OpType
        int OpType1Index = -1;
        // Att Safety
        int AttSafety1Index = -1;
        // Spy Slot 2
        int SpySlot2Index = -1;
        // OpType
        int OpType2Index = -1;
        // Att Safety
        int AttSafety2Index = -1;

        int rowsWithValues = 0;
        int nationIndex = 0;
        int headerIndex = -1;
        String lastRowValue = "";
        outer:
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.isEmpty()) continue;
            // Leader / Nation
            for (int column = 0; column < row.size(); column++) {
                Object value = row.get(column);
                if (value != null && !value.toString().isEmpty()) {
                    rowsWithValues++;
                    lastRowValue = value.toString();
                    if (lastRowValue.toLowerCase(Locale.ROOT).equalsIgnoreCase("Leader / Nation")) {
                        nationIndex = column;
                        headerIndex = i;
                        break outer;
                    }
                }
            }
        }
        if (headerIndex == -1) {
            throw new IllegalArgumentException("No header found containing `Leader / Nation`. Found " + rowsWithValues + " rows with values (out of " + rows.size() + ") / " + lastRowValue);
        }

        List<Object> header = rows.get(headerIndex);
        for (int i = 0; i < header.size(); i++) {
            Object cell = header.get(i);
            if (cell == null) continue;
            String cellString = cell.toString().toLowerCase(Locale.ROOT);
            if (cellString.equalsIgnoreCase("Spy Slot 1")) {
                SpySlot1Index = i;
            } else if (cellString.equalsIgnoreCase("OpType") && OpType1Index == -1) {
                OpType1Index = i;
            } else if (cellString.equalsIgnoreCase("Att Safety") && AttSafety1Index == -1) {
                AttSafety1Index = i;
            } else if (cellString.equalsIgnoreCase("Spy Slot 2")) {
                SpySlot2Index = i;
            } else if (cellString.equalsIgnoreCase("OpType") && OpType1Index != -1 && OpType2Index == -1) {
                OpType2Index = i;
            } else if (cellString.equalsIgnoreCase("Att Safety") && AttSafety1Index != -1 && AttSafety2Index == -1) {
                AttSafety2Index = i;
            }
        }

        if (SpySlot1Index == -1) {
            throw new IllegalArgumentException("No header found containing `Spy Slot 1`");
        }
        if (OpType1Index == -1) {
            throw new IllegalArgumentException("No header found containing `OpType`");
        }
        if (AttSafety1Index == -1) {
            throw new IllegalArgumentException("No header found containing `Att Safety`");
        }
        if (SpySlot2Index == -1) {
            throw new IllegalArgumentException("No header found containing `Spy Slot 2`");
        }
        if (OpType2Index == -1) {
            throw new IllegalArgumentException("No header found containing `OpType`");
        }
        if (AttSafety2Index == -1) {
            throw new IllegalArgumentException("No header found containing `Att Safety`");
        }

        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.size() < 9 || row.get(nationIndex) == null) {
                warnings.accept("Row incorrect size " + row.size() + " " + row);
                continue;
            }

            String[] leaderName = row.get(nationIndex).toString().split("( / )");
            String nationName = leaderName[leaderName.length - 1];
            DBNation attacker = null;
            try {
                attacker = DiscordUtil.parseNation(nationName, true, null);
            } catch (IllegalArgumentException e) {
                warnings.accept("Attacker is null (row:" + row.get(nationIndex) + "): " + e.getMessage());
                continue;
            }
            if (attacker == null) {
                warnings.accept("Attacker is null " + row.get(nationIndex) + " | " + nationName);
                continue;
            }

            String[] targetSplit1 = row.get(SpySlot1Index).toString().split("( / )");
            String[] targetSplit2 = row.get(SpySlot2Index).toString().split("( / )");
            Spyop op1 = createOp(attacker, targetSplit1[targetSplit1.length - 1], row.get(OpType1Index) + "", row.get(AttSafety1Index) + "", update);
            Spyop op2 = createOp(attacker, targetSplit2[targetSplit2.length - 1], row.get(OpType2Index) + "", row.get(AttSafety2Index) + "", update);

            List<String> addWarning = null;
            if (op1 == null) {
                (addWarning == null ? addWarning = new ArrayList<>() : addWarning).add("Operation is null " + row.get(SpySlot1Index) + " | " + row.get(OpType1Index) + " | " + row.get(AttSafety1Index));
            }
            if (op2 == null) {
                (addWarning == null ? addWarning = new ArrayList<>() : addWarning).add("Operation is null " + row.get(SpySlot2Index) + " | " + row.get(OpType2Index) + " | " + row.get(AttSafety2Index));
            }

            if (op1 != null) allOps.add(op1);
            if (op2 != null) allOps.add(op2);
        }

        Map<DBNation, List<Spyop>> spyOpsFiltered = new LinkedHashMap<>();
        for (Spyop op : allOps) {
            if (groupByAttacker) {
                spyOpsFiltered.computeIfAbsent(op.attacker, f -> new ArrayList<>()).add(op);
            } else {
                spyOpsFiltered.computeIfAbsent(op.defender, f -> new ArrayList<>()).add(op);
            }
        }
        return spyOpsFiltered;
    }

    public static Map<DBNation, List<Spyop>> getTargetsHidude(SpreadSheet sheet, boolean groupByAttacker, boolean forceUpdate) {

        List<List<Object>> rows = sheet.fetchRange(null,"A:Z", f -> f.setValueRenderOption("FORMULA"));

        List<Spyop> allOps = new ArrayList<>();
        Set<DBNation> update = forceUpdate ? new HashSet<>() : null;

        List<Object> header = rows.get(0);
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.size() < 5) continue;

            DBNation attacker = DiscordUtil.parseNation(row.get(0).toString().split("\"")[1], true, null);

            Spyop op1 = createOp(attacker, row.get(1) + "", row.get(2) + "", update);
            Spyop op2 = createOp(attacker, row.get(3) + "", row.get(4) + "", update);

            if (op1 != null) allOps.add(op1);
            if (op2 != null) allOps.add(op2);
        }

        Map<DBNation, List<Spyop>> spyOpsFiltered = new LinkedHashMap<>();
        for (Spyop op : allOps) {
            if (groupByAttacker) {
                spyOpsFiltered.computeIfAbsent(op.attacker, f -> new ArrayList<>()).add(op);
            } else {
                spyOpsFiltered.computeIfAbsent(op.defender, f -> new ArrayList<>()).add(op);
            }
        }
        return spyOpsFiltered;
    }

    private static Spyop createOp(DBNation att, String typeTarget, String safetyStr, Set<DBNation> update) {
        String[] split = typeTarget.split("\"");
        if (split.length < 4) return null;

        String url = split[1];

        String type = split[3].toLowerCase();
        return createOp(att, url, type, safetyStr, update);
    }

    private static Spyop createOp(DBNation att, String targetStr, String type, String safetyStr, Set<DBNation> update) {
        DBNation target = DiscordUtil.parseNation(targetStr, false, null);
        if (target == null) return null;

        Operation op;
        if (type.contains("spies") || type.toLowerCase(Locale.ROOT).contains("vs spy")) {
            op = Operation.SPIES;
        } else if (type.contains("tank")) {
            op = Operation.TANKS;
        } else if (type.contains("nuke") || type.contains("nuclear")) {
            op = Operation.NUKE;
        } else if (type.contains("missile")) {
            op = Operation.MISSILE;
        } else if (type.contains("soldier")) {
            op = Operation.SOLDIER;
        } else if (type.contains("ship") || type.contains("navy")) {
            op = Operation.SHIPS;
        } else if (type.contains("aircraft") || type.contains("plane")) {
            op = Operation.AIRCRAFT;
        } else if (type.contains("intel")) {
            op = Operation.INTEL;
        } else {
            Logg.text("Invalid op type " + type + " | " + targetStr);
            return null;
        }

        Safety safety = Safety.COVERT;
        safetyStr = safetyStr.toLowerCase();
        if (safetyStr.contains("normal")) {
            safety = Safety.NORMAL;
        } else if (safetyStr.contains("quick")) {
            safety = Safety.QUICK;
        }

        Integer attSpies = att.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, update != null && !update.contains(att), false);
        Integer defSpies = target.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, update != null && !update.contains(target), false);
        if (update != null) {
            update.add(att);
            update.add(target);
        }
        if (attSpies == null || defSpies == null) {
            return null;
        }

        Integer spiesUsed = SpyCount.getRecommendedSpies(attSpies, defSpies, safety.id, op, target);

        Spyop spyOp = new Spyop(att, target, spiesUsed, op, 0d, safety.id);
        return spyOp;
    }

    public static Map<DBNation, Set<Spyop>> getTargets(SpreadSheet sheet, int headerRow, boolean groupByAttacker) {
        List<List<Object>> rows = sheet.fetchAll(null);
        List<Object> header = rows.get(headerRow);

        Integer target1 = null;
        Integer att1 = null;
        Integer att2 = null;
        Integer att3 = null;
        Integer att4 = null;
        Integer att5 = null;
        Integer att6 = null;
        List<Integer> targetsIndexesRoseFormat = new ArrayList<>();

        boolean isReverse = false;
        for (int i = 0; i < header.size(); i++) {
            Object obj = header.get(i);
            if (obj == null) continue;
            String title = obj.toString();
            switch (title.toLowerCase(Locale.ROOT)) {
                // att1,op 1,attacker 1,fighter #1
                case "att1":
                case "op 1":
                case "attacker 1":
                case "fighter #1":
                    att1 = i;
                    break;
                // att2,op 2,attacker 2,fighter #2
                case "att2":
                case "op 2":
                case "attacker 2":
                case "fighter #2":
                    att2 = i;
                    break;
                // att3,op 3,attacker 3,fighter #3
                case "att3":
                case "op 3":
                case "attacker 3":
                case "fighter #3":
                    att3 = i;
                    break;
                // att4,op 4,attacker 4,fighter #4
                case "att4":
                case "op 4":
                case "attacker 4":
                case "fighter #4":
                    att4 = i;
                    break;
                // 5
                case "att5":
                case "op 5":
                case "attacker 5":
                case "fighter #5":
                    att5 = i;
                    break;
                // 6
                case "att6":
                case "op 6":
                case "attacker 6":
                case "fighter #6":
                    att6 = i;
                    break;
                case "nation":
                    target1 = i;
                    break;
                case "def1":
                case "target 1":
                case "defender 1":
                case "target #1":
                    att1 = i;
                    isReverse = true;
                    break;
                case "spy slot 1":
                    targetsIndexesRoseFormat.add(i);
                    target1 = 0;
                    break;
                default:
            }
            if (title.equalsIgnoreCase("nation")) {
                target1 = i;
            }
        }

        Map<DBNation, Set<Spyop>> targets = new LinkedHashMap<>();

        for (int i = headerRow + 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.isEmpty()) {
                continue;
            }

            Object cell = row.get(target1);
            if (cell == null || cell.toString().isEmpty()) {
                continue;
            }

            String nationStr = cell.toString();
            if (nationStr.contains(" / ")) nationStr = nationStr.split(" / ")[0];
            DBNation nation = DiscordUtil.parseNation(nationStr, false, null);

            DBNation attacker = isReverse ? nation : null;
            DBNation defender = !isReverse ? nation : null;

            if (nation == null) {
                continue;
            }

            if (att1 != null) {
                Integer[] indexes = new Integer[]{att1, att2, att3, att4, att5, att6};
                for (Integer j : indexes) {
                    if (j == null || row.size() <= j) continue;
                    cell = row.get(j);
                    if (cell == null || cell.toString().isEmpty()) continue;

                    String cellStr = cell.toString();
                    String[] split = cellStr.split("\\|");

                    DBNation other = DiscordUtil.parseNation(split[0], false, null);
                    if (other == null) continue;
                    if (isReverse) {
                        defender = other;
                    } else {
                        attacker = other;
                    }

                    Operation opType = Operation.valueOf(split[1]);
                    int spies = Integer.parseInt(split[3]);
                    int safety = 3;
                    switch (split[2].toLowerCase()) {
                        case "quick":
                            safety = 1;
                            break;
                        case "normal":
                            safety = 2;
                            break;
                        case "covert":
                            safety = 3;
                            break;
                    }
                    Spyop op = new Spyop(attacker, defender, spies, opType, 0, safety);
                    targets.computeIfAbsent(attacker, f -> new ObjectLinkedOpenHashSet<>()).add(op);
                }
            } else if (!targetsIndexesRoseFormat.isEmpty()) {
                for (Integer j : targetsIndexesRoseFormat) {
                    cell = row.get(j);
                    if (cell == null || cell.toString().isEmpty()) continue;
                    DBNation other = DiscordUtil.parseNation(cell.toString().split(" / ")[0], false, null);

                    if (other == null) continue;
                    if (isReverse) {
                        defender = other;
                    } else {
                        attacker = other;
                    }

                    int safety = 0;

                    switch (row.get(j + 2).toString().toLowerCase()) {
                        case "quick":
                            safety = 1;
                            break;
                        case "normal":
                            safety = 2;
                            break;
                        case "covert":
                            safety = 3;
                            break;
                    }

                    Operation type = null;
                    switch (row.get(j + 1).toString().toLowerCase().replace("spy vs ", "").trim()) {
                        case "spy":
                        case "spies":
                            type = Operation.SPIES;
                            break;
                        case "nuke":
                        case "nukes":
                        case "nuclear":
                            type = Operation.NUKE;
                            break;
                        case "tank":
                        case "tanks":
                            type = Operation.TANKS;
                            break;
                        case "aircraft":
                        case "air":
                        case "plane":
                        case "planes":
                            type = Operation.AIRCRAFT;
                            break;
                        case "soldier":
                        case "soldiers":
                            type = Operation.SOLDIER;
                            break;
                        case "ship":
                        case "ships":
                        case "navy":
                        case "naval":
                            type = Operation.SHIPS;
                            break;
                        case "misile":
                        case "misiles":
                            type = Operation.MISSILE;
                            break;
                        case "intel":
                            type = Operation.INTEL;
                            break;
                    }

                    DBNation tmp = attacker;
                    attacker = defender;
                    defender = tmp;

                    Integer spies = attacker.getSpies();
                    if (spies == null) spies = attacker.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK);
                    if (spies == null) spies = 60;
                    Spyop op = new Spyop(attacker, defender, spies, type, 0, safety);
                    if (groupByAttacker) {
                        targets.computeIfAbsent(attacker, f -> new ObjectLinkedOpenHashSet<>()).add(op);
                    } else {
                        targets.computeIfAbsent(defender, f -> new ObjectLinkedOpenHashSet<>()).add(op);
                    }
                }
            } else {
                throw new IllegalArgumentException("No targets found");
            }
        }
        return targets;
    }

    public void setAttackerWeighting(TypedFunction<DBNation, Double> weighting) {
        this.attackerPriority = weighting;
    }

    public void setDefenderWeighting(TypedFunction<DBNation, Double> defenderWeighting) {
        this.defenderPriority = defenderWeighting;
    }
}
