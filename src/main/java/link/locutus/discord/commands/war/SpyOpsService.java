package link.locutus.discord.commands.war;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Safety;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.Operation;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.PagePriority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SpyOpsService {
    private SpyOpsService() {
    }

    public static SpyOpsResult findSpyOps(DBNation attacker,
                                          GuildDB db,
                                          Collection<DBNation> candidates,
                                          Set<Operation> operations,
                                          int requiredSuccess,
                                          boolean prioritizeKills,
                                          int limit) {
        if (attacker == null) {
            return SpyOpsResult.error("Please sign in or provide an attacker nation.");
        }
        if (db == null) {
            db = attacker.getGuildDB();
        }
        if (db == null) {
            return SpyOpsResult.error("Please run this from a guild context or provide a guild database.");
        }
        if (operations == null || operations.isEmpty()) {
            return SpyOpsResult.error("Please provide at least one operation type.");
        }
        if (limit <= 0) {
            return SpyOpsResult.error("Result limit must be greater than 0.");
        }

        double minSuccess = requiredSuccess > 0 ? requiredSuccess : 50;

        Set<Integer> myEnemies = attacker.getActiveWars().stream()
                .map(dbWar -> dbWar.getAttacker_id() == attacker.getNation_id() ? dbWar.getDefender_id() : dbWar.getAttacker_id())
                .collect(Collectors.toSet());

        LinkedHashSet<DBNation> enemies = new LinkedHashSet<>();
        if (candidates != null) {
            enemies.addAll(candidates);
        }

        enemies.removeIf(nation -> nation == null
                || nation.active_m() > 2880
                || nation.getPosition() <= Rank.APPLICANT.id
                || nation.getVm_turns() > 0
                || nation.isEspionageFull()
                || (!attacker.isInSpyRange(nation) && !myEnemies.contains(nation.getNation_id())));

        if (enemies.isEmpty()) {
            return SpyOpsResult.error("No nations found (1)");
        }

        int mySpies = attacker.updateSpies(PagePriority.ESPIONAGE_ODDS_SINGLE);
        long dcTime = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - (TimeUtil.getTurn() % 12));

        List<SpyOpRecommendation> recommendations = new ArrayList<>();
        for (DBNation nation : enemies) {
            Integer enemySpies = nation.updateSpies(PagePriority.ESPIONAGE_ODDS_SINGLE, false, false);
            if (enemySpies == null || enemySpies == -1) {
                continue;
            }

            ArrayList<Operation> allowedOps = new ArrayList<>(operations);
            if (enemySpies == 0) {
                allowedOps.remove(Operation.SPIES);
            }
            if (nation.getSoldiers() == 0) {
                allowedOps.remove(Operation.SOLDIER);
            }
            if (nation.getTanks() == 0) {
                allowedOps.remove(Operation.TANKS);
            }
            if (nation.getAircraft() == 0) {
                allowedOps.remove(Operation.AIRCRAFT);
            }
            if (nation.getShips() == 0) {
                allowedOps.remove(Operation.SHIPS);
            }

            int maxMissile = MilitaryUnit.MISSILE.getMaxPerDay(nation.getCities(), nation::hasProject, f -> nation.getResearch(null, f));
            if (allowedOps.contains(Operation.MISSILE) && nation.getMissiles() > 0 && nation.getMissiles() <= maxMissile) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.MISSILE, dcTime);
                if (!purchases.isEmpty()) {
                    allowedOps.remove(Operation.MISSILE);
                }
            }

            int maxNukes = MilitaryUnit.NUKE.getMaxPerDay(nation.getCities(), nation::hasProject, f -> nation.getResearch(null, f));
            if (allowedOps.contains(Operation.NUKE) && nation.getNukes() > 0 && nation.getNukes() <= maxNukes) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.NUKE, dcTime);
                if (!purchases.isEmpty()) {
                    allowedOps.remove(Operation.NUKE);
                }
            }

            if (allowedOps.isEmpty()) {
                continue;
            }

            Map.Entry<Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(
                    !prioritizeKills,
                    mySpies,
                    nation,
                    attacker.hasProject(Projects.SPY_SATELLITE),
                    allowedOps.toArray(new Operation[0]));
            if (best == null) {
                continue;
            }

            Operation operation = best.getKey();
            int safety = best.getValue().getKey();
            double damage = best.getValue().getValue();
            if (nation.hasProject(Projects.INTELLIGENCE_AGENCY)) {
                damage *= 2;
            }
            if (nation.hasProject(Projects.SPY_SATELLITE)) {
                damage *= 2;
            }

            int spiesUsed = mySpies;
            if (operation != Operation.SPIES) {
                spiesUsed = SpyCount.getRecommendedSpies(spiesUsed, enemySpies, safety, operation, nation);
            }

            double kills = SpyCount.getKills(spiesUsed, nation, operation, attacker.hasProject(Projects.SPY_SATELLITE));
            double odds = SpyCount.getOdds(spiesUsed, nation.getSpies(), safety, operation, nation);
            if (odds <= minSuccess) {
                continue;
            }

            recommendations.add(new SpyOpRecommendation(
                    nation,
                    operation,
                    safety,
                    damage,
                    kills,
                    odds,
                    spiesUsed,
                    nation.getSpies()));
        }

        recommendations.sort((left, right) -> Double.compare(right.netDamage(), left.netDamage()));
        if (recommendations.isEmpty()) {
            return SpyOpsResult.error("No nations found (2)");
        }
        if (recommendations.size() > limit) {
            recommendations = new ArrayList<>(recommendations.subList(0, limit));
        }
        return SpyOpsResult.success(attacker, recommendations);
    }

    public static String toDiscordBody(SpyOpsResult result) {
        if (result.hasError()) {
            return result.message();
        }
        StringBuilder body = new StringBuilder("Results for " + result.attacker().getNation() + ":\n");
        for (SpyOpRecommendation recommendation : result.recommendations()) {
            DBNation nation = recommendation.target();
            body.append(PW.getMarkdownUrl(nation.getNation_id(), false)).append(" | ")
                    .append(PW.getMarkdownUrl(nation.getAlliance_id(), true)).append("\n")
                    .append("Op: ").append(recommendation.operation().name()).append("\n")
                    .append("Safety: ").append(Safety.byId(recommendation.safety())).append("\n")
                    .append("Enemy \uD83D\uDD0E: ").append(recommendation.enemySpies()).append("\n")
                    .append("Attacker \uD83D\uDD0E: ").append(recommendation.attackerSpiesUsed()).append("\n")
                    .append("Dmg: $").append(MathMan.format(recommendation.netDamage())).append("\n")
                    .append("Kills: ").append(MathMan.format(recommendation.kills())).append("\n")
                    .append("Success: ").append(MathMan.format(recommendation.odds())).append("%\n\n");
        }
        return body.toString();
    }

    public static IntelResult findIntelTargets(DBNation attacker,
                                               GuildDB db,
                                               Integer dnrTopX,
                                               boolean ignoreDNR,
                                               Double scoreOverride,
                                               Set<Integer> excludedNationIds,
                                               int limit) {
        if (attacker == null) {
            return IntelResult.error("Please sign in or provide an attacker nation.");
        }
        if (db == null) {
            db = attacker.getGuildDB();
        }
        if (db == null) {
            return IntelResult.error("Please run this from a guild context or provide a guild database.");
        }
        if (limit <= 0) {
            return IntelResult.error("Result limit must be greater than 0.");
        }

        double finalScore = scoreOverride == null ? attacker.getScore() : scoreOverride;
        Integer finalDnrTopX = dnrTopX;
        if (finalDnrTopX == null) {
            finalDnrTopX = db.getOrNull(GuildKey.DO_NOT_RAID_TOP_X);
            if (finalDnrTopX == null) {
                finalDnrTopX = 0;
            }
        }

        List<DBNation> enemies = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());
        Set<Integer> allies = db.getAllies(true);
        Function<DBNation, Boolean> raidList = db.getCanRaid(finalDnrTopX, true);
        Set<Integer> excluded = excludedNationIds == null ? Collections.emptySet() : new HashSet<>(excludedNationIds);
        double attackerGroundStrength = attacker.getGroundStrength(true, false);
        int attackerAircraft = attacker.getAircraft();
        int attackerShips = attacker.getShips();

        if (!ignoreDNR) {
            enemies.removeIf(f -> !raidList.apply(f));
        }

        enemies.removeIf(f -> allies.contains(f.getAlliance_id()));
        enemies.removeIf(f -> f.active_m() < 4320);
        enemies.removeIf(f -> f.getVm_turns() > 0);
        enemies.removeIf(DBNation::isBeige);
        if (attacker.getCities() > 3) {
            enemies.removeIf(f -> f.getCities() < 4 || f.getScore() < 500);
        }
        enemies.removeIf(f -> f.getDef() == 3);
        enemies.removeIf(nation ->
                nation.active_m() < 12000
                && nation.getGroundStrength(true, false) > attackerGroundStrength
                && nation.getAircraft() > attackerAircraft
                && nation.getShips() > attackerShips + 2);
        enemies.removeIf(f -> excluded.contains(f.getNation_id()));

        List<DBNation> inScoreRange = new ArrayList<>(enemies);
        inScoreRange.removeIf(f -> f.getScore() < finalScore * 0.75 || f.getScore() > finalScore * PW.WAR_RANGE_MAX_MODIFIER);
        if (inScoreRange.isEmpty()) {
            enemies.removeIf(f -> !f.isInSpyRange(attacker));
        } else {
            enemies = inScoreRange;
        }

        List<IntelRecommendation> noData = new ArrayList<>();
        List<IntelRecommendation> outDated = new ArrayList<>();
        for (DBNation enemy : enemies) {
            Map.Entry<Double, Boolean> opValue = enemy.getIntelOpValue();
            if (opValue == null) {
                continue;
            }
            IntelRecommendation recommendation = new IntelRecommendation(enemy, opValue.getKey(), opValue.getValue());
            if (opValue.getValue()) {
                outDated.add(recommendation);
            } else {
                noData.add(recommendation);
            }
        }

        noData.sort((left, right) -> Double.compare(right.intelValue(), left.intelValue()));
        outDated.sort((left, right) -> Double.compare(right.intelValue(), left.intelValue()));

        List<IntelRecommendation> combined = new ArrayList<>(noData.size() + outDated.size());
        combined.addAll(noData);
        combined.addAll(outDated);
        if (combined.isEmpty()) {
            return IntelResult.error("No results found");
        }
        if (combined.size() > limit) {
            combined = new ArrayList<>(combined.subList(0, limit));
        }
        return IntelResult.success(attacker, combined);
    }

    public record SpyOpRecommendation(DBNation target,
                                      Operation operation,
                                      int safety,
                                      double netDamage,
                                      double kills,
                                      double odds,
                                      int attackerSpiesUsed,
                                      int enemySpies) {
    }

    public record SpyOpsResult(DBNation attacker,
                               List<SpyOpRecommendation> recommendations,
                               String message) {
        public static SpyOpsResult success(DBNation attacker, List<SpyOpRecommendation> recommendations) {
            return new SpyOpsResult(attacker, Collections.unmodifiableList(new ArrayList<>(recommendations)), null);
        }

        public static SpyOpsResult error(String message) {
            return new SpyOpsResult(null, List.of(), message);
        }

        public boolean hasError() {
            return message != null;
        }
    }

    public record IntelRecommendation(DBNation target,
                                      double intelValue,
                                      boolean outdated) {
    }

    public record IntelResult(DBNation attacker,
                              List<IntelRecommendation> recommendations,
                              String message) {
        public static IntelResult success(DBNation attacker, List<IntelRecommendation> recommendations) {
            return new IntelResult(attacker, Collections.unmodifiableList(new ArrayList<>(recommendations)), null);
        }

        public static IntelResult error(String message) {
            return new IntelResult(null, List.of(), message);
        }

        public boolean hasError() {
            return message != null;
        }
    }
}