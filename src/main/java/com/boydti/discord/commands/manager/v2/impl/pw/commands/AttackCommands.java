package com.boydti.discord.commands.manager.v2.impl.pw.commands;

import com.boydti.discord.commands.manager.v2.binding.annotation.Command;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.apiv1.enums.SuccessType;
import net.dv8tion.jda.api.entities.User;

public class AttackCommands {
    @Command(aliases = "groundsim")
    public String groundSim(int attSoldiersUnarmed, int attSoldiers, int attTanks, int defSoldiersUnarmed, int defSoldiers, int defTanks) {
        if (attSoldiers != 0 && attSoldiersUnarmed != 0) return "You cannot attack with both armed and unarmed soldiers";
        if (defSoldiers != 0 && defSoldiersUnarmed != 0) return "You cannot defend with both armed and unarmed soldiers";

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
                response.append("\n" + SuccessType.values[success] + "=" + pctStr);
            }
        }


        int reqUnarmedIT = (int) Math.ceil(defStr * 2.5);
        int reqArmedIT = (int) Math.ceil(defStr * 2.5 / 1.75);

        int reqUnarmedUF = (int) Math.ceil(defStr * 0.4);
        int reqArmedUF = (int) Math.ceil(defStr * 0.4 / 1.75);

        response.append("\nNote:\n" +
                " - Tanks = 40x unarmed soldiers (22.86x armed) | Armed Soldiers = 1.75 Unarmed\n" +
                " - Guaranteed IT needs 2.5x enemy (" + reqUnarmedIT + " unarmed, " + reqArmedIT + " armed)\n" +
                " - Guaranteed UF needs 0.4x enemy (" + reqUnarmedUF + " unarmed, " + reqArmedUF + " armed)");

        return response.toString();
    }

    @Command(aliases = {"airsim", "airstrikesim", "planesim"})
    public String airSim(@Me GuildDB db, @Me User user, int attAircraft, int defAircraft) {
        double attStr = attAircraft;
        double defStr = defAircraft;

        StringBuilder response = new StringBuilder("**Airstrike**: " + attAircraft + " -> " + defAircraft);
        for (int success = 0; success <= 3; success++) {
            double odds = PnwUtil.getOdds(attStr, defStr, success);
            if (odds <= 0) continue;
            odds = Math.min(1, odds);
            String pctStr = MathMan.format(odds * 100) + "%";
            response.append("\n" + SuccessType.values[success] + "=" + pctStr);
        }

        response.append("\n\nNote: For a guaranteed IT, you need 2.5x enemy strength");

        return response.toString();
    }

    @Command(aliases = {"shipSim", "navalSim"})
    public String navalSim(@Me GuildDB db, @Me User user, int attShips, int defShips) {
        double attStr = attShips;
        double defStr = defShips;

        StringBuilder response = new StringBuilder("**Naval**: " + attShips + " -> " + defShips);
        for (int success = 0; success <= 3; success++) {
            double odds = PnwUtil.getOdds(attStr, defStr, success);
            if (odds <= 0) continue;
            odds = Math.min(1, odds);
            String pctStr = MathMan.format(odds * 100) + "%";
            response.append("\n - " + SuccessType.values[success] + "=" + pctStr);
        }

        response.append("\n\nNote: For a guaranteed IT, you need 2.5x enemy strength");

        return response.toString();
    }

    public String nameSuccess(int type) {
        switch (type) {
            case 0: return "Failure";
            case 1: return "Pyrrhic";
            case 2: return "Moderate";
            case 3: return "Immense";
            default: return "UNKNOWN";
        }
    }
}