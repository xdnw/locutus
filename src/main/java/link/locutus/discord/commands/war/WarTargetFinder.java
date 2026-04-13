package link.locutus.discord.commands.war;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.battle.BlitzGenerator;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.PriorityQueue;

public final class WarTargetFinder {
    private WarTargetFinder() {
    }

    public static Set<DBNation> getWarRangeTargets(DBNation me, GuildDB guildDB, boolean onlyWeaker, boolean ignoreDNR) {
        Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(f -> f.isInWarRange(me));
        nations.removeIf(f -> f.getVm_turns() != 0);

        if (!ignoreDNR) {
            Function<DBNation, Boolean> canRaid = guildDB == null ? f -> true : guildDB.getCanRaid();
            nations.removeIf(f -> !canRaid.apply(f));
        }

        if (onlyWeaker) {
            nations.removeIf(f -> f.getStrength() > me.getStrength());
        }

        return nations;
    }

    public static CounterChanceContext buildCounterChanceContext(GuildDB db, Set<DBNation> targets,
                                                                 boolean ignoreDNR,
                                                                 boolean includeAllies,
                                                                 Set<DBNation> nationsToBlitzWith,
                                                                 boolean withinAllAttackersRange,
                                                                 boolean ignoreODP,
                                                                 boolean force) {
        if (nationsToBlitzWith == null) {
            throw new IllegalArgumentException("Please provide a list of nations for `nationsToBlitzWith`");
        }
        if (ignoreODP && !includeAllies) {
            throw new IllegalArgumentException("Cannot use `ignoreODP` when `includeAllies` is false");
        }
        if (nationsToBlitzWith.stream().anyMatch(f -> f.active_m() > 7200 || f.getVm_turns() > 0) && !force) {
            throw new IllegalArgumentException("You can't blitz with nations that are inactive or VM. Add `force: True` to bypass");
        }
        Set<DBNation> blitzers = new ObjectLinkedOpenHashSet<>(nationsToBlitzWith);
        BiFunction<Double, Double, Integer> attScores = PW.getIsNationsInScoreRange(blitzers);

        List<DBNation> nations = new ArrayList<>(targets);
        nations.removeIf(f -> f.getVm_turns() != 0);
        nations.removeIf(f -> f.getDef() >= 3);
        nations.removeIf(DBNation::isBeige);
        if (withinAllAttackersRange) {
            double minScore = nationsToBlitzWith.stream().mapToDouble(DBNation::getScore).max().orElse(0) * 0.75;
            double maxScore = nationsToBlitzWith.stream().mapToDouble(DBNation::getScore).min().orElse(0) * PW.WAR_RANGE_MAX_MODIFIER;
            if (minScore >= maxScore) {
                throw new IllegalArgumentException("Nations `nationsToBlitzWith` do not share a score range.");
            }
            nations.removeIf(f -> f.getScore() < minScore || f.getScore() > maxScore);
        } else {
            nations.removeIf(f -> attScores.apply(f.getScore() / PW.WAR_RANGE_MAX_MODIFIER, f.getScore() * 1.25) <= 0);
        }

        if (!ignoreDNR) {
            Function<DBNation, Boolean> dnr = db == null ? f -> true : db.getCanRaid();
            nations.removeIf(f -> !dnr.apply(f));
        }

        Set<Integer> aaIds = new IntOpenHashSet();
        for (DBNation nation : nations) {
            if (nation.active_m() < 10000 && nation.getPosition() >= Rank.MEMBER.id) {
                aaIds.add(nation.getAlliance_id());
            }
        }

        int maxCounterSize = Math.max(0, blitzers.size() * 3);
        for (DBNation nation : blitzers) {
            maxCounterSize -= nation.getDef();
        }
        maxCounterSize = Math.max(0, maxCounterSize);

        Int2ObjectOpenHashMap<List<DBNation>> countersByAlliance = buildCounterAlliancePools(aaIds, includeAllies, ignoreODP, attScores, maxCounterSize);

        Int2DoubleOpenHashMap targetStrengthByNationId = new Int2DoubleOpenHashMap();
        targetStrengthByNationId.defaultReturnValue(0d);
        double blitzStrength = 0;
        for (DBNation nation : blitzers) {
            double baseStrength = Math.pow(nation.getStrength(), 3);
            double nationStrength;
            if (nation.active_m() > 2880) {
                if (nation.lostInactiveWar() || nation.getAlliance_id() == 0) {
                    nationStrength = baseStrength * 0.44;
                } else if (nation.getPosition() == Rank.APPLICANT.id) {
                    nationStrength = baseStrength * Math.max(0, 0.8 - 0.1 * nation.active_m() / 1440d);
                } else {
                    nationStrength = baseStrength * Math.max(0, 0.8 - 0.1 * nation.active_m() / 1440d);
                }
            } else if (nation.getAlliance_id() == 0) {
                nationStrength = baseStrength * 0.66;
            } else if (nation.getDef() > 0 && nation.getRelativeStrength(false) < 1) {
                nationStrength = baseStrength * 0.33;
            } else if (nation.getAircraft() == 0 && nation.getSoldiers() == 0) {
                nationStrength = baseStrength * 0.22;
            } else {
                nationStrength = baseStrength;
            }
            targetStrengthByNationId.put(nation.getNation_id(), nationStrength);
            blitzStrength += baseStrength;
        }

        return new CounterChanceContext(nations, countersByAlliance, targetStrengthByNationId, blitzers, maxCounterSize, blitzStrength);
    }

