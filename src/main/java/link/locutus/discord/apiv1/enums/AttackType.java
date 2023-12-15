package link.locutus.discord.apiv1.enums;

import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.db.entities.DBNation;

import java.util.*;
import java.util.function.Function;

public enum AttackType {
    GROUND(3, 10, MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT),
    VICTORY(0, 0),
    FORTIFY(3, 0),
    A_LOOT("Alliance Loot", 0, 0),
    AIRSTRIKE_INFRA("Airstrike Infrastructure", 4, 12, MilitaryUnit.AIRCRAFT), // infra
    AIRSTRIKE_SOLDIER("Airstrike Soldiers", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.SOLDIER),
    AIRSTRIKE_TANK("Airstrike Tanks", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.TANK),
    AIRSTRIKE_MONEY("Airstrike Money", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.MONEY),
    AIRSTRIKE_SHIP("Airstrike Ships", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP),
    AIRSTRIKE_AIRCRAFT("Dogfight", 4, 12, MilitaryUnit.AIRCRAFT), // airstrike aircraft
    NAVAL(4, 14, MilitaryUnit.SHIP),
    PEACE(0, 0),
    MISSILE(8, 18, MilitaryUnit.MISSILE),
    NUKE(12, 25, MilitaryUnit.NUKE),
    ;

    @Override
    public String toString() {
        return name;
    }

    public static AttackType fromV3(com.politicsandwar.graphql.model.AttackType v3) {
        switch (v3) {
            case AIRVINFRA:
                return AIRSTRIKE_INFRA;
            case AIRVSOLDIERS:
                return AIRSTRIKE_SOLDIER;
            case AIRVTANKS:
                return AIRSTRIKE_TANK;
            case AIRVMONEY:
                return AIRSTRIKE_MONEY;
            case AIRVSHIPS:
                return AIRSTRIKE_SHIP;
            case AIRVAIR:
                return AIRSTRIKE_AIRCRAFT;
            case GROUND:
                return GROUND;
            case MISSILE:
                return MISSILE;
            case MISSILEFAIL:
                return MISSILE;
            case NUKE:
                return NUKE;
            case NUKEFAIL:
                return NUKE;
            case NAVAL:
                return NAVAL;
            case FORTIFY:
                return FORTIFY;
            case PEACE:
                return PEACE;
            case VICTORY:
                return VICTORY;
            case ALLIANCELOOT:
                return A_LOOT;
            default:
                throw new IllegalStateException("No v3 attack type found");
        }
    }

    public static Map.Entry<Map.Entry<Integer, Integer>, Map.Entry<Integer, Integer>> getAirstrikeCasualties(int attUnits, int defUnits, SuccessType victory, boolean airVsAir, double attModifier, double defModifier) {
        double attStr = attUnits * 3;
        double defStr = defUnits * 3;
        int minAtt = 0;
        int minDef = 0;
        int maxAtt = 0;
        int maxDef = 0;

        double attCasualties, defCasualties;
        if (airVsAir) {
            attCasualties = 0.01;
            defCasualties = 0.018337;
        } else {
            attCasualties = 0.015385;
            defCasualties = 0.009091;
        }

        int failures = 3 - victory.ordinal();
        int successes = victory.ordinal();
        for (int i = 0; i < failures; i++) {
            maxAtt += defStr * attCasualties * attModifier;
            minAtt += Math.max(defStr * 0.4, attStr * 0.4 + 1) * attCasualties * attModifier;

            maxDef += Math.min(attStr, defStr - 1) * defCasualties * defModifier;
            minDef += attStr * 0.4 * defCasualties * defModifier;
        }
        for (int i = 0; i < successes; i++) {
            maxAtt += Math.min(defStr, attStr - 1) * attCasualties * attModifier;
            minAtt += defStr * 0.4 * attCasualties * attModifier;

            maxDef += attStr * defCasualties * defModifier;
            minDef += Math.max(attStr * 0.4, defStr * 0.4 + 1) * defCasualties * defModifier;
        }

        minAtt = Math.min(attUnits, minAtt);
        maxAtt = Math.min(attUnits, maxAtt);
        minDef = Math.min(defUnits, minDef);
        maxDef = Math.min(defUnits, maxDef);

        return Map.entry(
                Map.entry((int) minAtt, (int) maxAtt),
                Map.entry((int) minDef, (int) maxDef)
        );
    }

