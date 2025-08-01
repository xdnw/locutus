package link.locutus.discord.db.entities;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;

import java.util.*;

public class CustomBounty {
    public int id;
    public int placedBy;
    public long date;

    public long claimedBy;

    public double[] resources;

    public Set<Integer> nations = new IntOpenHashSet();
    public Set<Integer> alliances = new IntOpenHashSet();
    public NationFilter filter2;

    public Long totalDamage;
    public Long infraDamage;
    public Long unitDamage;
    public boolean onlyOffensives;
    public Map<MilitaryUnit, Integer> unitKills;
    public Map<MilitaryUnit, Integer> unitAttacks;
    public Set<WarType> allowedWarTypes;
    public Set<WarStatus> allowedWarStatus;
    public Set<AttackType> allowedAttackTypes;
    public Set<SuccessType> allowedAttackRolls;

    public Set<DBNation> getNations() {
        Set<DBNation> nations = new HashSet<>();
        for (int id  : this.nations) {
            DBNation nation = Locutus.imp().getNationDB().getNationById(id);
            if (nation != null) {
                nations.add(nation);
            }
        }
        nations.addAll(Locutus.imp().getNationDB().getNationsByAlliance(alliances));

//        if (!nations.isEmpty()) {
//            Predicate<DBNation> cached = filter.toCached(10000);
//            nations.removeIf(cached.negate());
//        }
        return nations;
    }