    private static Int2ObjectOpenHashMap<List<DBNation>> buildCounterAlliancePools(Set<Integer> aaIds,
                                                                                   boolean includeAllies,
                                                                                   boolean ignoreODP,
                                                                                   BiFunction<Double, Double, Integer> attScores,
                                                                                   int maxCounterSize) {
        Int2ObjectOpenHashMap<List<DBNation>> countersByAlliance = new Int2ObjectOpenHashMap<>();
        for (Integer aaId : aaIds) {
            List<DBNation> canCounter = new ArrayList<>();
            DBAlliance alliance = DBAlliance.getOrCreate(aaId);
            Set<DBAlliance> alliances = new HashSet<>(Arrays.asList(alliance));
            if (includeAllies) {
                Set<DBAlliance> allies;
                if (ignoreODP) {
                    allies = alliance.getTreatiedAllies(f -> f != TreatyType.ODP && f.isDefensive(), false);
                } else {
                    allies = alliance.getTreatiedAllies();
                }
                alliances.addAll(allies);
            }
            for (DBAlliance ally : alliances) {
                canCounter.addAll(ally.getNations(true, 10000, true));
            }

            canCounter.removeIf(f -> f.getVm_turns() > 0);
            canCounter.removeIf(f -> f.getCities() < 10 && f.active_m() > 2880);
            canCounter.removeIf(f -> f.getCities() == 10 && f.active_m() > 3000);
            canCounter.removeIf(f -> f.getCities() > 10 && f.active_m() > 12000);
            canCounter.removeIf(f -> attScores.apply(f.getScore() * 0.75, f.getScore() * PW.WAR_RANGE_MAX_MODIFIER) <= 0);
            canCounter.removeIf(f -> f.getOff() >= f.getMaxOff());
            canCounter.removeIf(f -> f.getNumWars() > 0 && f.getRelativeStrength() < 1);
            canCounter.removeIf(f -> f.getAircraftPct() < 0.5 && f.getTankPct() < 0.5);

            Collections.sort(canCounter, new Comparator<DBNation>() {
                @Override
                public int compare(DBNation o1, DBNation o2) {
                    return Double.compare(o2.getStrength(), o1.getStrength());
                }
            });
            if (canCounter.size() > maxCounterSize) canCounter = canCounter.subList(0, maxCounterSize);
            countersByAlliance.put(aaId, canCounter);
        }
        return countersByAlliance;
    }

