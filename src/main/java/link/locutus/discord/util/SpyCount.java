package link.locutus.discord.util;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.LootEntry;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.game.SpyReportEvent;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.io.PagePriority;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SpyCount {
    private static final List<Map.Entry<Double, Map.Entry<Operation, Integer>>> BY_SUCCESS = new ArrayList<>();
    private static final Map<Integer, Integer> DISTR;
    private static final Map<Integer, Integer> OP_DISTR;
    static int DEF_SPIES = 37;
    static int DEF_OP = 9;

    static {
        List<Operation> bySuccess = Arrays.asList(Operation.INTEL, Operation.TANKS, Operation.AIRCRAFT, Operation.SHIPS, Operation.MISSILE, Operation.NUKE);
        for (int i = bySuccess.size() - 1; i >= 0; i--) {
            Operation opType = bySuccess.get(i);
            outer:
            for (int safety = 1; safety <= 3; safety++) {
                double success = ((50d * opType.odds) - (safety * 25d));
                for (Map.Entry<Double, Map.Entry<Operation, Integer>> entry : BY_SUCCESS) {
                    if (entry.getKey() == success) continue outer;
                }
                BY_SUCCESS.add(new AbstractMap.SimpleEntry<>(success, new AbstractMap.SimpleEntry<>(opType, safety)));
            }
        }

        Collections.sort(BY_SUCCESS, Comparator.comparingDouble(Map.Entry::getKey));

        // TODO, might be best to generate this instead of having it hardcoded
        // This is a weighted binary tree, to reduce the number of requests done in the binary search
        // Since nations are more likely to have e.g. 0 spies, or 50/60 you can favor those branches first
        // The code (not here) also favors branches starting at their previous spy count
        // (because they are likely to have the same, or at least similar spy count when checking again)
        DISTR = new HashMap<>();
        DISTR.put(0, 401);
        DISTR.put(1, 23);
        DISTR.put(2, 100);
        DISTR.put(3, 42);
        DISTR.put(4, 61);
        DISTR.put(5, 25);
        DISTR.put(6, 58);
        DISTR.put(7, 30);
        DISTR.put(8, 32);
        DISTR.put(9, 21);
        DISTR.put(10, 29);
        DISTR.put(11, 18);
        DISTR.put(12, 32);
        DISTR.put(13, 19);
        DISTR.put(14, 15);
        DISTR.put(15, 19);
        DISTR.put(16, 16);
        DISTR.put(17, 11);
        DISTR.put(18, 21);
        DISTR.put(19, 8);
        DISTR.put(20, 16);
        DISTR.put(21, 12);
        DISTR.put(22, 15);
        DISTR.put(23, 7);
        DISTR.put(24, 12);
        DISTR.put(25, 7);
        DISTR.put(26, 15);
        DISTR.put(27, 9);
        DISTR.put(28, 11);
        DISTR.put(29, 3);
        DISTR.put(30, 13);
        DISTR.put(31, 8);
        DISTR.put(32, 18);
        DISTR.put(33, 19);
        DISTR.put(35, 7);
        DISTR.put(36, 28);
        DISTR.put(37, 16);
        DISTR.put(39, 4);
        DISTR.put(40, 11);
        DISTR.put(41, 3);
        DISTR.put(43, 8);
        DISTR.put(44, 55);
        DISTR.put(45, 11);
        DISTR.put(47, 1);
        DISTR.put(48, 7);
        DISTR.put(49, 117);
        DISTR.put(51, 2);
        DISTR.put(52, 4);
        DISTR.put(53, 5);
        DISTR.put(55, 4);
        DISTR.put(56, 1);
        DISTR.put(57, 9);
        DISTR.put(59, 5);
        DISTR.put(60, 163);

        OP_DISTR = new HashMap<>();
        OP_DISTR.put(2, 406);
        OP_DISTR.put(3, 136);
        OP_DISTR.put(4, 84);
        OP_DISTR.put(5, 56);
        OP_DISTR.put(6, 53);
        OP_DISTR.put(7, 32);
        OP_DISTR.put(8, 47);
        OP_DISTR.put(9, 21);
        OP_DISTR.put(10, 772);
    }

    public static boolean isInScoreRange(double attackerScore, double defenderScore) {
        double min = attackerScore * 0.4;
        double max = attackerScore * 2.5;
        if (defenderScore < min || defenderScore > max) {
//            if (other.getRank() < getRank() || other.getRank() >= getRank() + 10)
            {
                return false;
            }
        }
        return true;
    }

    public static Map.Entry<Integer, Integer> getSpiesUsedRange(int spiesKilled, int defSpies, boolean spySat) {
        double factor = spySat ? 1.5 : 1;
        long min = Math.round(spiesKilled / (1.05 * 0.335 * factor) + (defSpies * 0.4));
        long max = Math.round(spiesKilled / (0.85 * 0.335 * factor) + (defSpies * 0.4));
        return new AbstractMap.SimpleEntry<>((int) min, (int) max);
    }

    public static Map.Entry<Integer, Integer> getUnitKillRange(int attSpies, int defSpies, MilitaryUnit unit, int defUnits, boolean spySat, boolean defSN) {
        double min;
        double max;
        double factor = defSN ? 0.75 : 1;
        if (spySat) factor *= 1.5;
        if (unit == MilitaryUnit.SPIES) {
            double average =  ((double) attSpies - (defSpies * 0.4)) * 0.335;
            double cap = (defSpies * 0.25) + 4;
            min = Math.min(cap, average * 0.85) * factor;
            max = Math.min(cap, average * 1.05) * factor;
        } else {
            switch (unit) {
                case SOLDIER:
                case TANK:
                case AIRCRAFT:
                case SHIP:
                    min = (double) defUnits * 0.01 * factor;
                    max = (double) defUnits * 0.05 * factor;
                    break;
                case MISSILE:
                case NUKE:
                    min = 1;
                    max = 1;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown unit type: " + unit);
            }
        }
        return new AbstractMap.SimpleEntry<>((int) Math.round(min), (int) Math.round(max));
    }

    public static int guessSpyCount(PagePriority priority, DBNation nation) throws IOException {
        int current = nation.getSpies();

        int id = nation.getNation_id();
        int index = binarySearchOpType(priority, id, 0, BY_SUCCESS.size() - 1, DEF_OP);
        Map.Entry<Double, Map.Entry<Operation, Integer>> pair = BY_SUCCESS.get(index);
        Map.Entry<Operation, Integer> opSafety = pair.getValue();
        Operation opType = opSafety.getKey();
        int safety = opSafety.getValue();

        int def = DEF_SPIES;
        if (nation.getSpies() != -1) def = nation.getSpies();

        int inversionPoint = binarySearchSpies(priority, id, opType, safety, 0, 59, def);

        boolean arcane = nation.getWarPolicy() == WarPolicy.ARCANE;
        boolean tactician = nation.getWarPolicy() == WarPolicy.TACTICIAN;
        double chi = 1;

        if (arcane) chi -= 0.15;
        if (tactician) chi += 0.15;

        double enemySpiesMin = (Math.max(Math.min((( (100d*inversionPoint) / ((50d * opType.odds)/chi-25*safety) ) - 1 )/3d,60d),0));

        int result;
        if (enemySpiesMin > 61) {
            result = 60;
        }
         else if (enemySpiesMin < 0) {
            result =  0;
        }
        else {
            result = enemySpiesMin > 2 ? (int) Math.ceil(enemySpiesMin) : (int) enemySpiesMin;
        }
        if (current != result) {
            nation.setSpies(result, Event::post);
        }
        nation.setMeta(NationMeta.UPDATE_SPIES, TimeUtil.getTurn());
        return result;
    }

    public static Map.Entry<SpyCount.SpyOp, Integer> estimateSpiesUsed(long change, int minSpies) {
        if (change >= 0) return null;
        int max = (int) (1 + SpyCount.opCost(60, 3));
        long changeAbs = Math.abs(change);
        if (change < 0 && changeAbs <= max && minSpies > 0) {
            int maxSpies = Math.min(60, minSpies + 2);

            for (int spies = minSpies; spies <= maxSpies * 3; spies++) {
                for (int safety = 1; safety <= 3; safety++) {
                    double cost = SpyCount.opCost(spies, safety);
                    double diff = Math.abs(cost - changeAbs);
                    if (diff < 2) {
                        int uncertainty = spies > maxSpies ? 3 : 0;
                        return new AbstractMap.SimpleEntry<>(new SpyCount.SpyOp(null, spies, safety), uncertainty);
                    }
                }
            }
            for (int spies = 1; spies < minSpies; spies++) {
                for (int safety = 1; safety <= 3; safety++) {
                    double cost = SpyCount.opCost(spies, safety);
                    double diff = Math.abs(cost - changeAbs);
                    if (diff < 2) {
                        return new AbstractMap.SimpleEntry<>(new SpyCount.SpyOp(null, spies, safety), 2);
                    }
                }
            }

            for (int spies = 1; spies < maxSpies; spies++) {
                for (int safety = 1; safety <= 3; safety++) {
                    double cost = SpyCount.opCost(spies, safety);
                    double diff = Math.abs(cost - changeAbs);
                    if (diff - 50000 < 2 || diff - 150000 < 2 || diff - 200000 < 2) {
                        return new AbstractMap.SimpleEntry<>(new SpyCount.SpyOp(null, spies, safety), 4);
                    }
                }
            }
        }
        return null;
    }

    public static double opCost(int spies, int safety) {
        return (spies * 3500 * Math.pow(safety, 1.5));
    }

    public static int getBestDistr(Map<Integer, Integer> map, int min, int max) {
        int total = 0;
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getKey() >= min && entry.getKey() <= max) {
                total += entry.getValue();
            }
        }

        int current = 0;
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getKey() >= min && entry.getKey() <= max) {
                current += entry.getValue();
                if (current > total / 2) {
                    return entry.getKey();
                }
            }
        }
        return -1;
    }

    private static int binarySearchOpType(PagePriority priority, int id, int min, int max, int def) throws IOException {
        if (min >= max) {
            return max;
        }
        if (def == -1) {
            def = getBestDistr(OP_DISTR, min, max); // can be the midpoint, or based on their previous spy count etc. closer to the actual count the better
        }

        int index;
        if (def > min && def <= max) {
            index = def;
        } else {
            index = (int) Math.ceil((max + min) / 2d);
        }

        Map.Entry<Double, Map.Entry<Operation, Integer>> pair = BY_SUCCESS.get(index);
        Double factor = pair.getKey();
        Map.Entry<Operation, Integer> opSafety = pair.getValue();
        Operation opType = opSafety.getKey();
        int safety = opSafety.getValue();

        boolean isGreater = checkSpiesGreater(priority, id, opType, safety, 60);
        if (!isGreater) {
            max = index - 1;
        } else {
            min = index;
        }
        return binarySearchOpType(priority, id, min, max, -1);
    }

    public static Map.Entry<DBNation, double[]> parseSpyReport(String input) {
        return parseSpyReport(null, input);
    }

    public static Map.Entry<DBNation, double[]> parseSpyReport(DBNation reportBy, String input) {
        Map.Entry<DBNation, double[]> entry = ResourceType.parseIntelRss(input, null);
        DBNation target = entry != null ? entry.getKey() : null;
        double[] loot = entry != null ? entry.getValue() : null;

        if (reportBy != null) {
            int numOps = StringUtils.countMatches(input, "of your spies were captured and executed.");
            if (entry != null) numOps = Math.max(1, numOps);

            if (entry != null && ResourceType.convertedTotal(entry.getValue()) > 12_000_000_000d) {
                AlertUtil.error("Invalid spy report", "reported by: " + reportBy + "\n\n`" + input +"`");
                return entry;
            }

            try {
                long lastOpDay = 0;
                long currentDay = TimeUtil.getDay();
//                if (numOps != 0) {
//                    ByteBuffer lastSpyOpDayBuf = reportBy.getMeta(NationMeta.SPY_OPS_DAY);
//                    lastOpDay = lastSpyOpDayBuf == null ? 0L : lastSpyOpDayBuf.getLong();
//                    if (lastOpDay != currentDay) {
//                        reportBy.setMeta(NationMeta.SPY_OPS_DAY, currentDay);
//                    }
//
//                    int amt = 0;
//                    if (currentDay == lastOpDay) {
//                        ByteBuffer dailyOpAmt = reportBy.getMeta(NationMeta.SPY_OPS_AMOUNT_DAY);
//                        amt = dailyOpAmt == null ? 0 : dailyOpAmt.getInt();
//                    }
//                    amt++;
//                    reportBy.setMeta(NationMeta.SPY_OPS_AMOUNT_DAY, amt);
//
//                    if (amt <= 2) {
//                        ByteBuffer totalOpAmtBuf = reportBy.getMeta(NationMeta.SPY_OPS_AMOUNT_TOTAL);
//                        int total = totalOpAmtBuf == null ? 0 : totalOpAmtBuf.getInt();
//                        reportBy.setMeta(NationMeta.SPY_OPS_AMOUNT_TOTAL, total + 1);
//                    }
//                    new SpyReportEvent(reportBy, target, loot, lastOpDay, amt).post();
//                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        if (entry == null) {
            return null;
        }

        DBNation nation = entry.getKey();

        if (reportBy != null) {
            LootEntry previous = Locutus.imp().getNationDB().getLoot(nation.getNation_id());
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);

            if (previous == null || previous.getDate() > cutoff) {
//                if (last < currentDay) {
//                    if (nation.getPosition() > 1) {
////                        GuildDB db = nation.getGuildDB();
////                        nation.addBalance(db, ResourceType.MONEY, 19000, "#spyop");
////                        db.flush();
////                        nation.sendDM("Thanks for doing daily spy op. 36k has been added to your account");
//                    }
//                }
            }
        }

        if (nation != null) {
            Locutus.imp().runEventsAsync(events -> LootEntry.forNation(nation.getNation_id(), System.currentTimeMillis(), entry.getValue(), NationLootType.ESPIONAGE).save(events));
        }
        return entry;
    }

    public static int binarySearchSpies(PagePriority priority, int id, Operation optype, int safety, int min, int max, int def) throws IOException {
        if (min >= max) {
            return min;
        }

        if (def == -1) {
            def = getBestDistr(DISTR, min, max);
        }

        int val;
        if (def > min && def <= max) {
            val = def;
        } else {
            val = (int) Math.ceil((max + min) / 2d);
        }
        boolean isGreater = checkSpiesGreater(priority, id, optype, safety, val);
        if (isGreater) {
            max = val - 1;
        } else {
            min = val;
        }
        return binarySearchSpies(priority, id, optype, safety, min, max, -1);
    }

    public static boolean checkSpiesGreater(PagePriority priority, int id, Operation optype, int safety, int spies) throws IOException {
        String url = "" + Settings.INSTANCE.PNW_URL() + "/war/espionage_get_odds.php?id1=%s&id2=%s&id3=%s&id4=%s&id5=%s";
        url = String.format(url, Locutus.loader().getNationId(), id, optype.ordinal(), safety, spies);
        String result = FileUtil.readStringFromURL(priority, url).trim();
        return result.startsWith("Greater");
    }

    public static class SpyOp {
        public SpyCount.Operation operation;
        public int spies;
        public int safety;

        public SpyOp(Operation operation, int spies, int safety) {
            this.operation = operation;
            this.spies = spies;
            this.safety = safety;
        }
    }

    public static enum Safety {
        QUICK(1, 1),
        NORMAL(2, 2.8284271428571428571428571428571),
        COVERT(3, 5.1961524285714285714285714285714d);

        public final int id;
        private final double costFactor;

        Safety(int id, double costFactor) {
            this.id = id;
            this.costFactor = costFactor;
        }

        public double getCostFactor() {
            return costFactor;
        }

        public static Safety byId(int safety) {
            switch (safety) {
                case 1: return QUICK;
                case 2: return NORMAL;
                case 3: return COVERT;
            }
            return null;
        }
    }

    public enum Operation {
        INTEL(1, 1, null),
        NUKE(5, 18.74971745, MilitaryUnit.NUKE),
        MISSILE(4, 12.49979948, MilitaryUnit.MISSILE),
        SHIPS(3, 8.437383791, MilitaryUnit.SHIP),
        AIRCRAFT(2, 4.999927084, MilitaryUnit.AIRCRAFT),
        TANKS(1.5, 2.343723796, MilitaryUnit.TANK),
        SPIES(1.5, 2.812461264, MilitaryUnit.SPIES),
        SOLDIER(1, 1.249990886, MilitaryUnit.SOLDIER)
        ;

        public final double odds;
        public final double lossFactor;
        public final MilitaryUnit unit;

        Operation(double odds, double lossFactor, MilitaryUnit unit) {
            this.odds = odds;
            this.lossFactor = lossFactor;
            this.unit = unit;
        }

        public static final Operation[] values = values();

        public static Operation getByUnit(MilitaryUnit unit) {
            for (Operation op : values) {
                if (op.unit == unit) {
                    return op;
                }
            }
            return null;
        }
    }

    private static double[][] LOSSES_DEF_LOWER = {
            {-4.09705066790786000000E-41, 7.91349892413586000000E-33, - 5.99467105946312000000E-25, 2.45013288442541000000E-17, - 8.55274841509053000000E-10, 4.93157152593540000000E-02},
            {-1.29028502600168000000E-41, 3.23242888819472000000E-33, - 3.54948610068505000000E-25, 2.35556384837777000000E-17, - 1.14328991822235000000E-09,  4.78061187628782000000E-02},
            {-2.83623321494883000000E-41, 6.82834480966284000000E-33, - 6.97239529274830000000E-25, 4.05430130210287000000E-17, - 1.57028904023684000000E-09, 4.71921401617692000000E-02}
    };
    private static double[][] LOSSES_ATT_LOWER = {
            {-1.54595815194488000000E-41, 3.77900016173239000000E-33, - 4.07852200903932000000E-25, 2.72382185210400000000E-17, - 1.39007036035945000000E-09, 6.39160790760798000000E-02},
            {-8.40127373030099000000E-43, 2.48165463473599000000E-34, - 3.62239790740844000000E-26, 3.81253293230948000000E-18, - 3.54330939020047000000E-10, 3.19961095675581000000E-02},
            {-1.26936184447289000000E-43, 4.32535099348858000000E-35, - 7.94686583302241000000E-27, 1.15694542510054000000E-18, - 1.57900889805333000000E-10, 2.13329337763071000000E-02}
    };

    /**
     * Get the spies lost if an operation fails
     */
    public static double getFailedSpyLosses(int attSpies, int defSpies, Operation operation, int safety) {
        if (safety < 1 || safety > 3) {
            throw new IllegalArgumentException("Invalid safety level: " + safety + ". Must be in range: [1,3]");
        }
        if (attSpies == 0 || defSpies == 0) {
            return 0;
        }
        double factor;
        double x;
        double[] formula;
        if (defSpies < attSpies) {
            x = ((double) defSpies / attSpies) * 60000000d;
            factor = (1 / 60000000d) * ((double) defSpies / attSpies) * defSpies;
            formula = LOSSES_DEF_LOWER[safety - 1];
        } else {
            x = ((double) attSpies / defSpies) * 60000000d;
            factor = (1 / 60000000d) * ((double) attSpies / defSpies) * attSpies;
            formula = LOSSES_ATT_LOWER[safety - 1];
        }
        double killed = 0;
        for (int i = 0; i < 6; i++) {
            int pow = 6 - i;
            killed += Math.pow(x, pow) * formula[i];
        }

        return killed * factor * operation.lossFactor;
    }

    public static double getOdds(int attacking, int defending, int safety, Operation operation, DBNation nation) {
        boolean arcane = nation.getWarPolicy() == WarPolicy.ARCANE;
        boolean tactician = nation.getWarPolicy() == WarPolicy.TACTICIAN;
        boolean defenderHasSN = nation.hasProject(Projects.SURVEILLANCE_NETWORK);
        return getOdds(attacking, defending, safety, operation, arcane, tactician, defenderHasSN);
    }

    public static double getOdds(int attacking, int defending, int safety, Operation operation, boolean arcane, boolean tactician, boolean defenderHasSN) {
        double chi = 1;
        if (arcane) chi -= 0.15;
        if (tactician) chi += 0.15;
        if (defenderHasSN) chi -= 0.1;

        return Math.min(100, Math.max(0, chi * (safety * 25 + (attacking * 100d / ((defending * 3) + 1))) / operation.odds));
    }

    public static double getRequiredSpies(int defending, int safety, Operation operation, DBNation nation) {
        boolean arcane = nation.getWarPolicy() == WarPolicy.ARCANE;
        boolean tactician = nation.getWarPolicy() == WarPolicy.TACTICIAN;
        boolean defHasSN = nation.hasProject(Projects.SURVEILLANCE_NETWORK);
        double chi = 1;
        if (arcane) chi -= 0.15;
        if (tactician) chi += 0.15;
        if (defHasSN) chi -= 0.1;
        return (((defending * 3) + 1) * (operation.odds * (100 / chi) - safety * 25)) / (100d);
    }

    public static int getRecommendedSpies(int attacking, int defending, int safety, Operation operation, DBNation nation) {
        return (int) Math.min(Math.ceil(getRequiredSpies(defending, safety, operation, nation)), attacking);
    }

    public static double getCostPerSpy(int safety) {
        switch (safety) {
            default:
            case 1:
                return 5000;
            case 2:
                return 14142.13562373d;
            case 3:
                return 25980.76211353d;
        }
    }

    /**
     *
     * @param attacking
     * @param defender
     * @return (Operation, (Safety, Net Damage))
     */
    public static Map.Entry<Operation, Map.Entry<Integer, Double>> getBestOp(int attacking, DBNation defender, boolean attackerHasSpySat) {
        return getBestOp(attacking, defender, attackerHasSpySat, Operation.values());
    }

    public static Map.Entry<Operation, Map.Entry<Integer, Double>> getBestOp(int attacking, DBNation defender, boolean attackerHasSpySat, Operation... opTypes) {
        return getBestOp(attacking, defender, 1, 3, attackerHasSpySat, opTypes);
    }

    public static Map.Entry<Operation, Map.Entry<Integer, Double>> getBestOp(boolean useNet, int attacking, DBNation defender, boolean attackerHasSpySat, Operation... opTypes) {
        return getBestOp(useNet, attacking, defender, 1, 3, attackerHasSpySat, opTypes);
    }

    public static Map.Entry<Operation, Map.Entry<Integer, Double>> getBestOp(int attacking, DBNation defender, int minSafety, int maxSafety, boolean attackerHasSpySat, Operation... opTypes) {
        return getBestOp(true, attacking, defender, minSafety, maxSafety, attackerHasSpySat, opTypes);
    }

    public static Map.Entry<Operation, Map.Entry<Integer, Double>> getBestOp(boolean useNet, int attacking, DBNation defender, int minSafety, int maxSafety, boolean attackerHasSpySat, Operation... opTypes) {
        return getBestOp(useNet, true, attacking, defender, minSafety, maxSafety, attackerHasSpySat, opTypes);
    }

    public static Map.Entry<Operation, Map.Entry<Integer, Double>> getBestOp(boolean useNet, boolean checkUnits, int attacking, DBNation defender, int minSafety, int maxSafety, boolean attackerHasSpySat, Operation... opTypes) {
        Operation maxOp = null;
        int bestSafety = 1;
        double max = Double.NEGATIVE_INFINITY;

        for (int safety = minSafety; safety <= maxSafety; safety++) {
            for (Operation operation : opTypes) {
                if (operation.unit == null) continue;
                if (checkUnits && defender.getUnits(operation.unit) == 0) continue;

                int spiesUsed = attacking;
                if (operation != Operation.SPIES) {
                    spiesUsed = getRecommendedSpies(attacking, defender.getSpies(), safety, operation, defender);
                }

                double odds = getOdds(attacking, defender.getSpies(), safety, operation, defender);
                if (odds < 90 && safety < 3) {
                    continue;
                }

                double netDamage;
                if (useNet) netDamage = getNetDamage(spiesUsed, defender, operation, safety, operation != Operation.SPIES, attackerHasSpySat);
                else netDamage = getDamage(spiesUsed, defender, operation, safety, attackerHasSpySat);

                if (netDamage > max) {
                    max = netDamage;
                    maxOp = operation;
                    bestSafety = safety;
                }
            }
        }
        if (maxOp == null) return null;
        return new AbstractMap.SimpleEntry<>(maxOp, new AbstractMap.SimpleEntry<>(bestSafety, max));
    }

    public static double getNetDamage(int attacking, DBNation defender, Operation operation, int safety, boolean countOpCost, boolean attackerHasSpySat) {
        double net = getNetSpyKills(attacking, defender.getSpies(), operation, safety, defender);
        double netDamage = net * MilitaryUnit.SPIES.getConvertedCost();

        double odds = getOdds(attacking, defender.getSpies(), safety, operation, defender);
        if (operation != Operation.SPIES) {
            double kills = getKills(attacking, defender, operation, attackerHasSpySat) * (odds / 100d);
            netDamage += kills * operation.unit.getConvertedCost();
        }
        if (countOpCost) {
            netDamage -= getCostPerSpy(safety) * attacking;
        }
        return netDamage;
    }

    public static double getDamage(int attacking, DBNation defender, Operation operation, int safety, boolean attackerHasSpySat) {
        double odds = getOdds(attacking, defender.getSpies(), safety, operation, defender);
        double losses = getFailedSpyLosses(attacking, defender.getSpies(), operation, safety);
        double kills = operation == Operation.SPIES ? getSpyKills(attacking, defender.getSpies(), defender.hasProject(Projects.SURVEILLANCE_NETWORK)) : 0;

        double netDamage = kills * MilitaryUnit.SPIES.getConvertedCost();

        if (operation != Operation.SPIES) {
            kills = getKills(attacking, defender, operation, attackerHasSpySat) * (odds / 100d);
            netDamage = kills * operation.unit.getConvertedCost();
        }
        return netDamage;
    }

    public static double getKills(int attacking, DBNation defender, Operation operation, boolean attackerHasSS) {
        double factor = defender.hasProject(Projects.SURVEILLANCE_NETWORK) ? 0.75 : 1;
        if (attackerHasSS) factor *= 1.5;
        switch (operation) {
            default:
            case INTEL:
                return 0;
            case NUKE:
                return Math.min(1, defender.getNukes());
            case MISSILE:
                return Math.min(1, defender.getMissiles());
            case SHIPS:
                return defender.getShips() * 0.03d * factor;
            case AIRCRAFT:
                return defender.getAircraft() * 0.03d * factor;
            case TANKS:
                return defender.getTanks() * 0.03d * factor;
            case SPIES:
                return getSpyKills(attacking, defender.getSpies(), defender.hasProject(Projects.SURVEILLANCE_NETWORK));
            case SOLDIER:
                return defender.getSoldiers() * 0.03d * factor;
        }
    }

    public static double getSpyKills(int attacking, int defending, boolean defenderHasSN) {
        double killedCap = defending * 0.25 + 4 ;
        double killed = Math.min(defending, Math.min(killedCap, (attacking - (defending * 0.4)) * 0.5 * 0.95));
        if (defenderHasSN) killed *= 0.75;
        return killed;
    }

    public static double getNetSpyKills(int attacking, int defending, Operation operation, int safety, DBNation nation) {
        double odds = getOdds(attacking, defending, safety, operation, nation);
        double losses = getFailedSpyLosses(attacking, defending, operation, safety);
        boolean defenderHasSN = nation.hasProject(Projects.SURVEILLANCE_NETWORK);
        double kills = operation == Operation.SPIES ? getSpyKills(attacking, defending, defenderHasSN) : 0;

        double chi = 1;
        if (nation.hasProject(Projects.SPY_SATELLITE)) {
            chi *= 1.5;
        }

        double avgKills = kills * (odds / 100d);
        double avgDeaths = losses * ((100 - odds) / 100d);
        return (avgKills - avgDeaths) * chi;
    }

}