    public static Map.Entry<Double, Double> getAirInfraCasualties(int attAir, int defAir, boolean airVsInfra, SuccessType success, double infra) {
        int victoryType = success.ordinal();
        if (victoryType == 0) return Map.entry(0d, 0d);
        double min = Math.max(Math.min(attAir - (defAir * 0.5) * 0.35353535 * 0.85 * (victoryType / 3d), infra + 100), 0);
        double max = Math.max(Math.min(attAir - (defAir * 0.5) * 0.35353535 * 1.05 * (victoryType / 3d), infra + 100), 0);
        if (!airVsInfra) {
            min /= 3;
            max /= 3;
        }
        return Map.entry(min, max);
    }

    private Map.Entry<Integer, Integer> getAirTargetCasualties(MilitaryUnit unit, double killRatio, int baseUnits, double baseFactor, int attAir, int defAir, Function<MilitaryUnit, Integer> defenderUnits, SuccessType victory, double defModifier) {
        if (victory == SuccessType.UTTER_FAILURE) return Map.entry(0, 0);
        double victoryFactor = victory.ordinal() == 0 ? 0 : victory.ordinal() == 1 ? 0.4 : victory.ordinal() == 2 ? 0.7 : 1;
        int enemyUnits = defenderUnits.apply(unit);
        double randMin = (attAir - defAir * 0.5) * killRatio * 0.85 * defModifier;
        double randMax = (attAir - defAir * 0.5) * killRatio * 1.05 * defModifier;
        long upperBound = Math.round(Math.max(Math.min(enemyUnits, enemyUnits * baseFactor + baseUnits), 0));
        int min = (int) (Math.min(upperBound, randMin) * victoryFactor);
        int max = (int) (Math.min(upperBound, randMax) * victoryFactor);
        return Map.entry(min, max);
    }
    private void inputAirCasualties(Map<MilitaryUnit, Map.Entry<Integer, Integer>> attackerCasualties, Map<MilitaryUnit, Map.Entry<Integer, Integer>> defenderCasualties, DBNation attacker, DBNation defender, SuccessType victory, double infraModifier, double attModifier, double defModifier) {
        boolean vsInfra = this == AIRSTRIKE_INFRA;
        boolean vsAir = this == AIRSTRIKE_AIRCRAFT;

        int attAir = attacker.getUnits(MilitaryUnit.AIRCRAFT);
        int defAir = defender.getUnits(MilitaryUnit.AIRCRAFT);
        Map.Entry<Map.Entry<Integer, Integer>, Map.Entry<Integer, Integer>> airCasualties = getAirstrikeCasualties(attAir, defAir, victory, vsAir, attModifier, defModifier);
        attackerCasualties.put(MilitaryUnit.AIRCRAFT, airCasualties.getKey());
        defenderCasualties.put(MilitaryUnit.AIRCRAFT, airCasualties.getValue());

        if (victory != SuccessType.UTTER_FAILURE) {
            int infra = defender.getUnits(MilitaryUnit.INFRASTRUCTURE);
            Map.Entry<Double, Double> infraCasualties = getAirInfraCasualties(attAir, defAir, vsInfra, victory, infra);
            attackerCasualties.put(MilitaryUnit.INFRASTRUCTURE, Map.entry((int) Math.round(infraCasualties.getKey() * infraModifier), (int) Math.round(infraCasualties.getValue() * infraModifier)));
            double victoryFactor = victory.ordinal() == 0 ? 0 : victory.ordinal() == 1 ? 0.4 : victory.ordinal() == 2 ? 0.7 : 1;

            switch (this) {
                case AIRSTRIKE_SOLDIER -> {
                    // soldiers
                    // Soldiers Killed = ROUND( MAX( MIN( Enemy Soldiers, Enemy Soldiers * 0.75 + 1000, (Attacking Aircraft - Defending Aircraft * 0.5) * 35 * RAND(0.85,1.05)), 0))
                    Map.Entry<Integer, Integer> killed = getAirTargetCasualties(MilitaryUnit.SOLDIER, 35, 1000, 0.75, attAir, defAir, defender::getUnits, victory, defModifier);
                    defenderCasualties.put(MilitaryUnit.SOLDIER, killed);
                }
                case AIRSTRIKE_TANK -> {
                    // tanks
                    Map.Entry<Integer, Integer> killed = getAirTargetCasualties(MilitaryUnit.TANK, 1.25, 10, 0.75, attAir, defAir, defender::getUnits, victory, defModifier);
                    defenderCasualties.put(MilitaryUnit.TANK, killed);
                }
                case AIRSTRIKE_MONEY -> {
                    // money
                    int min = (int) (attAir * 0.85 * 3000 * victoryFactor);
                    int max = (int) (attAir * 1.05 * 3000 * victoryFactor);
                    defenderCasualties.put(MilitaryUnit.MONEY, Map.entry(min, max));
                }
                case AIRSTRIKE_SHIP -> {
                    // ships
                    Map.Entry<Integer, Integer> killed = getAirTargetCasualties(MilitaryUnit.SHIP, 0.0285, 4, 0.5, attAir, defAir, defender::getUnits, victory, defModifier);
                    defenderCasualties.put(MilitaryUnit.SHIP, killed);
                }
            }
        }
    }