    public void validateBounty() {
        Set<DBNation> nationsToTarget = getNations();

        double nationInfra = 0;
        double nationUnits = 0;
        for (DBNation nation : nationsToTarget) {
            Map<Integer, JavaCity> cityMap = nation.getCityMap(true, true, false);
            for (Map.Entry<Integer, JavaCity> cityEntry : cityMap.entrySet()) {
                JavaCity city = cityEntry.getValue();
                {
                    nationInfra += PW.City.Infra.calculateInfra(0, city.getInfra());
                }
            }
            for (MilitaryUnit unit : MilitaryUnit.values) {
                if (unit.getBuilding() == null) continue;
                nationUnits += nation.getUnits(unit) * unit.getConvertedCost(nation.getResearchBits());
            }
        }


        // if total damage greater than enemy infra value and unit value * 0.9
        if (totalDamage != null) {
            if (totalDamage < 0) {
                throw new IllegalArgumentException("Total damage cannot be negative");
            }
            if (totalDamage > (nationInfra + nationUnits) * 0.9) {
                throw new IllegalArgumentException("Total damage `$" + MathMan.format(totalDamage) + "` is high compared to enemy infra and unit value: `$" + MathMan.format((nationInfra + nationUnits)) + "`");
            }
        }

        if (infraDamage != null) {
            if (infraDamage < 0) {
                throw new IllegalArgumentException("Infra damage cannot be negative");
            }
            if (infraDamage > nationInfra * 0.9) {
                throw new IllegalArgumentException("Infra damage `$" + MathMan.format(infraDamage) + "` is high compared to enemy infra value: `$" + MathMan.format(nationInfra) + "`");
            }
        }

        if (unitDamage != null) {
            if (unitDamage < 0) {
                throw new IllegalArgumentException("Unit damage cannot be negative");
            }
            if (unitDamage > nationUnits * 0.9) {
                throw new IllegalArgumentException("Unit damage `$" + MathMan.format(unitDamage) + "` is high compared to enemy unit value: `$" + MathMan.format(nationUnits) + "`");
            }
        }

        // if value includes negatives
        for (ResourceType type : ResourceType.values) {
            if (resources[type.ordinal()] < 0) {
                throw new IllegalArgumentException("Resource " + type + " cannot be negative");
            }
        }
        // if value < 1 million
        if (ResourceType.convertedTotal(resources) < 1_000_000) {
            throw new IllegalArgumentException("Total value must be at least $1 (see: " + CM.trade.value.cmd.toSlashMention() + ")");
        }
        // if allowedWarStatus contains active war status
        if (allowedWarStatus != null && allowedWarStatus.contains(WarStatus.ACTIVE) || allowedWarStatus.contains(WarStatus.ATTACKER_OFFERED_PEACE) || allowedWarStatus.contains(WarStatus.DEFENDER_OFFERED_PEACE)) {
            throw new IllegalArgumentException("Active wars are not allowed for `allowedWarStatus`");
        }
        if (allowedWarTypes != null && allowedWarTypes.contains(WarType.NUCLEAR)) {
            throw new IllegalArgumentException("Nuclear wars are not allowed for `allowedWarTypes`. For nuclear attacks, specify `unitAttacks: nuke 1` OR use `allowedAttackTypes: NUKE`");
        }
        List<String> warnings = new ArrayList<>();

        // if unit kills is negative
        if (unitKills != null) {
            for (Map.Entry<MilitaryUnit, Integer> entry : unitKills.entrySet()) {
                if (entry.getValue() < 0) {
                    throw new IllegalArgumentException("Unit " + entry.getKey() + " kills cannot be negative");
                }
                if (entry.getValue() > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Unit " + entry.getKey() + " kills cannot be greater than " + Integer.MAX_VALUE);
                }

                int totalUnits = 0;
                for (DBNation enemies : nationsToTarget) {
                    totalUnits += enemies.getUnits(entry.getKey());
                }

                if (entry.getValue() > totalUnits) {
                    throw new IllegalArgumentException("Unit " + entry.getKey() + " kills cannot be greater than the total number of enemy units: " + totalUnits);
                }

                switch (entry.getKey()) {
                    case SOLDIER -> {
                        // if attack units is not null and does not include soldiers, tanks, or planes
                        if (unitAttacks != null && !unitAttacks.containsKey(MilitaryUnit.SOLDIER) && !unitAttacks.containsKey(MilitaryUnit.TANK) && !unitAttacks.containsKey(MilitaryUnit.AIRCRAFT)) {
                            throw new IllegalArgumentException("`unitKills` with " + MilitaryUnit.SOLDIER + " is specified, but `unitAttacks` does not allow for " + MilitaryUnit.SOLDIER + ", " + MilitaryUnit.TANK + ", or " + MilitaryUnit.AIRCRAFT);
                        }
                        // if attack types is not null and does not include ground or airstrike soldiers
                        if (allowedAttackTypes != null && !allowedAttackTypes.contains(AttackType.GROUND) && !allowedAttackTypes.contains(AttackType.AIRSTRIKE_SOLDIER)) {
                            throw new IllegalArgumentException("`unitKills` with " + MilitaryUnit.SOLDIER + " is specified, but `allowedAttackTypes` does not allow for " + AttackType.GROUND + " or " + AttackType.AIRSTRIKE_SOLDIER);
                        }
                    }
                    case TANK -> {
                        // if attack units is not null and does not include soldiers, tanks, or planes
                        if (unitAttacks != null && !unitAttacks.containsKey(MilitaryUnit.SOLDIER) && !unitAttacks.containsKey(MilitaryUnit.TANK) && !unitAttacks.containsKey(MilitaryUnit.AIRCRAFT)) {
                            throw new IllegalArgumentException("`unitKills` with " + MilitaryUnit.TANK + " is specified, but `unitAttacks` does not allow for " + MilitaryUnit.SOLDIER + ", " + MilitaryUnit.TANK + ", or " + MilitaryUnit.AIRCRAFT);
                        }
                        // if attack types is not null and does not include ground or airstrike tanks
                        if (allowedAttackTypes != null && !allowedAttackTypes.contains(AttackType.GROUND) && !allowedAttackTypes.contains(AttackType.AIRSTRIKE_TANK)) {
                            throw new IllegalArgumentException("`unitKills` with " + MilitaryUnit.SOLDIER + " is specified, but `allowedAttackTypes` does not allow for " + AttackType.GROUND + " or " + AttackType.AIRSTRIKE_TANK);
                        }
                    }
                    case AIRCRAFT -> {
                        // if attack units is not null and doesnt include tanks or planes
                        if (unitAttacks != null && !unitAttacks.containsKey(MilitaryUnit.TANK) && !unitAttacks.containsKey(MilitaryUnit.AIRCRAFT)) {
                            throw new IllegalArgumentException("`unitKills` with " + MilitaryUnit.AIRCRAFT + " is specified, but `unitAttacks` does not allow for " + MilitaryUnit.TANK + " or " + MilitaryUnit.AIRCRAFT);
                        }
                        // if attack types is not null and does not include ground or airstrikes
                        if (allowedAttackTypes != null && !allowedAttackTypes.contains(AttackType.GROUND) &&
                                allowedAttackTypes.stream().filter(f ->
                                        f == AttackType.AIRSTRIKE_MONEY ||
                                                f == AttackType.AIRSTRIKE_AIRCRAFT ||
                                                f == AttackType.AIRSTRIKE_SOLDIER ||
                                                f == AttackType.AIRSTRIKE_TANK ||
                                                f == AttackType.AIRSTRIKE_SHIP ||
                                                f == AttackType.AIRSTRIKE_INFRA
                                ).findAny().isEmpty()
                        ) {
                            throw new IllegalArgumentException("`unitKills` with " + MilitaryUnit.AIRCRAFT + " is specified, but `allowedAttackTypes` does not allow for " + AttackType.GROUND + " or AIRSTRIKE");
                        }
                    }
                    case SHIP -> {
                        // if attack units is not null and doesn't include ships or planes
                        if (unitAttacks != null && !unitAttacks.containsKey(MilitaryUnit.SHIP) && !unitAttacks.containsKey(MilitaryUnit.AIRCRAFT)) {
                            throw new IllegalArgumentException("`unitKills` with " + MilitaryUnit.SHIP + " is specified, but `unitAttacks` does not allow for " + MilitaryUnit.SHIP + " or " + MilitaryUnit.AIRCRAFT);
                        }
                        // if attack types is not null and does not include airstrike ships or naval
                        if (allowedAttackTypes != null && !allowedAttackTypes.contains(AttackType.AIRSTRIKE_SHIP) && !allowedAttackTypes.contains(AttackType.NAVAL)) {
                            throw new IllegalArgumentException("`unitKills` with " + MilitaryUnit.SHIP + " is specified, but `allowedAttackTypes` does not allow for " + AttackType.AIRSTRIKE_SHIP + " or " + AttackType.NAVAL);
                        }

                    }
                    case INFRASTRUCTURE -> {
                        warnings.add("`" + entry.getKey() + "` kills only applies to attacks and not beige damage");
                    }
                    case SPIES, MONEY, NUKE, MISSILE -> {
                        throw new IllegalArgumentException("Unit " + entry.getKey() + " cannot be specified for `unitKills`");
                    }
                }
            }
        }

        int largestEnemyCity = 0;
        for (DBNation enemy : nationsToTarget) {
            largestEnemyCity = Math.max(enemy.getCities(), largestEnemyCity);
        }


        if (unitAttacks != null) {
            for (Map.Entry<MilitaryUnit, Integer> entry : unitAttacks.entrySet()) {
                if (entry.getValue() <= 0) {
                    throw new IllegalArgumentException("`unitAttacks` " + entry.getKey() + " attacks must be positive");
                }
                switch (entry.getKey()) {
                    case SOLDIER, TANK, AIRCRAFT, SHIP -> {
                        MilitaryBuilding building = entry.getKey().getBuilding();
                        int max = building.getUnitCap() * building.cap(Predicates.alwaysTrue()) * largestEnemyCity;
                        if (entry.getValue() > max * 1.25) {
                            throw new IllegalArgumentException("`unitAttacks` " + entry.getKey() + " attacks must be less than or equal to " + max);
                        }
                    }
                    case INFRASTRUCTURE, SPIES, MONEY -> {
                        throw new IllegalArgumentException("Unit " + entry.getKey() + " cannot be specified for `unitAttacks`");
                    }
                }

                if (entry.getKey() == MilitaryUnit.NUKE && entry.getValue() != 1) {
                    throw new IllegalArgumentException("Only one nuke can be specified per attack for `unitKills`");
                }
                if (entry.getKey() == MilitaryUnit.MISSILE && entry.getValue() != 1) {
                    throw new IllegalArgumentException("Only one missile can be specified per attack for `unitKills`");
                }
            }
        }


        // if attack role is IT and type is nuke or missile
        if (allowedAttackRolls != null) {
            if (!allowedAttackRolls.contains(SuccessType.PYRRHIC_VICTORY) &&
                    ((allowedAttackTypes != null && (allowedAttackTypes.contains(AttackType.NUKE) || allowedAttackTypes.contains(AttackType.MISSILE))) ||
                            (unitAttacks != null && (unitAttacks.containsKey(MilitaryUnit.NUKE) || unitAttacks.containsKey(MilitaryUnit.MISSILE))))
            ) {
                throw new IllegalArgumentException("`allowedAttackRolls` must contain " + SuccessType.PYRRHIC_VICTORY + " if `allowedAttackTypes` or `unitAttacks` contains " + AttackType.NUKE + " or " + AttackType.MISSILE);
            }
        }

        // set values if null
        if (totalDamage == null) totalDamage = 0L;
        if (infraDamage == null) infraDamage = 0L;
        if (unitDamage == null) unitDamage = 0L;
        if (unitKills == null) unitKills = new HashMap<>();
        if (unitAttacks == null) unitAttacks = new HashMap<>();
        if (allowedWarTypes == null) allowedWarTypes = new HashSet<>();
        if (allowedWarStatus == null) allowedWarStatus = new HashSet<>();
        if (allowedAttackTypes == null) allowedAttackTypes = new HashSet<>();
        if (allowedAttackRolls == null) allowedAttackRolls = new HashSet<>();
    }

//    public Map<DBWar, Set<AbstractCursor>> getWarAttacks(Set<Integer> attackerNations, Set<Integer> attackerAlliances) {
//        if (claimedBy != 0) {
//            throw new IllegalStateException("Cannot get wars for a completed bounty");
//        }
//        Set<DBWar> wars = new HashSet<>(Locutus.imp().getWarDb().getWars(alliances, nations, attackerNations, attackerAlliances, date, Long.MAX_VALUE).values());
//        if (onlyOffensives) {
//            wars.removeIf(f -> !nations.contains(f.getAttacker_id()) && !alliances.contains(f.getAttacker_aa()));
//        }
//        if (!allowedWarTypes.isEmpty()) {
//            wars.removeIf(f -> !allowedWarTypes.contains(f.getWarType()));
//        }
//        if (!allowedWarStatus.isEmpty()) {
//            wars.removeIf(f -> !allowedWarStatus.contains(f.getStatus()));
//        }
//        // get validating attacks
//        List<AbstractCursor> attacks = getAttacks(wars, attackerNations, attackerAlliances);
//
//        // remove wars that dont have attacks
//        Set<Integer> warsWithAttacks = new HashSet<>();
//        for (AbstractCursor attack : attacks) {
//            warsWithAttacks.add(attack.getWar_id());
//        }
//        wars.removeIf(f -> !warsWithAttacks.contains(f.warId));
//
//        if (filter2 != null && !filter2.getFilter().isEmpty() && wars.size() > 0) {
//            // get war snapshots
//            Set<Integer> warsToRemove = new HashSet<>();
//
//            Set<Integer> warIds = wars.stream().map(f -> f.warId).collect(Collectors.toSet());
//            Map<Integer, Set<DBNation>> warSnapshots = Locutus.imp().getNationDB().getWarSnapshots(warIds);
//
//            for (DBWar war : wars) {
//                boolean isAttacker = nations.contains(war.getAttacker_id()) || alliances.contains(war.getAttacker_aa());
//                int targetId = isAttacker ? war.getDefender_id() : war.getAttacker_id();
//
//                Set<DBNation> snapshots = warSnapshots.get(war.warId);
//                DBNation snapshotNation = DBNation.getById(targetId);
//                for (DBNation snapshot : snapshots) {
//                    if (snapshot.getNation_id() == targetId) {
//                        snapshotNation = snapshot;
//                        break;
//                    }
//                }
//                if (!filter2.test(snapshotNation)) {
//                    warsToRemove.add(war.warId);
//                }
//            }
//
//            if (!warsToRemove.isEmpty()) {
//                wars.removeIf(f -> warsToRemove.contains(f.warId));
//                attacks.removeIf(f -> warsToRemove.contains(f.getWar_id()));
//
//            }
//
//            // TODO remake filter to use the actual provided nation, not the cached nations
//
//
//            // filter on the snaspshort
//            // add cache to a set of nations
//
//
//            // remove wars that dont match snapshoty
//
//        }
//        Map<DBWar, Set<AbstractCursor>> warAttacks = new HashMap<>();
//        Map<Integer, DBWar> warsById = new HashMap<>();
//        for (DBWar war : wars) {
//            warAttacks.put(war, new HashSet<>());
//            warsById.put(war.warId, war);
//        }
//        for (AbstractCursor attack : attacks) {
//            warAttacks.get(warsById.get(attack.getWar_id())).add(attack);
//        }
//        return warAttacks;
//    }

//    private List<AbstractCursor> getAttacks(Set<DBWar> wars, Set<Integer> attackerNations, Set<Integer> attackerAlliances) {
//        List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacksByWars(wars);
//        Set<Integer> offensiveWarIds = new HashSet<>();
//        for (DBWar war : wars) {
//            if (nations.contains(war.getAttacker_id()) || alliances.contains(war.getAttacker_aa())) {
//                offensiveWarIds.add(war.warId);
//            }
//        }
//        if (!allowedAttackTypes.isEmpty() || !allowedAttackRolls.isEmpty() || !unitAttacks.isEmpty()) {
//            attacks.removeIf(f -> !offensiveWarIds.contains(f.getWar_id()));
//        }
//
//        if (!allowedAttackTypes.isEmpty()) {
//            attacks.removeIf(f -> !allowedAttackTypes.contains(f.getAttack_type()));
//        }
//        // rolls
//        if (!allowedAttackRolls.isEmpty()) {
//            attacks.removeIf(f -> !allowedAttackRolls.contains(f.getSuccess()) && f.getAttack_type() != AttackType.VICTORY && f.getAttack_type() != AttackType.A_LOOT);
//        }
//
//        if (!unitAttacks.isEmpty()) {
//            attacks.removeIf(f -> {
//                switch (f.getAttack_type()) {
//                    case GROUND -> {
//                        int amt1 = unitAttacks.getOrDefault(MilitaryUnit.SOLDIER, 0);
//                        int amt2 = unitAttacks.getOrDefault(MilitaryUnit.TANK, 0);
//                        return amt1 > f.getAttcas1() || amt2 > f.getAttcas2();
//                    }
//                    case AIRSTRIKE_INFRA,AIRSTRIKE_SOLDIER,AIRSTRIKE_TANK,AIRSTRIKE_MONEY,AIRSTRIKE_SHIP,AIRSTRIKE_AIRCRAFT -> {
//                        Integer amt = unitAttacks.get(MilitaryUnit.AIRCRAFT);
//                        return amt == null || amt > f.getAttcas1();
//                    }
//                    case NAVAL -> {
//                        Integer amt = unitAttacks.get(MilitaryUnit.SHIP);
//                        return amt == null || amt > f.getAttcas1();
//                    }
//                    case MISSILE,NUKE -> {
//                        Integer amt = unitAttacks.get(MilitaryUnit.MISSILE);
//                        return amt == null || amt != 1;
//                    }
//                }
//                return false;
//            });
//        }
//
//        return attacks;
//    }

    public void setUnitKills(Map<MilitaryUnit, Long> unitKills) {
        if (unitKills != null) {
            // long map to int map
            this.unitKills = new HashMap<>();
            for (Map.Entry<MilitaryUnit, Long> entry : unitKills.entrySet()) {
                this.unitKills.put(entry.getKey(), entry.getValue().intValue());
            }
        }
    }


    public void setUnitAttacks(Map<MilitaryUnit, Long> unitAttacks) {
        if (unitAttacks != null) {
            // long map to int map
            this.unitAttacks = new HashMap<>();
            for (Map.Entry<MilitaryUnit, Long> entry : unitAttacks.entrySet()) {
                this.unitAttacks.put(entry.getKey(), entry.getValue().intValue());
            }
        }
    }
}
