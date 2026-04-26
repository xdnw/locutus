package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.AttackResolver;
import link.locutus.discord.sim.combat.OddsModel;
import link.locutus.discord.sim.combat.ProjectileDefenseMath;
import link.locutus.discord.sim.combat.state.CombatantView;
import link.locutus.discord.sim.combat.state.CombatantSnapshots;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.battle.CombatantViewAdapter;

import java.util.Map;
import java.util.Set;

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

        boolean equipAttackerSoldiers = attSoldiers != 0;
        boolean equipDefenderSoldiers = defSoldiers != 0;

        CombatantView attacker = syntheticCombatant(1, attSoldiers + attSoldiersUnarmed, attTanks, 0, 0);
        CombatantView defender = syntheticCombatant(2, defSoldiers + defSoldiersUnarmed, defTanks, 0, 0);
        String legalityError = attackLegalityError(attacker, AttackType.GROUND);
        if (legalityError != null) {
            return legalityError;
        }
        AttackResolver.Flags flags = AttackResolver.Flags.relative(
            false,
            false,
            false,
            false,
            equipAttackerSoldiers,
            equipDefenderSoldiers
        );

        double[] oddsVector = AttackResolver.oddsVector(
                attacker,
                defender,
                AttackType.GROUND,
            WarType.RAID,
            flags,
                OddsModel.DEFAULT
        );

        StringBuilder response = new StringBuilder("**Ground**: " + attSoldiersUnarmed + "/" + attSoldiers + "/" + attTanks + " -> " + defSoldiersUnarmed + "/" + defSoldiers + "/" + defTanks);
        if (oddsVector[SuccessType.UTTER_FAILURE.ordinal()] >= 0.999999d) {
            response.append("\n" + SuccessType.UTTER_FAILURE);
        } else {
            for (SuccessType success : SuccessType.values) {
                double odds = oddsVector[success.ordinal()];
                if (odds <= 0) continue;
                odds = Math.min(1, odds);
                String pctStr = MathMan.format(odds * 100) + "%";
                response.append("\n").append(success).append("=").append(pctStr);
            }
        }

        double defStr = (equipDefenderSoldiers ? defSoldiers * 1.75d : defSoldiersUnarmed) + defTanks * 40d;

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
        CombatantView attacker = syntheticCombatant(1, 0, 0, attAircraft, 0);
        CombatantView defender = syntheticCombatant(2, 0, 0, defAircraft, 0);
        String legalityError = attackLegalityError(attacker, AttackType.AIRSTRIKE_AIRCRAFT);
        if (legalityError != null) {
            return legalityError;
        }

        double[] oddsVector = AttackResolver.oddsVector(
                attacker,
                defender,
                AttackType.AIRSTRIKE_AIRCRAFT,
            WarType.RAID,
            AttackResolver.Flags.defaults(),
                OddsModel.DEFAULT
        );

        StringBuilder response = new StringBuilder("**Airstrike**: " + attAircraft + " -> " + defAircraft);
        for (SuccessType success : SuccessType.values) {
            double odds = oddsVector[success.ordinal()];
            if (odds <= 0) continue;
            odds = Math.min(1, odds);
            String pctStr = MathMan.format(odds * 100) + "%";
            response.append("\n").append(success).append("=").append(pctStr);
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

        CombatantView attackerView = CombatantViewAdapter.of(attacker, attacker_infra == null ? null : attacker_infra.doubleValue());
        CombatantView defenderView = CombatantViewAdapter.of(defender, defender_infra == null ? null : defender_infra.doubleValue());
        String legalityError = attackLegalityError(attackerView, attack);
        if (legalityError != null) {
            throw new IllegalArgumentException(legalityError);
        }
        AttackResolver.Flags flags = AttackResolver.Flags.relative(
            attAirControl,
            defAirControl,
            att_ground_control,
            defFortified,
                !unequipAttackerSoldiers,
                !unequipDefenderSoldiers
        );

        StringBuilder response = new StringBuilder();
        response.append("**" + attack.name() + "**: ");

        AttackResolver.AttackRanges immense = AttackResolver.rangesForSuccess(
                attackerView,
                defenderView,
                attack,
            warType,
            flags,
                SuccessType.IMMENSE_TRIUMPH,
                OddsModel.DEFAULT
        );
        double[] attConsume = immense.consumption();
        double attConsumeValue = ResourceType.convertedTotal(attConsume);
        response.append("Attacker Consumption (worth: ~$" + MathMan.format(attConsumeValue) + "):\n" +
            "`" + ResourceType.toString(attConsume) + "`\n");

        switch (attack) {
            case MISSILE, NUKE: {
                response.append("\n");
                Project project = attack == AttackType.NUKE ? Projects.VITAL_DEFENSE_SYSTEM : Projects.IRON_DOME;
                if (defender.hasProject(project)) {
                    double interceptionChance = ProjectileDefenseMath.interceptionChance(attack, defender::hasProject) * 100d;
                    response.append("Defender has project " + project.name() + " ("
                            + MathMan.format(interceptionChance)
                            + "% interception)\n");
                }

                appendLossRanges(response, "Attacker losses: ", attackerView, immense.attackerLossRanges());
                appendLossRanges(response, "Defender losses: ", defenderView, immense.defenderLossRanges());
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
                if (attack == AttackType.GROUND) {
                    response.append(" (soldier: " + attacker.getSoldiers() + ", tanks: " + attacker.getTanks() +") -> (soldier: " + defender.getSoldiers() + ", tanks: " + defender.getTanks() + ")");
                } else {
                    MilitaryUnit unit = attack.getUnits()[0];
                    response.append(attacker.getUnits(unit) + " -> " + defender.getUnits(unit));
                }
                response.append("\n");

                double[] oddsVector = AttackResolver.oddsVector(
                    attackerView,
                    defenderView,
                    attack,
                    warType,
                    flags,
                    OddsModel.DEFAULT
                );
                for (SuccessType success : SuccessType.values) {
                    double odds = oddsVector[success.ordinal()];
                    if (odds <= 0) continue;
                    odds = Math.min(1, odds);
                    String pctStr = MathMan.format(odds * 100) + "%";
                    response.append("\n").append(success.name()).append("=").append(pctStr).append("\n");

                    AttackResolver.AttackRanges ranges = AttackResolver.rangesForSuccess(
                            attackerView,
                            defenderView,
                            attack,
                            warType,
                            flags,
                            success,
                            OddsModel.DEFAULT
                    );
                    appendLossRanges(response, "Attacker losses: ", attackerView, ranges.attackerLossRanges());
                    appendLossRanges(response, "Defender losses: ", defenderView, ranges.defenderLossRanges());
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
        CombatantView attacker = syntheticCombatant(1, 0, 0, 0, attShips);
        CombatantView defender = syntheticCombatant(2, 0, 0, 0, defShips);
        String legalityError = attackLegalityError(attacker, AttackType.NAVAL);
        if (legalityError != null) {
            return legalityError;
        }

        double[] oddsVector = AttackResolver.oddsVector(
                attacker,
                defender,
                AttackType.NAVAL,
            WarType.RAID,
            AttackResolver.Flags.defaults(),
                OddsModel.DEFAULT
        );

        StringBuilder response = new StringBuilder("**Naval**: " + attShips + " -> " + defShips);
        for (SuccessType success : SuccessType.values) {
            double odds = oddsVector[success.ordinal()];
            if (odds <= 0) continue;
            odds = Math.min(1, odds);
            String pctStr = MathMan.format(odds * 100) + "%";
            response.append("\n").append(success).append("=").append(pctStr);
        }

        response.append("\n\nNote: For a guaranteed IT, you need 3.4x enemy strength");

        return response.toString();
    }

    private static void appendLossRanges(
            StringBuilder response,
            String header,
            CombatantView owner,
            Map<MilitaryUnit, Map.Entry<Integer, Integer>> ranges
    ) {
        response.append(header);
        if (ranges == null || ranges.isEmpty()) {
            response.append("none\n");
            return;
        }
        for (Map.Entry<MilitaryUnit, Map.Entry<Integer, Integer>> entry : ranges.entrySet()) {
            response.append(formatRange(owner, entry));
        }
    }

    private static String formatRange(
            CombatantView owner,
            Map.Entry<MilitaryUnit, Map.Entry<Integer, Integer>> entry
    ) {
        MilitaryUnit unit = entry.getKey();
        int min = entry.getValue().getKey();
        int max = entry.getValue().getValue();
        long avg = Math.round((min + max) / 2d);
        String avgValue = MathMan.format(lossValue(owner, unit, avg));
        return "- " + unit.name() + "=`[" + min + " - " + max + "] (avg:" + avg + " worth $" + avgValue + ")`\n";
    }

    private static double lossValue(CombatantView owner, MilitaryUnit unit, long amount) {
        if (amount <= 0) {
            return 0d;
        }
        if (unit == MilitaryUnit.INFRASTRUCTURE) {
            double start = owner.maxCityInfra();
            double end = Math.max(0d, start - amount);
            return PW.City.Infra.calculateInfra(end, start);
        }
        if (unit == MilitaryUnit.MONEY) {
            return amount;
        }
        return owner.getUnitConvertedCost(unit) * amount;
    }

    private static String attackLegalityError(CombatantView attacker, AttackType attackType) {
        if (CombatKernel.canUseAttackType(attacker, attackType)) {
            return null;
        }
        return "Attacker cannot use " + attackType.name() + " with current units/projects.";
    }

    private static CombatantView syntheticCombatant(
            int nationId,
            int soldiers,
            int tanks,
            int aircraft,
            int ships
    ) {
        return CombatantSnapshots.syntheticSingleCity(nationId, soldiers, tanks, aircraft, ships);
    }
}