    public static List<Map.Entry<DBNation, Double>> scoreCounterChance(CounterChanceContext context,
                                                                       Double maxRelativeTargetStrength,
                                                                       Double maxRelativeCounterStrength) {
        if (context.nations().isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<DBNation, Double>> counterChance = new ArrayList<>();
        double blitzStrengthModifier = Math.pow(0.85, Math.min(3, context.nationsToBlitzWith().size())) / 0.85;
        for (DBNation nation : context.nations()) {
            double counterStrength = 0;
            double inactive0 = 0;
            double inactive1 = 0;
            double inactive2 = 0;
            if (nation.getAlliance_id() != 0) {
                List<DBNation> counters = context.countersByAlliance().get(nation.getAlliance_id());
                if (counters != null) {
                    counters = new ArrayList<>(counters);
                    counters.remove(nation);
                    int i = 0;

                    for (DBNation other : counters) {
                        if (other.getId() == nation.getId()) continue;
                        if (i++ >= context.maxCounterSize()) break;
                        double otherStrength = Math.pow(other.getStrength(), 3);
                        if (other.active_m() > 2880) {
                            inactive0 += (1 + ((other.active_m() - 2880d) / 1440d));
                        } else if (other.active_m() > 1440) {
                            inactive1 += (1 + (other.active_m() - 1440d) / 1440d);
                        } else {
                            inactive2 += (1 + (other.active_m()) / 1440d);
                        }
                        counterStrength += otherStrength;
                    }
                }
            }
            double logistics = inactive0 * 2 + inactive1 * 1 + inactive2 * 0.5;
            if (logistics > 1) {
                counterStrength = counterStrength * Math.pow(logistics, 0.95);
            }
            counterStrength += context.targetStrengthByNationId().get(nation.getNation_id()) * blitzStrengthModifier;
            counterChance.add(new AbstractMap.SimpleEntry<>(nation, counterStrength));
        }

        double blitzStrength = context.blitzStrength();

        if (maxRelativeCounterStrength != null) {
            counterChance.removeIf(f -> f.getKey().getStrength() > blitzStrength * maxRelativeCounterStrength);
        }
        if (maxRelativeTargetStrength != null) {
            counterChance.removeIf(f -> context.targetStrengthByNationId().get(f.getKey().getNation_id()) > blitzStrength * maxRelativeTargetStrength);
        }

        if (counterChance.isEmpty()) {
            return Collections.emptyList();
        }

        Map<DBNation, Double> valueWeighted = new HashMap<>();
        for (Map.Entry<DBNation, Double> entry : counterChance) {
            valueWeighted.put(entry.getKey(), entry.getValue());
        }
        Collections.sort(counterChance, new Comparator<Map.Entry<DBNation, Double>>() {
            @Override
            public int compare(Map.Entry<DBNation, Double> o1, Map.Entry<DBNation, Double> o2) {
                return Double.compare(valueWeighted.get(o1.getKey()), valueWeighted.get(o2.getKey()));
            }
        });
        return counterChance;
    }

    public static List<Map.Entry<DBNation, Double>> getWarTargets(DBNation me, Set<DBNation> targets, int numResults, Double attackerScore,
                                                                  boolean includeInactives, boolean includeApplicants, boolean onlyPriority,
                                                                  boolean onlyWeak, boolean onlyEasy, boolean onlyLessCities,
                                                                  boolean includeStrong) {
        if (me == null) {
            throw new IllegalArgumentException("Please sign, or provide a nation to raid as");
        }

        double score = attackerScore == null ? me.getScore() : attackerScore;
        List<DBNation> filteredTargets = new ArrayList<>(targets == null ? Collections.emptySet() : targets);

        filteredTargets.removeIf(f -> !includeApplicants && f.active_m() > 1440 && f.getPosition() <= 1);
        filteredTargets.removeIf(f -> !includeInactives && f.active_m() >= 2440);
        filteredTargets.removeIf(f -> f.getVm_turns() != 0);

        double minScore = score * 0.75;
        double maxScore = score * PW.WAR_RANGE_MAX_MODIFIER;
        filteredTargets.removeIf(f -> f.getScore() >= maxScore || f.getScore() <= minScore);
        filteredTargets.removeIf(f -> f.getDef() >= 3);
        filteredTargets.removeIf(f -> f.getCities() >= me.getCities() * 1.5 && !includeStrong && me.getGroundStrength(false, true) > f.getGroundStrength(true, false) * 2);
        filteredTargets.removeIf(f -> f.getCities() >= me.getCities() * 1.8 && !includeStrong && f.active_m() < 2880);

        if (onlyPriority) {
            filteredTargets.removeIf(f -> f.getNumWars() == 0);
            filteredTargets.removeIf(f -> f.getRelativeStrength() <= 1);
        }

        if (onlyWeak) {
            filteredTargets.removeIf(f -> f.getGroundStrength(true, false) > me.getGroundStrength(true, false));
            filteredTargets.removeIf(f -> f.getAircraft() > me.getAircraft());
        }
        if (onlyLessCities) {
            filteredTargets.removeIf(f -> f.getCities() > me.getCities());
        }

        Set<DBWar> wars = me.getActiveWars();
        for (DBWar war : wars) {
            filteredTargets.remove(war.getNation(true));
            filteredTargets.remove(war.getNation(false));
        }

        List<Map.Entry<DBNation, Double>> nationNetValues = new ArrayList<>();

        for (DBNation nation : filteredTargets) {
            if (nation.isBeige()) continue;
            nationNetValues.add(new AbstractMap.SimpleEntry<>(nation, scoreWarTarget(me, nation, onlyEasy, score)));
        }

        if (nationNetValues.isEmpty()) {
            for (DBNation nation : filteredTargets) {
                if (nation.isBeige()) {
                    nationNetValues.add(new AbstractMap.SimpleEntry<>(nation, (double) nation.getBeigeTurns()));
                }
            }
        }

        nationNetValues.sort(Comparator.comparingDouble(Map.Entry::getValue));

        if (numResults >= 0 && nationNetValues.size() > numResults) {
            nationNetValues = new ArrayList<>(nationNetValues.subList(0, numResults));
        }
        return nationNetValues;
    }

    private static double scoreWarTarget(DBNation me, DBNation nation, boolean onlyEasy, double attackerScore) {
        double value = BlitzGenerator.getAirStrength(nation, true);
        if (!onlyEasy) {
            value *= 2 * (nation.getCities() / (double) me.getCities());
            if (nation.getOff() > 0) value /= 4;
            if (nation.getShips() > 1 && nation.getOff() > 0 && nation.isBlockader()) value /= 2;
            if (nation.getDef() <= 1) value /= (1.05 + (0.1 * nation.getDef()));
            if (nation.active_m() > 1440) value *= 1 + Math.sqrt(nation.active_m() - 1440) / 250;
            value /= (1 + nation.getOff() * 0.1);
            if (nation.getScore() > attackerScore * 1.25) value /= 2;
            if (nation.getOff() > 0) value /= nation.getRelativeStrength();
        }
        return value;
    }

    public static DamageTargets getDamageTargets(DBNation me, Set<DBNation> nations, boolean includeApplicants,
                                                 boolean includeInactives, boolean filterWeak, boolean filterNoShips,
                                                 boolean includeBeige, double score) {
        Set<DBNation> filtered = new ObjectLinkedOpenHashSet<>(nations);
        filtered.removeIf(f -> f.getDef() >= 3);
        if (!includeApplicants) {
            filtered.removeIf(f -> f.getPosition() <= 1);
        }
        if (!includeInactives) {
            filtered.removeIf(f -> f.active_m() > (f.getCities() > 11 ? 5 : 2) * 1440);
        }
        if (filterNoShips) {
            filtered.removeIf(f -> f.getShips() > 2);
        }
        if (!includeBeige) {
            filtered.removeIf(DBNation::isBeige);
        }

        double minScore = score * 0.75;
        double maxScore = score * PW.WAR_RANGE_MAX_MODIFIER;
        filtered.removeIf(f -> f.getScore() <= minScore || f.getScore() >= maxScore);

        double attackerGroundStrength = me.getGroundStrength(false, true);
        attackerGroundStrength = Math.max(attackerGroundStrength, me.getCities() * 15000);
        if (filterWeak) {
            double finalAttackerGroundStrength = attackerGroundStrength;
            filtered.removeIf(f -> f.getGroundStrength(true, false) > finalAttackerGroundStrength * 0.4);
        }

        Map<Integer, Double> maxInfraByNation = new LinkedHashMap<>();
        Map<Integer, Double> damageEstByNation = new LinkedHashMap<>();
        Map<Integer, Double> avgInfraByNation = new LinkedHashMap<>();
        Map<Integer, Double> avgBeigeInfraByNation = new LinkedHashMap<>();
        Map<Integer, List<Double>> cityInfraByNation = new HashMap<>();

        for (DBNation nation : filtered) {
            Collection<JavaCity> cities = nation.getCityMap(false, false, false).values();
            List<Double> allInfra = new ArrayList<>(cities.size());
            for (JavaCity city : cities) {
                allInfra.add(city.getInfra());
            }
            if (allInfra.isEmpty()) {
                continue;
            }
            double max = Collections.max(allInfra);
            double average = allInfra.stream().mapToDouble(f -> f).average().orElse(0);
            double beigeDmg = 0;
            double beigeFactor = nation.getBeigeDamageFactor();
            double beigeAbs = 1 - (0.04 * beigeFactor);
            for (JavaCity city : cities) {
                beigeDmg += PW.City.Infra.calculateInfra(city.getInfra() * beigeAbs, city.getInfra());
            }
            avgInfraByNation.put(nation.getNation_id(), average);
            maxInfraByNation.put(nation.getNation_id(), max);
            cityInfraByNation.put(nation.getNation_id(), allInfra);
            avgBeigeInfraByNation.put(nation.getNation_id(), beigeDmg);
        }

        for (Map.Entry<Integer, List<Double>> entry : cityInfraByNation.entrySet()) {
            double cost = damageEstimate(me, entry.getKey(), entry.getValue());
            if (cost <= 0) continue;
            damageEstByNation.put(entry.getKey(), cost);
        }

        return new DamageTargets(filtered, maxInfraByNation, damageEstByNation, avgInfraByNation, avgBeigeInfraByNation, Collections.emptyList());
    }

    public static DamageTargets getWarDamageTargets(DBNation me, Set<DBNation> nations, boolean includeApps,
                                                     boolean includeInactives, boolean filterWeak, boolean noNavy,
                                                     boolean includeBeige, Double relativeNavalStrength, Double warRange) {
        Set<DBNation> filtered = new ObjectLinkedOpenHashSet<>(nations);
        List<String> removeNotes = new ArrayList<>();
        int prevSize;
        int removedCount;

        prevSize = filtered.size();
        filtered.removeIf(f -> f.getDef() >= 3);
        if ((removedCount = prevSize - filtered.size()) > 0) {
            removeNotes.add("Removed because `Def >= 3`: " + removedCount);
        }

        prevSize = filtered.size();
        filtered.removeIf(f -> f.getVm_turns() != 0);
        if ((removedCount = prevSize - filtered.size()) > 0) {
            removeNotes.add("Removed because `VM Turns != 0`: " + removedCount);
        }

        if (!includeApps) {
            prevSize = filtered.size();
            filtered.removeIf(f -> f.getPosition() <= 1);
            if ((removedCount = prevSize - filtered.size()) > 0) {
                removeNotes.add("Removed because `includeApps:False`: " + removedCount);
            }
        }

        if (!includeInactives) {
            prevSize = filtered.size();
            filtered.removeIf(f -> f.active_m() > (f.getCities() > 11 ? 5 : 2) * 1440);
            if ((removedCount = prevSize - filtered.size()) > 0) {
                removeNotes.add("Removed because `includeInactives:False`: " + removedCount);
            }
        }

        if (noNavy) {
            prevSize = filtered.size();
            filtered.removeIf(f -> f.getShips() > 2);
            if ((removedCount = prevSize - filtered.size()) > 0) {
                removeNotes.add("Removed because `noNavy:True`: " + removedCount);
            }
        }

        if (relativeNavalStrength != null) {
            prevSize = filtered.size();
            filtered.removeIf(f -> f.getShips() > me.getShips() * relativeNavalStrength);
            if ((removedCount = prevSize - filtered.size()) > 0) {
                removeNotes.add("Removed because `relativeNavalStrength`: " + removedCount);
            }
        }

        if (!includeBeige) {
            prevSize = filtered.size();
            filtered.removeIf(DBNation::isBeige);
            if ((removedCount = prevSize - filtered.size()) > 0) {
                removeNotes.add("Removed because `includeBeige:False`: " + removedCount);
            }
        }

        if (warRange == null || warRange == 0) warRange = me.getScore();
        double minScore = warRange * 0.75;
        double maxScore = warRange * PW.WAR_RANGE_MAX_MODIFIER;

        prevSize = filtered.size();
        filtered.removeIf(f -> f.getScore() <= minScore || f.getScore() >= maxScore);
        if ((removedCount = prevSize - filtered.size()) > 0) {
            removeNotes.add("Removed because `Score Range`: " + removedCount);
        }

        if (me == null) {
            throw new IllegalArgumentException("Please use the current nation before running damage targets");
        }
        double str = me.getGroundStrength(false, true);
        str = Math.max(str, me.getCities() * 15000);
        if (filterWeak) {
            double finalStr = str;
            prevSize = filtered.size();
            filtered.removeIf(f -> f.getGroundStrength(true, false) > finalStr * 0.4);
            if ((removedCount = prevSize - filtered.size()) > 0) {
                removeNotes.add("Removed because `filterWeak:True`: " + removedCount);
            }
        }

        Map<Integer, Double> maxInfraByNation = new LinkedHashMap<>();
        Map<Integer, Double> damageEstByNation = new LinkedHashMap<>();
        Map<Integer, Double> avgInfraByNation = new LinkedHashMap<>();
        Map<Integer, Double> avgBeigeInfraByNation = new LinkedHashMap<>();
        Map<Integer, List<Double>> cityInfraByNation = new HashMap<>();

        for (DBNation nation : filtered) {
            Collection<JavaCity> cities = nation.getCityMap(false, false, false).values();
            List<Double> allInfra = new ArrayList<>(cities.size());
            for (JavaCity city : cities) {
                allInfra.add(city.getInfra());
            }
            if (allInfra.isEmpty()) {
                continue;
            }
            double max = Collections.max(allInfra);
            double average = allInfra.stream().mapToDouble(f -> f).average().orElse(0);
            double beigeDmg = 0;
            double beigeFactor = nation.getBeigeDamageFactor();
            double beigeAbs = 1 - (0.04 * beigeFactor);
            for (JavaCity city : cities) {
                beigeDmg += PW.City.Infra.calculateInfra(city.getInfra() * beigeAbs, city.getInfra());
            }
            avgInfraByNation.put(nation.getNation_id(), average);
            maxInfraByNation.put(nation.getNation_id(), max);
            cityInfraByNation.put(nation.getNation_id(), allInfra);
            avgBeigeInfraByNation.put(nation.getNation_id(), beigeDmg);
        }

        for (Map.Entry<Integer, List<Double>> entry : cityInfraByNation.entrySet()) {
            double cost = damageEstimate(me, entry.getKey(), entry.getValue());
            if (cost <= 0) continue;
            damageEstByNation.put(entry.getKey(), cost);
        }

        return new DamageTargets(filtered, maxInfraByNation, damageEstByNation, avgInfraByNation, avgBeigeInfraByNation, removeNotes);
    }

    public static List<Map.Entry<DBNation, Double>> topDamageTargets(Map<Integer, Double> valueFunction, int limit) {
        if (limit <= 0 || valueFunction.isEmpty()) {
            return Collections.emptyList();
        }

        PriorityQueue<Map.Entry<DBNation, Double>> topTargets = new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getValue));
        for (Map.Entry<Integer, Double> entry : valueFunction.entrySet()) {
            DBNation nation = DBNation.getById(entry.getKey());
            if (nation == null) {
                continue;
            }

            Map.Entry<DBNation, Double> candidate = new AbstractMap.SimpleEntry<>(nation, entry.getValue());
            if (topTargets.size() < limit) {
                topTargets.offer(candidate);
                continue;
            }

            Map.Entry<DBNation, Double> smallest = topTargets.peek();
            if (smallest != null && candidate.getValue() > smallest.getValue()) {
                topTargets.poll();
                topTargets.offer(candidate);
            }
        }

