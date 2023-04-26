package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import net.dv8tion.jda.api.entities.User;

public class AttackCommands {
    @Command(aliases = "groundsim", desc = "Simulate a ground attack with the given attacker and defender troops\n" +
            "Halve the tank number if the opponent has air control\n" +
            "Note: Use math via: `50/2`")
    public String groundSim(@Range(min = 0) int attSoldiersUnarmed, @Range(min = 0) int attSoldiers, @Range(min = 0) int attTanks, @Range(min = 0) int defSoldiersUnarmed, @Range(min = 0) int defSoldiers, @Range(min = 0) int defTanks) {
        if (attSoldiers != 0 && attSoldiersUnarmed != 0)
            return "You cannot attack with both armed and unarmed soldiers.";
        if (defSoldiers != 0 && defSoldiersUnarmed != 0)
            return "You cannot defend with both armed and unarmed soldiers.";

        double attStr = attSoldiers * 1.75 + attSoldiersUnarmed + attTanks * 40;
        double defStr = defSoldiers * 1.75 + defSoldiersUnarmed + defTanks * 40;

        StringBuilder response = new StringBuilder("**Ground**: " + attSoldiersUnarmed + "/" + attSoldiers + "/" + attTanks + " -> " + defSoldiersUnarmed + "/" + defSoldiers + "/" + defTanks);
        if (defStr * 0.4 > attStr) {
            response.append("\n" + SuccessType.UTTER_FAILURE);
        } else {
            for (int success = 0; success <= 3; success++) {
                double odds = PnwUtil.getOdds(attStr, defStr, success);
                if (odds <= 0) continue;
                odds = Math.min(1, odds);
                String pctStr = MathMan.format(odds * 100) + "%";
                response.append("\n").append(SuccessType.values[success]).append("=").append(pctStr);
            }
        }


        int reqUnarmedIT = (int) Math.ceil(defStr * 2.5);
        int reqArmedIT = (int) Math.ceil(defStr * 2.5 / 1.75);

        int reqUnarmedUF = (int) Math.ceil(defStr * 0.4);
        int reqArmedUF = (int) Math.ceil(defStr * 0.4 / 1.75);

        response.append("\nNote:\n" + " - Tanks = 40x unarmed soldiers (22.86x armed) | Armed Soldiers = 1.75 Unarmed\n" + " - Guaranteed IT needs 2.5x enemy (").append(reqUnarmedIT).append(" unarmed, ").append(reqArmedIT).append(" armed)\n").append(" - Guaranteed UF needs 0.4x enemy (").append(reqUnarmedUF).append(" unarmed, ").append(reqArmedUF).append(" armed)");

        return response.toString();
    }

    @Command(aliases = {"airsim", "airstrikesim", "planesim"}, desc = "Simulate an airstrike with the given attacker and defender aircraft")
    public String airSim(int attAircraft, int defAircraft) {
        double attStr = attAircraft;
        double defStr = defAircraft;

        StringBuilder response = new StringBuilder("**Airstrike**: " + attAircraft + " -> " + defAircraft);
        for (int success = 0; success <= 3; success++) {
            double odds = PnwUtil.getOdds(attStr, defStr, success);
            if (odds <= 0) continue;
            odds = Math.min(1, odds);
            String pctStr = MathMan.format(odds * 100) + "%";
            response.append("\n").append(SuccessType.values[success]).append("=").append(pctStr);
        }

        response.append("\n\nNote: For a guaranteed IT, you need 2.5x enemy strength.");

        return response.toString();
    }

    @Command(aliases = {"shipSim", "navalSim"}, desc = "Simulate a naval battle with the given attacker and defender ships")
    public String navalSim(int attShips, int defShips) {
        double attStr = attShips;
        double defStr = defShips;

        StringBuilder response = new StringBuilder("**Naval**: " + attShips + " -> " + defShips);
        for (int success = 0; success <= 3; success++) {
            double odds = PnwUtil.getOdds(attStr, defStr, success);
            if (odds <= 0) continue;
            odds = Math.min(1, odds);
            String pctStr = MathMan.format(odds * 100) + "%";
            response.append("\n - ").append(SuccessType.values[success]).append("=").append(pctStr);
        }

        response.append("\n\nNote: For a guaranteed IT, you need 2.5x enemy strength");

        return response.toString();
    }

    public String nameSuccess(int type) {
        return switch (type) {
            case 0 -> "Failure";
            case 1 -> "Pyrrhic";
            case 2 -> "Moderate";
            case 3 -> "Immense";
            default -> "UNKNOWN";
        };
    }
}