package link.locutus.discord.util.battle;

import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.pnw.Spyop;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.project.Projects;

import java.nio.ByteBuffer;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SpyBlitzGenerator {

    private final Map<DBNation, Double> attList;
    private final Map<DBNation, Double> defList;
    private final Set<SpyCount.Operation> allowedTypes;
    private final int maxDef;
    private final boolean checkEspionageSlots;
    private final Integer minRequiredSpies;
    private final boolean prioritizeKills;
    private boolean forceUpdate;

    private Map<Integer, Double> allianceWeighting = new HashMap<>();

    public SpyBlitzGenerator setAllianceWeighting(DBAlliance alliance, double weight) {
        allianceWeighting.put(alliance.getAlliance_id(), weight);
        return this;
    }

    public SpyBlitzGenerator(Set<DBNation> attackers, Set<DBNation> defenders, Set<SpyCount.Operation> allowedTypes, boolean forceUpdate, int maxDef, boolean checkEspionageSlots, Integer minRequiredSpies, boolean prioritizeKills) {
        this.allowedTypes = allowedTypes;
        this.maxDef = maxDef;
        this.checkEspionageSlots = checkEspionageSlots;
        this.minRequiredSpies = minRequiredSpies;
        this.prioritizeKills = prioritizeKills;
        this.forceUpdate = forceUpdate;

        this.attList = sortNations(attackers, true);
        this.defList = sortNations(defenders, false);
    }

    public Map<DBNation, List<Spyop>> assignTargets() {
        BiFunction<Double, Double, Integer> attRange = PnwUtil.getIsNationsInSpyRange(attList.keySet());
        BiFunction<Double, Double, Integer> defSpyRange = PnwUtil.getIsNationsInSpyRange(defList.keySet());

        BiFunction<Double, Double, Integer> attScoreRange = PnwUtil.getIsNationsInScoreRange(attList.keySet());
        BiFunction<Double, Double, Integer> defScoreRange = PnwUtil.getIsNationsInScoreRange(defList.keySet());

        defList.entrySet().removeIf(n -> attScoreRange.apply(n.getKey().getScore() * 0.75, n.getKey().getScore() / 0.75) == 0);

        BiFunction<Double, Double, Double> attSpyGraph = PnwUtil.getXInRange(attList.keySet(), n -> Math.pow(attList.get(n), 3));
        BiFunction<Double, Double, Double> defSpyGraph = PnwUtil.getXInRange(defList.keySet(), n -> Math.pow(defList.get(n), 3));

        if (forceUpdate) {
            forceUpdate = false;
            List<DBNation> allNations = new ArrayList<>();
            allNations.addAll(attList.keySet());
            allNations.addAll(defList.keySet());

            SimpleNationList list = new SimpleNationList(allNations);
            list.updateSpies(true);
        }

        List<Spyop> ops = new ArrayList<>();

        Set<SpyCount.Operation> allowedOpTypes = new HashSet<>(allowedTypes);

        Function<Double, Double> enemySpyRatio = new Function<Double, Double>() {
            @Override
            public Double apply(Double scoreAttacker) {
                double attSpies = attSpyGraph.apply(scoreAttacker * 0.4, scoreAttacker * 1.5);
                double defSpies = defSpyGraph.apply(scoreAttacker * 0.4, scoreAttacker * 1.5);
                return defSpies / attSpies;
            }
        };

        double spyRatioFactor = 0.1;

        for (Map.Entry<DBNation, Double> entry : attList.entrySet()) {
            DBNation attacker = entry.getKey();
            int mySpies = attacker.getSpies();
            double attValue = entry.getValue();

            Double attWeight = allianceWeighting.get(attacker.getAlliance_id());
            if (attWeight != null && (attacker.getActive_m() < 1440 || attWeight < 1)) {
                attValue *= attWeight;
            }

            if (mySpies <= 1) {

            }

            for (Map.Entry<DBNation, Double> entry2 : defList.entrySet()) {
                DBNation defender = entry2.getKey();
                if (!attacker.isInSpyRange(defender)) continue;

                double defValue = entry2.getValue();

                Double defWeight = allianceWeighting.get(defender.getAlliance_id());
                if (defWeight != null && (defender.getActive_m() < 1440 || defWeight < 1)) {
                    defValue *= defWeight;
                }

                double spyRatio = enemySpyRatio.apply(defender.getScore());
                for (SpyCount.Operation operation : allowedOpTypes) {
                    if (mySpies >= 30 && defender.getSpies() > 9 && operation != SpyCount.Operation.SPIES && allowedOpTypes.contains(SpyCount.Operation.SPIES)) {
                         continue;
                    }
//                    if (defender.getSpies() > Math.min(6, attacker.getSpies() / 3d) && operation != SpyCount.Operation.SPIES && mySpies >= 30) {
//                        continue;
//                    }
                    if (operation.unit == null) continue;
                    if (operation != SpyCount.Operation.SPIES) {
                        int units = defender.getUnits(operation.unit);
                        if (units == 0) continue;
                        switch (operation.unit) {
                            case SOLDIER:
                            case TANK:
                            case AIRCRAFT:
                            case SHIP:
                            case MONEY:
                                if (units * operation.unit.getConvertedCost() * 0.05 < 300000) continue;
                                break;
                        }
                    }
                    SpyCount.Operation[] opTypes = new SpyCount.Operation[]{operation};
                    Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(!prioritizeKills, mySpies, defender, opTypes);

                    if (best == null) continue;

                    Map.Entry<Integer, Double> bestValue = best.getValue();
                    double opNetDamage = bestValue.getValue();

                    opNetDamage *= defValue;

                    if (operation == SpyCount.Operation.SPIES) {
                        opNetDamage = opNetDamage * (1 - spyRatioFactor) + opNetDamage * spyRatio * spyRatioFactor;
                    }
                    if (operation == SpyCount.Operation.NUKE) {
                        if (defender.getNukes() == 1) {
                            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                            int minute = now.getHour() * 60 + now.getMinute();
                            if (minute > 30) {
                                if (defender.getActive_m() < minute) {
                                    continue;
                                }
                            }
                        } else {
                            opNetDamage *= 2;
                        }
                    }
                    if (operation == SpyCount.Operation.MISSILE) {
                        Integer missileCap = defender.hasProject(Projects.SPACE_PROGRAM) ? 2 : 1;
                        if (defender.getMissiles() == missileCap) {
                            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                            int minute = now.getHour() * 60 + now.getMinute();
                            if (minute > 30) {
                                if (defender.getActive_m() < minute) {
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

                    Integer defSpies = defender.updateSpies(false, false);
                    if (defSpies < 48 && operation == SpyCount.Operation.NUKE) defSpies += 3;
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
                return nation.hasProject(Projects.INTELLIGENCE_AGENCY) ? 2 : 1;
            }
        };

        for (Spyop op : ops) {
            List<Spyop> attOps = opsByNations.computeIfAbsent(op.attacker, f -> new ArrayList<>());
            if (attOps.size() >= getNumOps.apply(op.attacker)) {
                continue;
            }
            List<Spyop> defOps = opsAgainstNations.computeIfAbsent(op.defender, f -> new ArrayList<>());
            if (defOps.size() >= maxDef) {
                continue;
            }
            if (defOps.size() > 0) {
                int units = op.defender.getUnits(op.operation.unit);
                for (Spyop other : defOps) {
                    if (other.operation != op.operation) continue;
                    units -= Math.ceil(SpyCount.getKills(other.spies, other.defender, other.operation, other.safety));
                }
                int kills = (int) Math.ceil(SpyCount.getKills(op.spies, op.defender, op.operation, op.safety));
                if (units < kills || kills == 0) continue;

            }

            defOps.add(op);
            attOps.add(op);
        }

        return opsAgainstNations;
    }

    public static double estimateValue(DBNation nation, boolean isAttacker) {
        Integer spies = nation.getSpies();
        String mmrBuilding = nation.getMMRBuildingStr();
        int barracks = (mmrBuilding.charAt(0) - '0');
        int factories = (mmrBuilding.charAt(1) - '0');
        int hangars = (mmrBuilding.charAt(2) - '0');

        double strength = (nation.getAircraftPct() + nation.getTankPct()) / 2d;
        double login = nation.avg_daily_login_week();
        double login_dc = nation.login_daychange();
        Map.Entry<Integer, Integer> allTimeWars = nation.getAllTimeOffDefWars();
        double off_def_ratio = (double) allTimeWars.getKey() / (double) allTimeWars.getKey();
        int totalOff = allTimeWars.getKey();

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
        return perSpyValue;
    }

    private Map<DBNation, Double> sortNations(Collection<DBNation> nations, boolean isAttacker) {
        List<DBNation> list = new ArrayList<>(nations);

        list.removeIf(DBNation::hasUnsetMil);
        list.removeIf(f -> f.getActive_m() > 1440);
        list.removeIf(f -> f.getVm_turns() > 0);
        list.removeIf(f -> f.getSpies() <= 0);
        list.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id);
        if (checkEspionageSlots && !isAttacker) {
            list.removeIf(DBNation::isEspionageFull);
        }
        list.removeIf(f -> f.updateSpies(false, false) == null);

        Map<DBNation, Double> spyValueMap = new LinkedHashMap<>();
        for (DBNation nation : list) {
            double perSpyValue = estimateValue(nation, isAttacker);

            spyValueMap.put(nation, perSpyValue);
        }

        return new SummedMapRankBuilder<>(spyValueMap).sort().get();
    }

    public static Map<DBNation, Set<Spyop>> getTargets(SpreadSheet sheet, int headerRow) {
        return getTargets(sheet, headerRow, true);
    }

    public static Map<DBNation, List<Spyop>> getTargetsTKR(SpreadSheet sheet, boolean groupByAttacker, boolean forceUpdate) {
        List<List<Object>> rows = sheet.get("A:Z", f -> f.setValueRenderOption("FORMULA"));

        List<Spyop> allOps = new ArrayList<>();
        Set<DBNation> update = forceUpdate ? new HashSet<>() : null;

        List<Object> header = rows.get(0);
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.size() < 7) continue;

            DBNation attacker = DiscordUtil.parseNation(row.get(0).toString());

            Spyop op1 = createOp(attacker, row.get(3) + "", "COVERT" + "", update);
            Spyop op2 = createOp(attacker, row.get(6) + "", "COVERT" + "", update);

            if (op1 == null) System.out.println("OP is null");

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

    public static Map<DBNation, List<Spyop>> getTargetsDTC(SpreadSheet sheet, boolean groupByAttacker, boolean forceUpdate) {
        List<List<Object>> rows = sheet.get("A:Z", f -> f.setValueRenderOption("FORMULA"));

        List<Spyop> allOps = new ArrayList<>();
        Set<DBNation> update = forceUpdate ? new HashSet<>() : null;

        System.out.println("Rows " + rows.size());

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

        int nationIndex = 0;
        int headerIndex = -1;
        outer:
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            System.out.println("Row " + i + " | " + StringMan.getString(row));
            if (row.isEmpty()) continue;
            // Leader / Nation
            for (int column = 0; column < row.size(); column++) {
                if (row.get(column) != null && row.get(column).toString().toLowerCase(Locale.ROOT).equalsIgnoreCase("Leader / Nation")) {
                    nationIndex = column;
                    headerIndex = i;
                    break outer;
                }
            }
        }
        if (headerIndex == -1) {
            throw new IllegalArgumentException("No header found containing `Leader / Nation`");
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
                System.out.println("Row incorrect size " + row.size() + " " + row);
                continue;
            }

            System.out.println("Leadername " + row.get(nationIndex));
            String[] leaderName = row.get(nationIndex).toString().split("( / )");
            String nationName = leaderName[leaderName.length - 1];
            DBNation attacker = DiscordUtil.parseNation(nationName);
            if (attacker == null) {
                System.out.println("Attacker is null " + row.get(nationIndex) + " | " + nationName);
                continue;
            }

            String[] targetSplit1 = row.get(SpySlot1Index).toString().split("( / )");
            String[] targetSplit2 = row.get(SpySlot2Index).toString().split("( / )");
            System.out.println("Target 1 " + StringMan.getString(row.get(SpySlot1Index).toString()));
            System.out.println("Target 2 " + StringMan.getString(row.get(SpySlot2Index).toString()));
            Spyop op1 = createOp(attacker, targetSplit1[targetSplit1.length - 1] + "", row.get(OpType1Index) + "", row.get(AttSafety1Index) + "", update);
            Spyop op2 = createOp(attacker, targetSplit2[targetSplit2.length - 1] + "", row.get(OpType2Index) + "", row.get(AttSafety2Index) + "", update);

            if (op1 == null) System.out.println("OP is null " + row.get(SpySlot1Index) + " | " + row.get(OpType1Index) + " | " + row.get(AttSafety1Index));
            if (op2 == null) System.out.println("OP is null " + row.get(SpySlot2Index) + " | " + row.get(OpType2Index) + " | " + row.get(AttSafety2Index));

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

        List<List<Object>> rows = sheet.get("A:Z", f -> f.setValueRenderOption("FORMULA"));

        List<Spyop> allOps = new ArrayList<>();
        Set<DBNation> update = forceUpdate ? new HashSet<>() : null;

        List<Object> header = rows.get(0);
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.size() < 5) continue;

            DBNation attacker = DiscordUtil.parseNation(row.get(0).toString().split("\"")[1]);

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
        DBNation target = DiscordUtil.parseNation(targetStr);
        if (target == null) return null;

        SpyCount.Operation op;
        if (type.contains("spies") || type.toLowerCase(Locale.ROOT).contains("vs spy")) {
            op = SpyCount.Operation.SPIES;
        } else if (type.contains("tank")) {
            op = SpyCount.Operation.TANKS;
        } else if (type.contains("nuke") || type.contains("nuclear")) {
            op = SpyCount.Operation.NUKE;
        } else if (type.contains("missile")) {
            op = SpyCount.Operation.MISSILE;
        } else if (type.contains("soldier")) {
            op = SpyCount.Operation.SOLDIER;
        } else if (type.contains("ship") || type.contains("navy")) {
            op = SpyCount.Operation.SHIPS;
        } else if (type.contains("aircraft") || type.contains("plane")) {
            op = SpyCount.Operation.AIRCRAFT;
        } else if (type.contains("intel")) {
            op = SpyCount.Operation.INTEL;
        } else {
            System.out.println("Invalid op type " + type + " | " + targetStr);
            return null;
        }

        SpyCount.Safety safety = SpyCount.Safety.COVERT;
        safetyStr = safetyStr.toLowerCase();
        if (safetyStr.contains("normal")) {
            safety = SpyCount.Safety.NORMAL;
        } else if (safetyStr.contains("quick")) {
            safety = SpyCount.Safety.QUICK;
        }

        Integer attSpies = att.updateSpies(update != null && !update.contains(att), false);
        Integer defSpies = target.updateSpies(update != null && !update.contains(target), false);
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
        List<List<Object>> rows = sheet.getAll();
        List<Object> header = rows.get(headerRow);

        Integer targetI = null;
        Integer attI = null;
        List<Integer> targetsIndexesRoseFormat = new ArrayList<>();

        boolean isReverse = false;
        for (int i = 0; i < header.size(); i++) {
            Object obj = header.get(i);
            if (obj == null) continue;
            String title = obj.toString();
            if (title.equalsIgnoreCase("nation")) {
                targetI = i;
            }
            if (title.equalsIgnoreCase("att1")) {
                attI = i;
            }
            else if (title.equalsIgnoreCase("def1")) {
                attI = i;
                isReverse = true;
            } else if (title.toLowerCase().startsWith("spy slot ")) {
                targetsIndexesRoseFormat.add(i);
                targetI = 0;
            }
        }

        Map<DBNation, Set<Spyop>> targets = new LinkedHashMap<>();

        for (int i = headerRow + 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.isEmpty() || row.size() <= targetI) {
                continue;
            }

            Object cell = row.get(targetI);
            if (cell == null || cell.toString().isEmpty()) {
                continue;
            }

            String nationStr = cell.toString();
            if (nationStr.contains(" / ")) nationStr = nationStr.split(" / ")[0];
            DBNation nation = DiscordUtil.parseNation(nationStr);

            DBNation attacker = isReverse ? nation : null;
            DBNation defender = !isReverse ? nation : null;

            if (nation == null) {
                continue;
            }

            if (attI != null) {
                for (int j = attI; j < row.size(); j++) {
                    cell = row.get(j);
                    if (cell == null || cell.toString().isEmpty()) continue;

                    String cellStr = cell.toString();
                    String[] split = cellStr.split("\\|");

                    DBNation other = DiscordUtil.parseNation(split[0]);
                    if (other == null) continue;
                    if (isReverse) {
                        defender = other;
                    } else {
                        attacker = other;
                    }

                    SpyCount.Operation opType = SpyCount.Operation.valueOf(split[1]);
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
                    targets.computeIfAbsent(attacker, f -> new LinkedHashSet<>()).add(op);
                }
            } else if (!targetsIndexesRoseFormat.isEmpty()) {
                for (Integer j : targetsIndexesRoseFormat) {
                    cell = row.get(j);
                    if (cell == null || cell.toString().isEmpty()) continue;
                    DBNation other = DiscordUtil.parseNation(cell.toString().split(" / ")[0]);

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

                    SpyCount.Operation type = null;
                    switch (row.get(j + 1).toString().toLowerCase().replace("spy vs ", "").trim()) {
                        case "spy":
                        case "spies":
                            type = SpyCount.Operation.SPIES;
                            break;
                        case "nuke":
                        case "nukes":
                        case "nuclear":
                            type = SpyCount.Operation.NUKE;
                            break;
                        case "tank":
                        case "tanks":
                            type = SpyCount.Operation.TANKS;
                            break;
                        case "aircraft":
                        case "air":
                        case "plane":
                        case "planes":
                            type = SpyCount.Operation.AIRCRAFT;
                            break;
                        case "soldier":
                        case "soldiers":
                            type = SpyCount.Operation.SOLDIER;
                            break;
                        case "ship":
                        case "ships":
                        case "navy":
                        case "naval":
                            type = SpyCount.Operation.SHIPS;
                            break;
                        case "misile":
                        case "misiles":
                            type = SpyCount.Operation.MISSILE;
                            break;
                        case "intel":
                            type = SpyCount.Operation.INTEL;
                            break;
                    }

                    DBNation tmp = attacker;
                    attacker = defender;
                    defender = tmp;

                    Integer spies = attacker.getSpies();
                    if (spies == null) spies = attacker.updateSpies();
                    if (spies == null) spies = 60;
                    Spyop op = new Spyop(attacker, defender, spies, type, 0, safety);
                    if (groupByAttacker) {
                        targets.computeIfAbsent(attacker, f -> new LinkedHashSet<>()).add(op);
                    } else {
                        targets.computeIfAbsent(defender, f -> new LinkedHashSet<>()).add(op);
                    }
                }
            } else {
                throw new IllegalArgumentException("No targets found");
            }
        }
        return targets;
    }
}