    @Command(desc = "Get the minimum unit casualties for the attacker.")
    public int getAttackerMinCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers) {
        return getCasualties(attacker, defender, victory, warType, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers)
                .getKey().getOrDefault(unit, Map.entry(0, 0)).getKey();
    }

    @Command(desc = "Get the maximum unit casualties for the attacker.")
    public int getAttackerMaxCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers) {
        return getCasualties(attacker, defender, victory, warType, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers)
                .getKey().getOrDefault(unit, Map.entry(0, 0)).getValue();
    }

    @Command(desc = "Get the average unit casualties for the attacker.")
    public int getAttackerAvgCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers) {
        Map.Entry<Integer, Integer> pair = getCasualties(attacker, defender, victory, warType, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers)
                .getKey().getOrDefault(unit, Map.entry(0, 0));
        return (int) Math.round((pair.getKey() + pair.getValue()) / 2d);
    }

    @Command(desc = "Get the minimum unit casualties for the defender.")
    public int getDefenderMinCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers) {
        return getCasualties(attacker, defender, victory, warType, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers)
                .getValue().getOrDefault(unit, Map.entry(0, 0)).getKey();
    }

    @Command(desc = "Get the maximum unit casualties for the defender.")
    public int getDefenderMaxCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers) {
        return getCasualties(attacker, defender, victory, warType, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers)
                .getValue().getOrDefault(unit, Map.entry(0, 0)).getValue();
    }

    @Command(desc = "Get the average unit casualties for the defender.")
    public int getDefenderAvgCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers) {
        Map.Entry<Integer, Integer> pair = getCasualties(attacker, defender, victory, warType, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers)
                .getValue().getOrDefault(unit, Map.entry(0, 0));
        return (int) Math.round((pair.getKey() + pair.getValue()) / 2d);
    }

    public Map.Entry<Map<MilitaryUnit, Map.Entry<Integer, Integer>>, Map<MilitaryUnit, Map.Entry<Integer, Integer>>> getCasualties(DBNation attacker, DBNation defender, SuccessType victory, WarType type, boolean defAirControl, boolean attAirControl, boolean defFortified, boolean equipAttackerSoldiers, boolean equipDefenderSoldiers) {
        Map<MilitaryUnit, Map.Entry<Integer, Integer>> attackerCasualties = new HashMap<>();
        Map<MilitaryUnit, Map.Entry<Integer, Integer>> defenderCasualties = new HashMap<>();

        double infraFactor = (1 + (attacker.infraAttackModifier(this) - 1) + (defender.infraDefendModifier(this) - 1)) * (type.infraModifier());
        double defModifier = attacker.isBlitzkrieg() ? 1.1 : 1;
        double attModifier = defFortified ? 1.33 : 1;

        switch (this) {
            case NAVAL -> {
                int attShips = attacker.getUnits(MilitaryUnit.SHIP);
                int defShips = defender.getUnits(MilitaryUnit.SHIP);
                if (victory != SuccessType.UTTER_FAILURE) {
                    double maxCityInfra = defender.maxCityInfra();
                    double infraMin = Math.max(Math.min(attShips - (defShips * 0.5) * 2.625 * 0.85 * (victory.ordinal() / 3d), maxCityInfra * 0.5 + 25), 0);
                    double infraMax = Math.max(Math.min(attShips - (defShips * 0.5) * 2.625 * 1.05 * (victory.ordinal() / 3d), maxCityInfra * 0.5 + 25), 0);
                    defenderCasualties.put(MilitaryUnit.INFRASTRUCTURE, Map.entry((int) Math.round(infraMin * infraFactor), (int) Math.round(infraMax * infraFactor)));
                }
                int failures = 3 - victory.ordinal();
                int successes = victory.ordinal();
                int attStr = attShips * 4;
                int defStr = defShips * 4;
                int attLossMin = 0;
                int attLossMax = 0;
                int defLossMin = 0;
                int defLossMax = 0;
                if (failures > 0) {
                    double attStrMin = attStr * 0.4;
                    double attStrMax = Math.min(attStr, defStr - 1);

                    double defStrMin = Math.max(attStr * 0.4 + 1, defStr * 0.4);
                    double defStrMax = defStr;

                    attLossMin += (int) Math.round((defStrMin * 0.01375 * attModifier) * failures);
                    attLossMax += (int) Math.round((defStrMax * 0.01375 * attModifier) * failures);
                    defLossMin += (int) Math.round((attStrMin * 0.01375 * defModifier) * failures);
                    defLossMax += (int) Math.round((attStrMax * 0.01375 * defModifier) * failures);
                }
                if (successes > 0) {
                    double attStrMin = Math.max(defStr * 0.4 + 1, attStr * 0.4);
                    double attStrMax = attStr;

                    double defStrMin = defStr * 0.4;
                    double defStrMax = Math.min(defStr, attStr - 1);

                    attLossMin += (int) Math.round((defStrMin * 0.01375 * attModifier) * successes);
                    attLossMax += (int) Math.round((defStrMax * 0.01375 * attModifier) * successes);
                    defLossMin += (int) Math.round((attStrMin * 0.01375 * defModifier) * successes);
                    defLossMax += (int) Math.round((attStrMax * 0.01375 * defModifier) * successes);
                }

                attLossMin = Math.min(attLossMin, attShips);
                attLossMax = Math.min(attLossMax, attShips);
                defLossMin = Math.min(defLossMin, defShips);
                defLossMax = Math.min(defLossMax, defShips);

                attackerCasualties.put(MilitaryUnit.SHIP, Map.entry(attLossMin, attLossMax));
                defenderCasualties.put(MilitaryUnit.SHIP, Map.entry(defLossMin, defLossMax));
            }
            case GROUND -> {
                if (victory != SuccessType.UTTER_FAILURE) {
                    double lootFactor = (1 + (attacker.looterModifier(true) - 1) + (defender.lootModifier() - 1)) * (type.lootModifier());
                    double soldiersStoleMoney = attacker.getSoldiers() * 1.1;
                    double tankStoleMoney = attacker.getTanks() * 25.15;
                    double minStolen = (soldiersStoleMoney + tankStoleMoney) * victory.ordinal() * 0.8 * lootFactor;
                    double maxStolen = (soldiersStoleMoney + tankStoleMoney) * victory.ordinal() * 1.1 * lootFactor;
                    defenderCasualties.put(MilitaryUnit.MONEY, Map.entry((int) Math.round(minStolen), (int) Math.round(maxStolen)));

                    double maxCityInfra = defender.maxCityInfra();
                    double infraMin = Math.max(Math.min(((attacker.getSoldiers() - (defender.getSoldiers() * 0.5)) * 0.000606061 + (attacker.getTanks() - (defender.getTanks() * 0.5)) * 0.01) * 0.85 * (victory.ordinal() / 3d), maxCityInfra * 0.2 + 25), 0);
                    double infraMax = Math.max(Math.min(((attacker.getSoldiers() - (defender.getSoldiers() * 0.5)) * 0.000606061 + (attacker.getTanks() - (defender.getTanks() * 0.5)) * 0.01) * 1.05 * (victory.ordinal() / 3d), maxCityInfra * 0.2 + 25), 0);
                    defenderCasualties.put(MilitaryUnit.INFRASTRUCTURE, Map.entry((int) Math.round(infraMin * infraFactor), (int) Math.round(infraMax * infraFactor)));
                }

                double attStrS = attacker.getSoldiers() * (equipAttackerSoldiers ? 1.7_5 : 1);
                double attStrT = attacker.getTanks() * 40 * (defAirControl ? 0.5 : 1);
                double attStr = attStrS + attStrT;
                double defStrS = defender.getSoldiers() * (equipDefenderSoldiers ? 1.7_5 : 1);
                double defStrT = defender.getTanks() * 40 * (attAirControl ? 0.5 : 1);
                double defStr = defStrS + defStrT;

                double attSoldierLossMin = 0;
                double attSoldierLossMax = 0;
                double defSoldierLossMin = 0;
                double defSoldierLossMax = 0;
                double attTankLossMin = 0;
                double attTankLossMax = 0;
                double defTankLossMin = 0;
                double defTankLossMax = 0;

                int failures = 3 - victory.ordinal();
                int successes = victory.ordinal();
                if (failures > 0) {
                    double attStrMin = attStr * 0.4;
                    double attStrMax = Math.min(attStr, defStr - 1);

                    double defStrMin = Math.max(attStr * 0.4 + 1, defStr * 0.4);
                    double defStrMax = defStr;

                    double attStrMinS = attStrS * attStrMin / attStr;
                    double attStrMaxS = attStrS * attStrMax / attStr;
                    double defStrMinS = defStrS * defStrMin / defStr;
                    double defStrMaxS = defStrS * defStrMax / defStr;
                    double attStrMinT = attStrT * attStrMin / attStr;
                    double attStrMaxT = attStrT * attStrMax / attStr;
                    double defStrMinT = defStrT * defStrMin / defStr;
                    double defStrMaxT = defStrT * defStrMax / defStr;

                    attSoldierLossMin += ((defStrMinS * 0.0084) + (defStrMinT * 0.0092)) * failures;
                    attSoldierLossMax += ((defStrMaxS * 0.0084) + (defStrMaxT * 0.0092)) * failures;
                    defSoldierLossMin += ((attStrMinS * 0.0084) + (attStrMinT * 0.0092)) * failures;
                    defSoldierLossMax += ((attStrMaxS * 0.0084) + (attStrMaxT * 0.0092)) * failures;
                    attTankLossMin += ((defStrMinS * 0.00043225806) + (defStrMinT * 0.00070967741)) * failures;
                    attTankLossMax += ((defStrMaxS * 0.00043225806) + (defStrMaxT * 0.00070967741)) * failures;
                    defTankLossMin += ((attStrMinS * 0.0004060606) + (attStrMinT * 0.00066666666)) * failures;
                    defTankLossMax += ((attStrMaxS * 0.0004060606) + (attStrMaxT * 0.00066666666)) * failures;
                }
                if (successes > 0) {
                    double attStrMin = Math.max(defStr * 0.4 + 1, attStr * 0.4);
                    double attStrMax = attStr;

                    double defStrMin = defStr * 0.4;
                    double defStrMax = Math.min(defStr, attStr - 1);

                    double attStrMinS = attStrS * attStrMin / attStr;
                    double attStrMaxS = attStrS * attStrMax / attStr;
                    double defStrMinS = defStrS * defStrMin / defStr;
                    double defStrMaxS = defStrS * defStrMax / defStr;
                    double attStrMinT = attStrT * attStrMin / attStr;
                    double attStrMaxT = attStrT * attStrMax / attStr;
                    double defStrMinT = defStrT * defStrMin / defStr;
                    double defStrMaxT = defStrT * defStrMax / defStr;

                    attSoldierLossMin += ((defStrMinS * 0.0084) + (defStrMinT * 0.0092)) * successes;
                    attSoldierLossMax += ((defStrMaxS * 0.0084) + (defStrMaxT * 0.0092)) * successes;
                    defSoldierLossMin += ((attStrMinS * 0.0084) + (attStrMinT * 0.0092)) * successes;
                    defSoldierLossMax += ((attStrMaxS * 0.0084) + (attStrMaxT * 0.0092)) * successes;

                    attTankLossMin += ((defStrMinS * 0.0004060606) + (defStrMinT * 0.00066666666)) * successes;
                    attTankLossMax += ((defStrMaxS * 0.0004060606) + (defStrMaxT * 0.00066666666)) * successes;
                    defTankLossMin += ((attStrMinS * 0.00043225806) + (attStrMinT * 0.00070967741)) * successes;
                    defTankLossMax += ((attStrMaxS * 0.00043225806) + (attStrMaxT * 0.00070967741)) * successes;
                }

                defSoldierLossMin = Math.min(defender.getSoldiers(), defSoldierLossMin * defModifier);
                defSoldierLossMax = Math.min(defender.getSoldiers(), defSoldierLossMax * defModifier);
                defTankLossMin = Math.min(defender.getTanks(), defTankLossMin * defModifier);
                defTankLossMax = Math.min(defender.getTanks(), defTankLossMax * defModifier);
                attSoldierLossMin = Math.min(attacker.getSoldiers(), attSoldierLossMin * attModifier);
                attSoldierLossMax = Math.min(attacker.getSoldiers(), attSoldierLossMax * attModifier);
                attTankLossMin = Math.min(attacker.getTanks(), attTankLossMin * attModifier);
                attTankLossMax = Math.min(attacker.getTanks(), attTankLossMax * attModifier);

                defenderCasualties.put(MilitaryUnit.SOLDIER, Map.entry((int) Math.round(defSoldierLossMin), (int) Math.round(defSoldierLossMax)));
                defenderCasualties.put(MilitaryUnit.TANK, Map.entry((int) Math.round(defTankLossMin), (int) Math.round(defTankLossMax)));
                attackerCasualties.put(MilitaryUnit.SOLDIER, Map.entry((int) Math.round(attSoldierLossMin), (int) Math.round(attSoldierLossMax)));
                attackerCasualties.put(MilitaryUnit.TANK, Map.entry((int) Math.round(attTankLossMin), (int) Math.round(attTankLossMax)));
            }
            case NUKE -> {
                if (victory == SuccessType.UTTER_FAILURE)
                attackerCasualties.put(MilitaryUnit.NUKE, Map.entry(1, 1));
                double min = 0;
                double max = 0;
                for (JavaCity city : defender.getCityMap(false).values()) {
                    Map.Entry<Integer, Integer> infraDamage = city.getMissileDamage(defender::hasProject);
                    min = Math.max(min, infraDamage.getKey());
                    max = Math.max(max, infraDamage.getValue());
                }
                defenderCasualties.put(MilitaryUnit.INFRASTRUCTURE, Map.entry((int) Math.round(min * infraFactor), (int) Math.round(max * infraFactor)));
            }
            case MISSILE -> {
                attackerCasualties.put(MilitaryUnit.MISSILE, Map.entry(1, 1));
                double min = 0;
                double max = 0;
                for (JavaCity city : defender.getCityMap(false).values()) {
                    Map.Entry<Integer, Integer> infraDamage = city.getNukeDamage(defender::hasProject);
                    min = Math.max(min, infraDamage.getKey());
                    max = Math.max(max, infraDamage.getValue());
                }
                defenderCasualties.put(MilitaryUnit.INFRASTRUCTURE, Map.entry((int) Math.round(min * infraFactor), (int) Math.round(max * infraFactor)));
            }
            case AIRSTRIKE_INFRA, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK, AIRSTRIKE_MONEY, AIRSTRIKE_SHIP, AIRSTRIKE_AIRCRAFT -> {
                inputAirCasualties(attackerCasualties, defenderCasualties, attacker, defender, victory, infraFactor, attModifier, defModifier);
            }
            default -> {
                throw new IllegalArgumentException("Cannot get casualties for " + this);
            }
        }
        return Map.entry(
                attackerCasualties,
                defenderCasualties
        );
    }

    private final MilitaryUnit[] units;
    private final String name;
    private final int mapUsed;
    private final int resistanceIT;

    AttackType(int mapUsed, int resistanceIT, MilitaryUnit... units) {
        this(null, mapUsed, resistanceIT, units);
    }

    AttackType(String name, int mapUsed, int resistanceIT, MilitaryUnit... units) {
        this.units = units;
        this.name = name == null ? name() : name;
        this.mapUsed = mapUsed;
        this.resistanceIT = resistanceIT;
    }


    @Command(desc = "Resistance points dealt for a successful attack")
    public int getResistanceIT() {
        return resistanceIT;
    }

    @Command(desc = "Resistance points dealt for an attack with the given success types")
    public int getResistance(SuccessType success) {
        if (this == MISSILE || this == NUKE) {
            return success == SuccessType.UTTER_FAILURE ? 0 : getResistanceIT();
        }
        return getResistanceIT() - (9 - success.ordinal() * 3);
    }

    @Command(desc = "The Military Action Points (MAP) used for this attack type")
    public int getMapUsed() {
        return mapUsed;
    }

    @Command(desc = "The name of this attack type")
    public String getName() {
        return name;
    }

    public static final AttackType[] values = values();

    public static AttackType get(String input) {
        if (input.charAt(input.length() - 1) == 'F') {
            return get(input.substring(0, input.length() - 1));
        }
        switch (input.toUpperCase(Locale.ROOT)) {
            case "AIRSTRIKE1": return AIRSTRIKE_INFRA;
            case "AIRSTRIKE2": return AIRSTRIKE_SOLDIER;
            case "AIRSTRIKE3": return AIRSTRIKE_TANK;
            case "AIRSTRIKE4": return AIRSTRIKE_MONEY;
            case "AIRSTRIKE5": return AIRSTRIKE_SHIP;
            case "AIRSTRIKE6": return AIRSTRIKE_AIRCRAFT;
        }
        return valueOf(input);
    }

    @Command(desc = "Get the unit type at the given index")
    public MilitaryUnit getUnitType(int index) {
        return units.length > index ? units[index] : null;
    }

    public MilitaryUnit[] getUnits() {
        return units;
    }

    public Map<MilitaryUnit, Integer> getLosses(int a, int b, int c) {
        if (a == 0 && b == 0 && c == 0) return Collections.emptyMap();
        Map<MilitaryUnit, Integer> map = new HashMap<>(2);
        if (a != 0) {
            map.put(units[0], a);
        }
        if (b != 0) {
            map.put(units[1], b);
        }
        if (c != 0) {
            map.put(units[2], c);
        }
        return map;
    }

    @Command(desc = "If this attack type is a victory")
    public boolean isVictory() {
        switch (this) {
            case VICTORY:
            case A_LOOT:
                return true;
            default:
                return false;
        }
    }

    @Command(desc = "If this attack is one that can damage the enemy")
    public boolean canDamage() {
        return this == FORTIFY || this == PEACE;
    }
}