        List<Map.Entry<DBNation, Double>> result = new ArrayList<>(topTargets);
        result.sort(Comparator.comparingDouble(Map.Entry<DBNation, Double>::getValue).reversed());
        return result;
    }

    public static double damageHitCount(DBNation me, DBNation nation) {
        double numCities = 0;
        if (me.hasProject(Projects.MISSILE_LAUNCH_PAD)) {
            numCities += 0.5;
            if (nation.hasProject(Projects.IRON_DOME)) numCities -= 0.15;
        }
        if (me.hasProject(Projects.NUCLEAR_RESEARCH_FACILITY)) {
            numCities += 1.5;
            if (nation.hasProject(Projects.VITAL_DEFENSE_SYSTEM)) numCities -= 0.375;
        }
        if (nation.getGroundStrength(true, false) < me.getGroundStrength(true, false) * 0.4) {
            numCities++;
            if (nation.getAircraft() <= me.getAircraft()) numCities += 5;
        }
        if (nation.active_m() > 2440) numCities += 0.5;
        if (nation.active_m() > 4880) numCities += 0.5;
        if (nation.getShips() <= 1 && me.getShips() > 1) numCities += 0.3;
        if (nation.getCities() <= me.getCities() * 0.5) numCities++;
        if (nation.active_m() > 10000) numCities += 10;
        return numCities;
    }

    public static Map<DBNation, Set<DBTreasure>> getTreasureTargets(DBNation me, GuildDB guildDB, boolean onlyWeaker,
                                                                    boolean ignoreDNR) {
        Set<Integer> treasureNationIds = Locutus.imp().getNationDB().getTreasureNationIds();
        Function<DBNation, Boolean> canRaid = ignoreDNR || guildDB == null ? f -> true : guildDB.getCanRaid();
        Map<DBNation, Set<DBTreasure>> nationTreasures = new LinkedHashMap<>();
        for (Integer nationId : treasureNationIds) {
            DBNation nation = Locutus.imp().getNationDB().getNationById(nationId);
            if (nation == null) {
                continue;
            }
            if (!nation.isInWarRange(me)) {
                continue;
            }
            if (!canRaid.apply(nation)) {
                continue;
            }
            if (onlyWeaker && nation.getStrength() > me.getStrength()) {
                continue;
            }

            Set<DBTreasure> treasures = Locutus.imp().getNationDB().getTreasure(nationId);
            if (!treasures.isEmpty()) {
                nationTreasures.put(nation, treasures);
            }
        }
        return nationTreasures;
    }

    public static Map<DBNation, Set<DBBounty>> getBountyTargets(DBNation me, GuildDB guildDB, boolean onlyWeaker,
                                                                boolean ignoreDNR, Set<WarType> bountyTypes) {
        Set<DBNation> nations = getWarRangeTargets(me, guildDB, onlyWeaker, ignoreDNR);
        Set<Integer> nationIds = new HashSet<>();
        for (DBNation nation : nations) {
            nationIds.add(nation.getNation_id());
        }
        Map<Integer, List<DBBounty>> bountiesByNationId = Locutus.imp().getWarDb().getBountiesByNationIds(nationIds);
        Map<DBNation, Set<DBBounty>> nationBounties = new LinkedHashMap<>();
        for (DBNation nation : nations) {
            List<DBBounty> bountyList = bountiesByNationId.get(nation.getNation_id());
            if (bountyList == null || bountyList.isEmpty()) {
                continue;
            }
            Set<DBBounty> bounties = new ObjectLinkedOpenHashSet<>(bountyList);
            if (bountyTypes != null && !bountyTypes.isEmpty()) {
                bounties.removeIf(f -> !bountyTypes.contains(f.getType()));
            }
            if (!bounties.isEmpty()) {
                nationBounties.put(nation, bounties);
            }
        }
        return nationBounties;
    }

    public static double damageEstimate(DBNation me, int nationId, List<Double> cityInfra) {
        DBNation nation = DBNation.getById(nationId);
        if (nation == null) return 0;

        double numCities = 0;
        if (me.hasProject(Projects.MISSILE_LAUNCH_PAD)) {
            numCities += 0.5;
            if (nation.hasProject(Projects.IRON_DOME)) numCities -= 0.15;
        }
        if (me.hasProject(Projects.NUCLEAR_RESEARCH_FACILITY)) {
            numCities += 1.5;
            if (nation.hasProject(Projects.VITAL_DEFENSE_SYSTEM)) numCities -= 0.375;
        }
        if (nation.getGroundStrength(true, false) < me.getGroundStrength(true, false) * 0.4) {
            numCities++;
            if (nation.getAircraft() <= me.getAircraft()) numCities += 5;
        }
        if (nation.getShips() <= 1 && me.getShips() > 1) numCities += 0.3;
        if (nation.getCities() <= me.getCities() * 0.5) numCities++;
        if (nation.active_m() > 10000) numCities += 10;

        if (numCities == 0) return 0;

        double cost = 0;
        Collections.sort(cityInfra);
        int i = cityInfra.size() - 1;
        while (i >= 0 && numCities > 0) {
            Double infra = cityInfra.get(i);
            if (infra <= 600) break;
            double factor = Math.min(numCities, 1);
            double minInfra = infra * 0.6 - 500;
            double beigeFactor = nation.getBeigeDamageFactor();
            if (beigeFactor != 1) minInfra = infra - ((infra - minInfra) * beigeFactor);
            cost += factor * PW.City.Infra.calculateInfra(minInfra, infra);

            i--;
            numCities--;
        }
        return cost;
    }

    public static final class CounterChanceContext {
        private final List<DBNation> nations;
        private final Int2ObjectOpenHashMap<List<DBNation>> countersByAlliance;
        private final Int2DoubleOpenHashMap targetStrengthByNationId;
        private final Set<DBNation> nationsToBlitzWith;
        private final int maxCounterSize;
        private final double blitzStrength;

        private CounterChanceContext(List<DBNation> nations,
                                     Int2ObjectOpenHashMap<List<DBNation>> countersByAlliance,
                                     Int2DoubleOpenHashMap targetStrengthByNationId,
                                     Set<DBNation> nationsToBlitzWith,
                                     int maxCounterSize,
                                     double blitzStrength) {
            this.nations = nations;
            this.countersByAlliance = countersByAlliance;
            this.targetStrengthByNationId = targetStrengthByNationId;
            this.nationsToBlitzWith = nationsToBlitzWith;
            this.maxCounterSize = maxCounterSize;
            this.blitzStrength = blitzStrength;
        }

        public List<DBNation> nations() {
            return nations;
        }

        public Int2ObjectOpenHashMap<List<DBNation>> countersByAlliance() {
            return countersByAlliance;
        }

        public Int2DoubleOpenHashMap targetStrengthByNationId() {
            return targetStrengthByNationId;
        }

        public Set<DBNation> nationsToBlitzWith() {
            return nationsToBlitzWith;
        }

        public int maxCounterSize() {
            return maxCounterSize;
        }

        public double blitzStrength() {
            return blitzStrength;
        }
    }

    public static final class DamageTargets {
        private final Set<DBNation> nations;
        private final Map<Integer, Double> maxInfraByNation;
        private final Map<Integer, Double> damageEstByNation;
        private final Map<Integer, Double> avgInfraByNation;
        private final Map<Integer, Double> avgBeigeInfraByNation;
        private final List<String> removeNotes;

        private DamageTargets(Set<DBNation> nations, Map<Integer, Double> maxInfraByNation, Map<Integer, Double> damageEstByNation,
                              Map<Integer, Double> avgInfraByNation, Map<Integer, Double> avgBeigeInfraByNation,
                              List<String> removeNotes) {
            this.nations = nations;
            this.maxInfraByNation = maxInfraByNation;
            this.damageEstByNation = damageEstByNation;
            this.avgInfraByNation = avgInfraByNation;
            this.avgBeigeInfraByNation = avgBeigeInfraByNation;
            this.removeNotes = removeNotes;
        }

        public Set<DBNation> nations() {
            return nations;
        }

        public Map<Integer, Double> damageEstByNation() {
            return damageEstByNation;
        }

        public Map<Integer, Double> maxInfraByNation() {
            return maxInfraByNation;
        }

        public Map<Integer, Double> avgInfraByNation() {
            return avgInfraByNation;
        }

        public Map<Integer, Double> avgBeigeInfraByNation() {
            return avgBeigeInfraByNation;
        }

        public List<String> removeNotes() {
            return removeNotes;
        }

        public List<Map.Entry<DBNation, Double>> topTargets(int limit, boolean targetMeanInfra, boolean targetCityMax, boolean targetBeigeMax) {
            Map<Integer, Double> valueFunction;
            if (targetMeanInfra) valueFunction = avgInfraByNation;
            else if (targetCityMax) valueFunction = maxInfraByNation;
            else if (targetBeigeMax) valueFunction = avgBeigeInfraByNation;
            else valueFunction = damageEstByNation;
            return topDamageTargets(valueFunction, limit);
        }
    }
}