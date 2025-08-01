package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class AttackCommands {
    @Command(aliases = "groundsim", desc = """
            Simulate a ground attack with the given attacker and defender troops
            Halve the tank number if the opponent has air control
            Note: Use math via: `50/2`""", viewable = true)
    public String groundSim(@Range(min = 0) int attSoldiersUnarmed, @Range(min = 0) int attSoldiers, @Range(min = 0) int attTanks, @Range(min = 0) int defSoldiersUnarmed, @Range(min = 0) int defSoldiers, @Range(min = 0) int defTanks) {
        if (attSoldiers != 0 && attSoldiersUnarmed != 0)
            return "You cannot attack with both armed and unarmed soldiers.";
        if (defSoldiers != 0 && defSoldiersUnarmed != 0)
            return "You cannot defend with both armed and unarmed soldiers.";

        double attStr = attSoldiers * 1.7_5 + attSoldiersUnarmed + attTanks * 40;
        double defStr = defSoldiers * 1.7_5 + defSoldiersUnarmed + defTanks * 40;

        StringBuilder response = new StringBuilder("**Ground**: " + attSoldiersUnarmed + "/" + attSoldiers + "/" + attTanks + " -> " + defSoldiersUnarmed + "/" + defSoldiers + "/" + defTanks);
        if (defStr * 0.4 > attStr) {
            response.append("\n" + SuccessType.UTTER_FAILURE);
        } else {
            for (int success = 0; success <= 3; success++) {
                double odds = PW.getOdds(attStr, defStr, success);
                if (odds <= 0) continue;
                odds = Math.min(1, odds);
                String pctStr = MathMan.format(odds * 100) + "%";
                response.append("\n").append(SuccessType.values[success]).append("=").append(pctStr);
            }
        }

        int reqUnarmedIT = (int) Math.ceil(defStr * 3.4);
        int reqArmedIT = (int) Math.ceil(defStr * 3.4 / 1.7_5);

        int reqUnarmedUF = (int) Math.ceil(defStr / 3.4);
        int reqArmedUF = (int) Math.ceil(defStr / (3.4 * 1.7_5));

        response.append("""
                
                Note:
                - Tanks = 40x unarmed soldiers (22.86x armed) | Armed Soldiers = 1.75 Unarmed
                - Guaranteed IT needs 3.4x enemy (""").append(reqUnarmedIT).append(" unarmed, ").append(reqArmedIT).append(" armed)\n").append("- Guaranteed UF needs 0.29x enemy (").append(reqUnarmedUF).append(" unarmed, ").append(reqArmedUF).append(" armed)");

        return response.toString();
    }

    @Command(aliases = {"airsim", "airstrikesim", "planesim"}, desc = "Simulate an airstrike with the given attacker and defender aircraft", viewable = true)
    public String airSim(int attAircraft, int defAircraft) {
        double attStr = attAircraft;
        double defStr = defAircraft;

        StringBuilder response = new StringBuilder("**Airstrike**: " + attAircraft + " -> " + defAircraft);
        for (int success = 0; success <= 3; success++) {
            double odds = PW.getOdds(attStr, defStr, success);
            if (odds <= 0) continue;
            odds = Math.min(1, odds);
            String pctStr = MathMan.format(odds * 100) + "%";
            response.append("\n").append(SuccessType.values[success]).append("=").append(pctStr);
        }

        response.append("\n\nNote: For a guaranteed IT, you need 3.4x enemy strength.");

        return response.toString();
    }

    @Command(
            desc = "Simulate an attack between two nations and return the odds and casualties\n" +
                    "Configure fortification, air control, projects and infrastructure overrides.",
            groups = {
                    "Main Parameters",
                    "Military Units",
                    "War Policy",
                    "War Control",
                    "Projects",
                    "Infrastructure"
            }
    )
    public String casualties(
            @Arg(value = "The type of attack to perform", group = 0)
            AttackType attack,
            @Arg(value = "War declaration type", group = 0)
            WarType warType,
            @Arg(value = "Enemy nation to attack", group = 0)
            DBNation enemy,
            @Arg(value = "Primary nation (defaults to your nation)", group = 0)
            @Default("%user%")
            DBNation me,
            @Arg(value = "Swap roles: you become defender", group = 0)
            @Switch("s") boolean selfIsDefender,
            @Arg(value = "Override attacker military units", group = 1)
            @Default
            Map<MilitaryUnit, Long> attackerMilitary,
            @Arg(value = "Override defender military units", group = 1)
            @Default
            Map<MilitaryUnit, Long> defenderMilitary,
            @Arg(value="No munitions for attacker soldiers", group = 1) @Switch("ua") boolean unequipAttackerSoldiers,
            @Arg(value="No munitions for defender soldiers", group = 1) @Switch("ud") boolean unequipDefenderSoldiers,
            @Arg(value = "Attacker war policy", group = 2)
            @Switch("att_policy")
            WarPolicy attackerPolicy,
            @Arg(value = "Defender war policy", group = 2)
            @Switch("def_policy")
            WarPolicy defenderPolicy,
            @Arg(value = "Defender is fortified", group = 3)
            @Switch("f")
            boolean defFortified,
            @Arg(value = "Attacker has air control", group = 3)
            @Switch("ac")
            boolean attAirControl,
            @Arg(value = "Defender has air control", group = 3)
            @Switch("dac")
            boolean defAirControl,
            @Arg(value = "Attacker has ground control", group = 3)
            @Switch("agc")
            boolean att_ground_control,
            @Arg(value = "Attacker projects", group = 4)
            @Switch("ap")
            Set<Project> attackerProjects,
            @Arg(value = "Defender projects", group = 4)
            @Switch("dp")
            Set<Project> defenderProjects,
            @Arg(value = "Override attacker infrastructure level", group = 5)
            @Switch("ai")
            Integer attacker_infra,
            @Arg(value = "Override defender infrastructure level", group = 5)
            @Switch("di")
            Integer defender_infra
    ) {
        if (att_ground_control && attack != AttackType.GROUND) {
            throw new IllegalArgumentException("Ground control can only be used with ground attacks, not " + attack.name());
        }
        DBNation attacker = (selfIsDefender ? enemy : me).copy();
        DBNation defender = (selfIsDefender ? me : enemy).copy();
        if (attacker_infra != null) {
            attacker = new SimpleDBNation(((SimpleDBNation) attacker).getData()) {
                @Override
                public double maxCityInfra() {
                    return attacker_infra;
                }
            };
        }
        if (defender_infra != null) {
            defender = new SimpleDBNation(((SimpleDBNation) defender).getData()) {
                @Override
                public double maxCityInfra() {
                    return defender_infra;
                }
            };
        }

        if (attackerMilitary != null) {
            for (Map.Entry<MilitaryUnit, Long> entry : attackerMilitary.entrySet()) {
                attacker.setUnits(entry.getKey(), entry.getValue().intValue());
            }
        }
        if (defenderMilitary != null) {
            for (Map.Entry<MilitaryUnit, Long> entry : defenderMilitary.entrySet()) {
                defender.setUnits(entry.getKey(), entry.getValue().intValue());
            }
        }
        if (attackerPolicy != null) {
            attacker.setWarPolicy(attackerPolicy);
        }
        if (defenderPolicy != null) {
            defender.setWarPolicy(defenderPolicy);
        }
        if (attackerProjects != null) {
            attacker.setProjectsRaw(0L);
            for (Project project : attackerProjects) {
                attacker.setProject(project);
            }
        }
        if (defenderProjects != null) {
            defender.setProjectsRaw(0L);
            for (Project project : defenderProjects) {
                defender.setProject(project);
            }
        }

        StringBuilder response = new StringBuilder();
        response.append("**" + attack.name() + "**: ");

        DBNation finalDefender = defender;
        BiFunction<MilitaryUnit, Integer, Double> getCost = (unit, amount) -> {
            if (unit == MilitaryUnit.INFRASTRUCTURE) {
                double start = finalDefender.maxCityInfra();
                double end = Math.max(start - amount, 0);
                return PW.City.Infra.calculateInfra(end, start);
            }
            return unit.getConvertedCost(finalDefender::getResearch) * amount;
        };
        Function<Map.Entry<MilitaryUnit, Map.Entry<Integer, Integer>>, String> getAvgStr = (entry) -> {
            long avg = Math.round((entry.getValue().getKey() + entry.getValue().getValue()) / 2d);
            MilitaryUnit unit = entry.getKey();
            String avgValue = MathMan.format(getCost.apply(unit, (int) avg));
            String avgStr = avg + " worth $" + avgValue;
            return ("- " + entry.getKey().name()) + ("=") + ("`[" + entry.getValue().getKey()) + ("- ") + (entry.getValue().getValue()) + ("] (avg:" + avgStr + ")`\n");
        };
        Map<ResourceType, Double> attConsume = attack.getConsumption(attacker::getUnits, !unequipAttackerSoldiers);
        double attConsumeValue = attConsume.entrySet().stream().mapToDouble(e -> e.getValue() * e.getKey().getMarketValue()).sum();
        response.append("Attacker Consumption (worth: ~$" + MathMan.format(attConsumeValue) + "):\n" +
                "`" + ResourceType.toString(attConsume) + "`\n");

        switch (attack) {
            case MISSILE, NUKE: {
                response.append("\n");
                Project project = attack == AttackType.NUKE ? Projects.VITAL_DEFENSE_SYSTEM : Projects.IRON_DOME;
                if (defender.hasProject(project)) {
                    response.append("Defender has project " + Projects.IRON_DOME.name() + " (30% interception)\n");
                }
                Map.Entry<Map<MilitaryUnit, Map.Entry<Integer, Integer>>, Map<MilitaryUnit, Map.Entry<Integer, Integer>>> casualties
                        = attack.getCasualties(attacker, defender, SuccessType.IMMENSE_TRIUMPH, warType, defFortified, attAirControl, defAirControl, unequipAttackerSoldiers, unequipDefenderSoldiers, att_ground_control);
                response.append("Attacker losses: ");
                for (Map.Entry<MilitaryUnit, Map.Entry<Integer, Integer>> entry : casualties.getKey().entrySet()) {
                    response.append(getAvgStr.apply(entry));
                }
                response.append("Defender losses: ");
                for (Map.Entry<MilitaryUnit, Map.Entry<Integer, Integer>> entry : casualties.getValue().entrySet()) {
                    response.append(getAvgStr.apply(entry));
                }
                break;
            }
            case GROUND:
            case AIRSTRIKE_INFRA:
            case AIRSTRIKE_SOLDIER:
            case AIRSTRIKE_TANK:
            case AIRSTRIKE_MONEY:
            case AIRSTRIKE_SHIP:
            case AIRSTRIKE_AIRCRAFT:
            case NAVAL_INFRA:
            case NAVAL_GROUND:
            case NAVAL_AIR:
            case NAVAL: {
                double attStr = 0;
                double defStr = 0;
                if (attack == AttackType.GROUND) {
                    response.append(" (soldier: " + attacker.getSoldiers() + ", tanks: " + attacker.getTanks() +") -> (soldier: " + defender.getSoldiers() + ", tanks: " + defender.getTanks() + ")");
                    attStr = attacker.getGroundStrength(!unequipAttackerSoldiers, defAirControl);
                    defStr = defender.getGroundStrength(!unequipDefenderSoldiers, attAirControl);
                } else {
                    MilitaryUnit unit = attack.getUnits()[0];
                    response.append(attacker.getUnits(unit) + " -> " + defender.getUnits(unit));
                    attStr = attacker.getUnits(unit);
                    defStr = defender.getUnits(unit);
                }
                response.append("\n");

                for (SuccessType success : SuccessType.values) {
                    double odds = PW.getOdds(attStr, defStr, success.ordinal());
                    if (odds <= 0) continue;
                    odds = Math.min(1, odds);
                    String pctStr = MathMan.format(odds * 100) + "%";
                    response.append("\n").append(success.name()).append("=").append(pctStr).append("\n");

                    Map.Entry<Map<MilitaryUnit, Map.Entry<Integer, Integer>>, Map<MilitaryUnit, Map.Entry<Integer, Integer>>> casualties
                            = attack.getCasualties(attacker, defender, success, warType, defFortified, attAirControl, defAirControl, unequipAttackerSoldiers, unequipDefenderSoldiers, att_ground_control);
                    response.append("Attacker losses: ");
                    for (Map.Entry<MilitaryUnit, Map.Entry<Integer, Integer>> entry : casualties.getKey().entrySet()) {
                        response.append(getAvgStr.apply(entry));
                    }
                    response.append("Defender losses: ");
                    for (Map.Entry<MilitaryUnit, Map.Entry<Integer, Integer>> entry : casualties.getValue().entrySet()) {
                        response.append(getAvgStr.apply(entry));
                    }
                    response.append("\n");
                }
                break;
            }

            default:
                throw new IllegalArgumentException("Invalid attack type: " + attack);
        }

        return response.toString();
    }

    @Command(aliases = {"shipSim", "navalSim"}, desc = "Simulate a naval battle with the given attacker and defender ships", viewable = true)
    public String navalSim(int attShips, int defShips) {
        double attStr = attShips;
        double defStr = defShips;

        StringBuilder response = new StringBuilder("**Naval**: " + attShips + " -> " + defShips);
        for (int success = 0; success <= 3; success++) {
            double odds = PW.getOdds(attStr, defStr, success);
            if (odds <= 0) continue;
            odds = Math.min(1, odds);
            String pctStr = MathMan.format(odds * 100) + "%";
            response.append("\n- ").append(SuccessType.values[success]).append("=").append(pctStr);
        }

        response.append("\n\nNote: For a guaranteed IT, you need 3.4x enemy strength");

        return response.toString();
    }
}