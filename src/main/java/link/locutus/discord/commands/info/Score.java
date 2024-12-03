package link.locutus.discord.commands.info;

import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
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
    public List<CommandRef> getSlashReference() {
        return List.of(CM.nation.score.cmd);
    }
    @Override
    public String help() {
        return super.help() + " cities=X soldiers=X tanks=X aircraft=X ships=X missiles=X nukes=X projects=X infra=X avg_infra=X mmr=XXXX";
    }

    @Override
    public String desc() {
        return "Calculate the score of various things. Each argument is option, and can go in any order e.g.\n" +
                "`" + Settings.commandPrefix(true) + "score cities=10 projects=2 avg_infra=1.5k mmr=0251`\n" +
                "You can also specify a nation to use as a base e.g.\n" +
                "`" + Settings.commandPrefix(true) + "score 'Mountania' avg_infra=2000`";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() == 0) return usage(args.size(), 1, channel);
        double infra = -1;
        double avg_infra = -1;

        String mmrStr = null;

        DBNation nation = new SimpleDBNation(new DBNationData());
        nation.setMissiles(0);
        nation.setNukes(0);

        nation.setSoldiers(0);
        nation.setTanks(0);
        nation.setAircraft(0);
        nation.setShips(0);

        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            String next = iterator.next();

            DBNation nationArg = DiscordUtil.parseNation(next);
            if (nationArg != null) {
                nation = nationArg.copy();
                iterator.remove();
            }
        }

        iterator = args.iterator();
        while (iterator.hasNext()) {
            String next = iterator.next();

            String[] split = next.split("=");
            if (split.length != 2) return usage("Argument must have a value in form e.g. `key=1234`. Instead received: `" + next + "`", channel);

            Double amt = MathMan.parseDouble(split[1]);
            if (amt == null) return "Unknown number `" + split[1] + "`";

            String arg = split[0].toLowerCase();
            switch (arg) {
                case "cities", "city" -> nation.setCities(amt.intValue());

                case "soldier", "soldiers" -> nation.setSoldiers(amt.intValue());

                case "tank", "tanks" -> nation.setTanks(amt.intValue());

                case "aircraft", "planes", "plane" -> nation.setAircraft(amt.intValue());

                case "ship", "ships", "boats" -> nation.setShips(amt.intValue());

                case "missile", "missiles" -> nation.setMissiles(amt.intValue());

                case "nuke", "nukes" -> nation.setNukes(amt.intValue());
                case "project", "projects" -> {
                    if (amt.intValue() >= Projects.values.length) return "Too many projects: " + amt.intValue();
                    nation.setProjectsRaw(0);
                    for (int i = 0; i < amt.intValue(); i++) {
                        nation.setProject(Projects.values[i]);
                    }
                }
                case "infra" -> infra = amt.intValue();
                case "avg_infra" -> avg_infra = amt;
                case "mmr" -> mmrStr = split[1];
                default -> {
                    return "Unknown value: `" + split[0] + "`.\n" + usage();
                }
            }
        }
        if (avg_infra >= 0) {
            infra = avg_infra * nation.getCities();
        }
        if (infra == -1) {
            infra = nation.getInfra();
        }

        if (mmrStr != null) {
            nation.setMMR((mmrStr.charAt(0) - '0'), (mmrStr.charAt(1) - '0'), (mmrStr.charAt(2) - '0'), (mmrStr.charAt(3) - '0'));
        }

        double score = nation.estimateScore(infra);

        if (score == 0) return usage("No score provided", channel);

        return "Score: " + MathMan.format(score) + "\n" +
                "WarRange: " + MathMan.format(score * 0.75) + "- " + MathMan.format(score * PW.WAR_RANGE_MAX_MODIFIER) + "\n" +
                "Can be Attacked By: " + MathMan.format(score / PW.WAR_RANGE_MAX_MODIFIER) + "- " + MathMan.format(score / 0.75) + "\n" +
                "Spy range: " + MathMan.format(score * 0.4) + "- " + MathMan.format(score * 1.5);
    }
}
