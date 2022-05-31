package com.boydti.discord.commands.info;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Score extends Command {
    public Score() {
        super("score", "warscore", "warrange", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }
    @Override
    public String help() {
        return super.help() + " cities=X soldiers=X tanks=X aircraft=X ships=X missiles=X nukes=X projects=X infra=X avg_infra=X mmr=XXXX";
    }

    @Override
    public String desc() {
        return "Calculate the score of various things. Each argument is option, and can go in any order e.g.\n" +
                "`!score cities=10 projects=2 avg_infra=1.5k mmr=0251`\n" +
                "You can also specify a nation to use as a base e.g.\n" +
                "`!score 'Mountania' avg_infra=2000`";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() == 0) return usage(event);
        int cities = 0;
        double avg_infra = 0;
        String mmrStr = null;

        DBNation nation = new DBNation();
        nation.setMissiles(0);
        nation.setNukes(0);
        nation.setInfra(0);
        nation.setSoldiers(0);
        nation.setTanks(0);
        nation.setAircraft(0);
        nation.setShips(0);

        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            String next = iterator.next();

            DBNation nationArg = DiscordUtil.parseNation(next);
            if (nationArg != null) {
                nation = new DBNation(nationArg);
                iterator.remove();
            }
        }

        iterator = args.iterator();
        while (iterator.hasNext()) {
            String next = iterator.next();

            String[] split = next.split("=");
            if (split.length != 2) return usage(event);

            Double amt = MathMan.parseDouble(split[1]);
            if (amt == null) return "Unknown number `" + split[1] +"`";

            String arg = split[0].toLowerCase();
            switch (arg) {
                case "cities":
                case "city":
                    nation.setCities(amt.intValue());
//                    score += (100 * (amt - 1));
//                    cities = amt.intValue();
                    break;
                case "soldier":
                case "soldiers":
                    nation.setSoldiers(amt.intValue());
//                    score += MilitaryUnit.SOLDIER.getScore(amt.intValue());
                    break;
                case "tank":
                case "tanks":
                    nation.setTanks(amt.intValue());
//                    score += MilitaryUnit.TANK.getScore(amt.intValue());
                    break;
                case "aircraft":
                case "planes":
                case "plane":
                    nation.setAircraft(amt.intValue());
//                    score += MilitaryUnit.AIRCRAFT.getScore(amt.intValue());
                    break;
                case "ship":
                case "ships":
                case "boats":
                    nation.setShips(amt.intValue());
//                    score += MilitaryUnit.SHIP.getScore(amt.intValue());
                    break;
                case "missile":
                case "missiles":
                    nation.setMissiles(amt.intValue());
//                    score += MilitaryUnit.MISSILE.getScore(amt.intValue());
                    break;
                case "nuke":
                case "nukes":
                    nation.setNukes(amt.intValue());
//                    score += MilitaryUnit.NUKE.getScore(amt.intValue());
                    break;
                case "project":
                case "projects":
                    if (amt.intValue() >= Projects.values.length) return "Too many projects: " + amt.intValue();
                    nation.setProjectsRaw(0);
                    for (int i = 0; i < amt.intValue(); i++) {
                        nation.setProject(Projects.values[i]);
                    }
//                    score += 20 * amt;
                    break;
                case "infra":
                    nation.setInfra(amt.intValue());
//                    score += amt / 40d;
                    break;
                case "avg_infra":
                    avg_infra = amt;
                    break;
                case "mmr":
                    mmrStr = split[1];
                    break;
                default:
                    return "Unknown value: `" + split[0] + "`.\n" + usage();
            }
        }
        if (avg_infra != 0) {
            nation.setAvg_infra((int) avg_infra);
            nation.setInfra((int) (avg_infra * nation.getCities()));
        }

        if (mmrStr != null) {
            nation.setMMR((mmrStr.charAt(0) - '0'), (mmrStr.charAt(1) - '0'), (mmrStr.charAt(2) - '0'), (mmrStr.charAt(3) - '0'));
        }

        double score = nation.estimateScore(true);

        if (score == 0) return usage(event);

        return "Score: " + MathMan.format(score) + "\n" +
                "WarRange: " + MathMan.format(score * 0.75) + " - " + MathMan.format(score * 1.75) + "\n" +
                "Can be Attacked By: " + MathMan.format(score / 1.75) + " - " + MathMan.format(score / 0.75) + "\n" +
                "Spy range: " + MathMan.format(score * 0.4) + " - " + MathMan.format(score * 1.5);
    }
}
