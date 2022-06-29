package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import views.grant.cities;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public enum AllianceMetric {
    SOLDIER(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getSoldiers();
        }
    },
    SOLDIER_PCT(true) {
        @Override
        public double apply(DBAlliance alliance) {
            DBNation total = alliance.getMembersTotal();
            return (double) total.getSoldiers() / (total.getCities() * Buildings.BARRACKS.cap() * Buildings.BARRACKS.max());
        }
    },
    TANK(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getTanks();
        }
    },
    TANK_PCT(true) {
        @Override
        public double apply(DBAlliance alliance) {
            DBNation total = alliance.getMembersTotal();
            return (double) total.getTanks() / (total.getCities() * Buildings.FACTORY.cap() * Buildings.FACTORY.max());
        }
    },
    AIRCRAFT(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getAircraft();
        }
    },
    AIRCRAFT_PCT(true) {
        @Override
        public double apply(DBAlliance alliance) {
            DBNation total = alliance.getMembersTotal();
            return (double) total.getAircraft() / (total.getCities() * Buildings.HANGAR.cap() * Buildings.HANGAR.max());
        }
    },
    SHIP(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getShips();
        }
    },
    SHIP_PCT(true) {
        @Override
        public double apply(DBAlliance alliance) {
            DBNation total = alliance.getMembersTotal();
            return (double) total.getShips() / (total.getCities() * Buildings.DRYDOCK.cap() * Buildings.DRYDOCK.max());
        }
    },
    INFRA(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getInfra();
        }
    },
    INFRA_AVG(true) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersAverage().getAvg_infra();
        }
    },
    LAND(false) {
        @Override
        public double apply(DBAlliance alliance) {
            List<DBNation> nations = alliance.getNations(true, 0, true);
            double totalLand = 0;
            for (DBNation nation : nations) {
                totalLand += nation.getTotalLand();
            }
            return totalLand;
        }
    },
    LAND_AVG(true) {
        @Override
        public double apply(DBAlliance alliance) {
            List<DBNation> nations = alliance.getNations(true, 0, true);
            double totalLand = 0;
            int num = 0;
            for (DBNation nation : nations) {
                totalLand += nation.getTotalLand();
                num += nation.getCities();
            }
            return totalLand / num;
        }
    },
    SCORE(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getScore();
        }
    },
    SCORE_AVG(true) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getScore() / alliance.getNations(true, 0, true).size();
        }
    },
    CITY(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getCities();
        }
    },
    CITY_AVG(true) {
        @Override
        public double apply(DBAlliance alliance) {
            List<DBNation> nations = alliance.getNations(true, 0, true);
            return new SimpleNationList(nations).getTotal().getCities() / (double) nations.size();
        }
    },
    MEMBERS(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getNations(true, 0, true).size();
        }
    },
    MEMBERS_ACTIVE_1W(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getNations(true, 1440 * 7, true).size();
        }
    },
    VM(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getNations().stream().filter(f -> f.getVm_turns() != 0 && f.getPosition() > Rank.APPLICANT.id).count();
        }
    },
    INACTIVE_1W(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getNations().stream().filter(f -> f.getVm_turns() == 0 && f.getPosition() > Rank.APPLICANT.id && f.getActive_m() > 1440 * 7).count();
        }
    },
    VM_PCT(true) {
        @Override
        public double apply(DBAlliance alliance) {
            List<DBNation> nations = alliance.getNations();
            return nations.stream().filter(f -> f.getVm_turns() != 0 && f.getPosition() > Rank.APPLICANT.id).count() / (double) nations.size();
        }
    },
    INACTIVE_PCT(true) {
        @Override
        public double apply(DBAlliance alliance) {
            List<DBNation> nations = alliance.getNations();
            return nations.stream().filter(f -> f.getActive_m() > 1440 * 7 && f.getPosition() > Rank.APPLICANT.id).count() / (double) nations.size();
        }
    },
    WARCOST_DAILY(false) {
        @Override
        public double apply(DBAlliance alliance) {
            List<DBNation> nations = alliance.getNations();
            nations.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id || f.getVm_turns() > 0);
            Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());

            AttackCost cost = new AttackCost();
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
            List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(nationIds, cutoff);
            cost.addCost(attacks, a -> nationIds.contains(a.attacker_nation_id), b -> !nationIds.contains(b.defender_nation_id));
            return cost.convertedTotal(true);
        }
    },
    REVENUE(false) {
        @Override
        public double apply(DBAlliance alliance) {
            List<DBNation> nations = alliance.getNations();
            nations.removeIf(f -> f.isGray() || f.isBeige() || f.getPosition() <= Rank.APPLICANT.id || f.getVm_turns() > 0);
            Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());

            Map<Integer, Map<Integer, DBCity>> allCities = Locutus.imp().getNationDB().getCitiesV3(nationIds);

            double[] profitBuffer = ResourceType.getBuffer();
            double[] totalRss = ResourceType.getBuffer();
            for (DBNation nation : nations) {
                Map<Integer, DBCity> v3Cities = allCities.get(nation.getNation_id());
                if (v3Cities == null || v3Cities.isEmpty()) continue;

                Map<Integer, JavaCity> cities = Locutus.imp().getNationDB().toJavaCity(v3Cities);

                Arrays.fill(profitBuffer, 0);
                double[] revenue = PnwUtil.getRevenue(profitBuffer, nation, cities, true, true, true);
                totalRss = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, totalRss, revenue);

            }

            aaRevenueCache = new AbstractMap.SimpleEntry<>(alliance.getAlliance_id(), totalRss);

            return PnwUtil.convertedTotal(totalRss);
        }
    },
    OFFENSIVE_WARS(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getOff();
        }
    },
    OFFENSIVE_WARS_AVG(true) {
        @Override
        public double apply(DBAlliance alliance) {
            List<DBNation> nations = alliance.getNations();
            nations.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id || f.getVm_turns() > 0);
            return new SimpleNationList(nations).getTotal().getOff() / (double) nations.size();
        }
    },
    DEFENSIVE_WARS(false) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getDef();
        }
    },
    DEFENSIVE_WARS_AVG(true) {
        @Override
        public double apply(DBAlliance alliance) {
            List<DBNation> nations = alliance.getNations();
            nations.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id || f.getVm_turns() > 0);
            return new SimpleNationList(nations).getTotal().getDef() / (double) nations.size();
        }
    },

    REVENUE_MONEY(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.MONEY.ordinal()] : 0d;
        }
    },
    REVENUE_FOOD(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.FOOD.ordinal()] : 0d;
        }
    },
    REVENUE_COAL(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.COAL.ordinal()] : 0d;
        }
    },
    REVENUE_OIL(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.OIL.ordinal()] : 0d;
        }
    },
    REVENUE_URANIUM(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.URANIUM.ordinal()] : 0d;
        }
    },
    REVENUE_LEAD(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.LEAD.ordinal()] : 0d;
        }
    },
    REVENUE_IRON(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.IRON.ordinal()] : 0d;
        }
    },
    REVENUE_BAUXITE(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.BAUXITE.ordinal()] : 0d;
        }
    },
    REVENUE_GASOLINE(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.GASOLINE.ordinal()] : 0d;
        }
    },
    REVENUE_MUNITIONS(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.MUNITIONS.ordinal()] : 0d;
        }
    },
    REVENUE_STEEL(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.STEEL.ordinal()] : 0d;
        }
    },
    REVENUE_ALUMINUM(false) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.ALUMINUM.ordinal()] : 0d;
        }
    },

    ;

    private static Map.Entry<Integer, double[]> aaRevenueCache;

    private final boolean average;

    AllianceMetric(boolean averageInAggregate) {
        this.average = averageInAggregate;
    }

    public boolean shouldAverage() {
        return average;
    }

    public static AllianceMetric[] values = AllianceMetric.values();

    public static synchronized void update(int topX) {
        long turn = TimeUtil.getTurn();
        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances(true, true, true, topX);
        for (DBAlliance alliance : alliances) {
            for (AllianceMetric metric : values) {
                double value = metric.apply(alliance);
                Locutus.imp().getNationDB().addMetric(alliance, metric, turn, value);
            }
        }
    }

    public abstract double apply(DBAlliance alliance);

    public static TimeNumericTable generateTable(AllianceMetric metric, long cutoffTurn, Collection<String> coalitionNames, Set<DBAlliance>... coalitions) {
        return generateTable(metric, cutoffTurn, TimeUtil.getTurn(), coalitionNames, coalitions);
    }

    public static TimeNumericTable generateTable(Collection<AllianceMetric> metrics, long startTurn, long endTurn, String coalitionName, Set<DBAlliance> coalition) {
        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(metrics, startTurn, endTurn, coalition);
        return generateTable(metricMap, metrics, startTurn, endTurn, coalitionName, coalition);
    }

    public static TimeNumericTable generateTable(Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap, Collection<AllianceMetric> metrics, long startTurn, long endTurn, String coalitionName, Set<DBAlliance> coalition) {
        double[][] valuesByTurnByMetric = AllianceMetric.toValueByTurnByMetric(metricMap, metrics, startTurn, endTurn, coalition);

        AllianceMetric[] metricsArr = metrics.toArray(new AllianceMetric[0]);
        long minTurn = Long.MAX_VALUE;
        long maxTurn = 0;
        for (double[] valuesByTurn : valuesByTurnByMetric) {
            for (int i = 0; i < valuesByTurn.length; i++) {
                if (valuesByTurn[i] != Long.MAX_VALUE) {
                    minTurn = Math.min(i + startTurn, minTurn);
                    maxTurn = Math.max(i + startTurn, maxTurn);
                }
            }
        }

        double[] buffer =  new double[metrics.size()];

        String[] labels = new String[metrics.size()];
        {
            int i = 0;
            for (AllianceMetric metric : metrics) {
                labels[i++] = metric.name();
            }
        }
        String title = coalitionName + " metrics";
        TimeNumericTable<Void> table = new TimeNumericTable<>(title, "turn", "value", labels) {
            @Override
            public void add(long turn, Void ignore) {
                int turnRelative = (int) (turn - startTurn);
                for (int i = 0; i < metricsArr.length; i++) {
                    double value = valuesByTurnByMetric[i][turnRelative];
                    if (value != Double.MAX_VALUE && !Double.isNaN(value)) {
                        buffer[i] = value;
                    }
                }
                add(turnRelative, buffer);
            }
        };

        for (long turn = minTurn; turn <= maxTurn; turn++) {
            table.add(turn, (Void) null);
        }

        return table;
    }

    public static TimeNumericTable generateTable(AllianceMetric metric, long startTurn, long endTurn, Collection<String> coalitionNames, Set<DBAlliance>... coalitions) {
        if (startTurn < endTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (endTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(Collections.singleton(metric), startTurn, endTurn, coalitions);
        double[][] valuesByTurnByCoalition = AllianceMetric.toValueByTurnByCoalition(metricMap, metric, startTurn, endTurn, coalitions);

        long minTurn = Long.MAX_VALUE;
        long maxTurn = 0;
        for (double[] valuesByTurn : valuesByTurnByCoalition) {
            for (int i = 0; i < valuesByTurn.length; i++) {
                if (valuesByTurn[i] != Long.MAX_VALUE) {
                    minTurn = Math.min(i + startTurn, minTurn);
                    maxTurn = Math.max(i + startTurn, maxTurn);
                }
            }
        }

        double[] buffer =  new double[coalitions.length];

        String[] labels;
        if (coalitionNames == null || coalitionNames.size() != coalitions.length) {
            labels = new String[coalitions.length];
            for (int i = 0; i < labels.length; i++) labels[i] = "coalition " + (i + 1);
        } else {
            labels = coalitionNames.toArray(new String[0]);
        }
        String title = metric + " by turn";
        TimeNumericTable<Void> table = new TimeNumericTable<>(title, "turn", metric.name(), labels) {

            @Override
            public void add(long turn, Void ignore) {
                int turnRelative = (int) (turn - startTurn);
                for (int i = 0; i < coalitions.length; i++) {
                    double value = valuesByTurnByCoalition[i][turnRelative];
                    if (value != Double.MAX_VALUE && !Double.isNaN(value)) {
                        buffer[i] = value;
                    }
                }
                add(turnRelative, buffer);
            }
        };

        for (long turn = minTurn; turn <= maxTurn; turn++) {
            table.add(turn, (Void) null);
        }

        return table;
    }

    public static double[][] toValueByTurnByMetric(Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap, Collection<AllianceMetric> metrics, long cutoffTurn, long maxTurn, Set<DBAlliance> coalition) {
        AllianceMetric[] metricsArr = metrics.toArray(new AllianceMetric[0]);
        double[][] totalValuesByTurnByMetric = new double[metricsArr.length][(int) (maxTurn - cutoffTurn + 1)];
        for (double[] doubles : totalValuesByTurnByMetric) Arrays.fill(doubles, Double.MAX_VALUE);

        boolean[] shouldAverageArr = new boolean[metricsArr.length];
        double[][] totalMembersByTurnByMetric = new double[metricsArr.length][];
        for (int i = 0; i < shouldAverageArr.length; i++) {
            if (metricsArr[i].shouldAverage() && coalition.size() > 1) {
                shouldAverageArr[i] = true;
                totalMembersByTurnByMetric[i] = new double[(int) (maxTurn - cutoffTurn + 1)];
            }
        }

        for (int k = 0; k < metricsArr.length; k++) {
            AllianceMetric metric = metricsArr[k];
            boolean shouldAverage = shouldAverageArr[k];

            for (DBAlliance alliance : coalition) {
                Map<AllianceMetric, Map<Long, Double>> metricData = metricMap.get(alliance);
                if (metricData == null) continue;
                Map<Long, Double> membersByTurn = null;
                if (shouldAverage) {
                    membersByTurn = metricData.get(AllianceMetric.MEMBERS);
                    if (membersByTurn == null) {
                        continue;
                    }
                }
                Map<Long, Double> metricByTurn = metricData.get(metric);
                if (metricByTurn == null) {
                    continue;
                }

                for (Map.Entry<Long, Double> turnMetric : metricByTurn.entrySet()) {
                    long turn = turnMetric.getKey();
                    Double value = turnMetric.getValue();
                    if (value == null) {
                        continue;
                    }

                    int turnRelative = (int) (turn - cutoffTurn);

                    Double members = null;
                    if (shouldAverage) {
                        members = membersByTurn.get(turn);
                        if (members == null) {
                            continue;
                        }
                        value *= members;
                    }

                    if (members != null) {
                        double[] totalMembersByTurn = totalMembersByTurnByMetric[k];
                        totalMembersByTurn[turnRelative] += members;
                    }
                    double[] totalValuesByTurn = totalValuesByTurnByMetric[k];
                    if (totalValuesByTurn[turnRelative] == Double.MAX_VALUE) {
                        totalValuesByTurn[turnRelative] = value;
                    } else {
                        totalValuesByTurn[turnRelative] += value;
                    }
                }
            }
        }
        for (int i = 0; i < totalMembersByTurnByMetric.length; i++) {
            boolean shouldAverage = shouldAverageArr[i];
            if (shouldAverage) {
                double[] totalValuesByTurn = totalValuesByTurnByMetric[i];
                double[] totalMembersByTurn = totalMembersByTurnByMetric[i];
                for (int j = 0; j < totalMembersByTurn.length; j++) {
                    if (totalValuesByTurn[j] != Double.MAX_VALUE) {
                        totalValuesByTurn[j] /= totalMembersByTurn[j];
                    }
                }
            }
        }

        return totalValuesByTurnByMetric;
    }

    public static double[][] toValueByTurnByCoalition(Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap, AllianceMetric metric, long cutoffTurn, long maxTurn, Set<DBAlliance>... coalitions) {

        double[][] totalValuesByTurnByCoal = new double[coalitions.length][(int) (maxTurn - cutoffTurn + 1)];
        for (double[] doubles : totalValuesByTurnByCoal) Arrays.fill(doubles, Double.MAX_VALUE);

        boolean shouldAverage = metric.shouldAverage() && metricMap.size() > 1;

        double[][] totalMembersByTurnByCoal = shouldAverage ? new double[coalitions.length][(int) (maxTurn - cutoffTurn + 1)] : null;

        for (Map.Entry<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> entry1 : metricMap.entrySet()) {
            DBAlliance alliance = entry1.getKey();
            Map<Long, Double> membersByTurn = null;
            if (shouldAverage) {
                membersByTurn = entry1.getValue().get(AllianceMetric.MEMBERS);
                if (membersByTurn == null) {
                    continue;
                }
            }
            Map<Long, Double> metricByTurn = entry1.getValue().get(metric);
            if (metricByTurn == null) {
                continue;
            }

            for (Map.Entry<Long, Double> turnMetric : metricByTurn.entrySet()) {
                long turn = turnMetric.getKey();
                Double value = turnMetric.getValue();
                if (value == null) {
                    continue;
                }

                int turnRelative = (int) (turn - cutoffTurn);

                Double members = null;
                if (shouldAverage) {
                    members = membersByTurn.get(turn);
                    if (members == null) {
                        continue;
                    }
                    value *= members;
                }
                for (int i = 0; i < coalitions.length; i++) {
                    Set<DBAlliance> coalition = coalitions[i];
                    if (coalition.contains(alliance)) {
                        if (members != null) {
                            double[] totalMembersByTurn = totalMembersByTurnByCoal[i];
                            totalMembersByTurn[turnRelative] += members;
                        }
                        double[] totalValuesByTurn = totalValuesByTurnByCoal[i];
                        if (totalValuesByTurn[turnRelative] == Double.MAX_VALUE) {
                            totalValuesByTurn[turnRelative] = value;
                        } else {
                            totalValuesByTurn[turnRelative] += value;
                        }
                    }
                }
            }
        }
        if (shouldAverage) {
            for (int i = 0; i < totalMembersByTurnByCoal.length; i++) {
                double[] totalValuesByTurn = totalValuesByTurnByCoal[i];
                double[] totalMembersByTurn = totalMembersByTurnByCoal[i];
                for (int j = 0; j < totalMembersByTurn.length; j++) {
                    if (totalValuesByTurn[j] != Double.MAX_VALUE) {
                        totalValuesByTurn[j] /= totalMembersByTurn[j];
                    }
                }
            }
        }

        return totalValuesByTurnByCoal;
    }

    public static Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getMetrics(Collection<AllianceMetric> metrics, long minTurn, Set<DBAlliance>... coalitions) {
        return getMetrics(metrics, minTurn, TimeUtil.getTurn(), coalitions);
    }

    public static Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getMetrics(Collection<AllianceMetric> metrics, long minTurn, long maxTurn, Set<DBAlliance>... coalitions) {
        if (minTurn < maxTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (maxTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");
        Set<DBAlliance> allAlliances = new HashSet<>();
        for (Set<DBAlliance> coalition : coalitions) allAlliances.addAll(coalition);
        Set<Integer> aaIds = allAlliances.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());
        Set<AllianceMetric> finalMetrics = new HashSet<>(metrics);
        if (aaIds.size() > 1) {
            for (AllianceMetric metric : metrics) {
                if (metric.shouldAverage()) {
                    finalMetrics.add(AllianceMetric.MEMBERS);
                    break;
                }
            }
        }

        return Locutus.imp().getNationDB().getMetrics(aaIds, finalMetrics, minTurn, maxTurn);
    }
}
