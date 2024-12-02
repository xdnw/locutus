package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

public class IntelOp extends Command {
    public IntelOp() {
        super("IntelOp", "Intel", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.spy.find.intel.cmd);
    }

    @Override
    public String help() {
        return super.help() + " [topX]";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return "Find nations to conduct intel ops on (sorted by infra days * inactive)\n" +
                "Use `nation:Borg` to specify nation\n" +
                "Use `score:1234` to specify score";
    }

    private Map<Integer, Long> alreadySpied = new ConcurrentHashMap<>();

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String nationStr = DiscordUtil.parseArg(args, "nation");
        String scoreStr = DiscordUtil.parseArg(args, "score");
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention() + "";
        if (args.size() > 1) return usage(args.size(), 0, 1, channel);

        DBNation finalNation = nationStr == null ? me : PWBindings.nation(null, nationStr);
        double score = scoreStr == null ? finalNation.getScore() : PrimitiveBindings.Double(scoreStr);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        int topX = 25;
        if (args.size() == 1) {
            topX = Integer.parseInt(args.get(0));
        } else {
            Integer dnr = db.getOrNull(GuildKey.DO_NOT_RAID_TOP_X);
            if (dnr != null) topX = dnr;
        }

        List<DBNation> enemies = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());

        Set<Integer> allies = db.getAllies(true);

        Function<DBNation, Boolean> raidList = db.getCanRaid(topX, true);
        Set<Integer> enemyCoalitions = db.getCoalition("enemies");
        Set<Integer> targetCoalitions = db.getCoalition("targets");

        if (!flags.contains('d')) {
            enemies.removeIf(f -> !raidList.apply(f));
        }

        enemies.removeIf(f -> allies.contains(f.getAlliance_id()));
        enemies.removeIf(f -> f.active_m() < 4320);
        enemies.removeIf(f -> f.getVm_turns() > 0);
        enemies.removeIf(f -> f.isBeige());
        if (finalNation.getCities() > 3) enemies.removeIf(f -> f.getCities() < 4 || f.getScore() < 500);
        enemies.removeIf(f -> f.getDef() == 3);
        enemies.removeIf(nation ->
                nation.active_m() < 12000 &&
                        nation.getGroundStrength(true, false) > finalNation.getGroundStrength(true, false) &&
                        nation.getAircraft() > finalNation.getAircraft() &&
                        nation.getShips() > finalNation.getShips() + 2);
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30);
        enemies.removeIf(f -> alreadySpied.getOrDefault(f.getNation_id(), 0L) > cutoff);

        if (false) {
            Set<DBNation> myAlliance = Locutus.imp().getNationDB().getNationsByAlliance(Collections.singleton(finalNation.getAlliance_id()));
            myAlliance.removeIf(f -> f.active_m() > 2440 || f.getVm_turns() != 0);
            BiFunction<Double, Double, Integer> range = PW.getIsNationsInScoreRange(myAlliance);
            enemies.removeIf(f -> range.apply(f.getScore() / PW.WAR_RANGE_MAX_MODIFIER, f.getScore() / 0.75) <= 0);
        } else {
            List<DBNation> tmp = new ArrayList<>(enemies);
            tmp.removeIf(f -> f.getScore() < score * 0.75 || f.getScore() > score * PW.WAR_RANGE_MAX_MODIFIER);
            if (tmp.isEmpty()) {
                enemies.removeIf(f -> !f.isInSpyRange(finalNation));
            } else {
                enemies = tmp;
            }

        }

        List<Map.Entry<DBNation, Double>> noData = new ArrayList<>();
        List<Map.Entry<DBNation, Double>> outDated = new ArrayList<>();

        for (DBNation enemy : enemies) {
            Map.Entry<Double, Boolean> opValue = enemy.getIntelOpValue();
            if (opValue != null) {
                List<Map.Entry<DBNation, Double>> list = opValue.getValue() ? outDated : noData;
                list.add(new AbstractMap.SimpleEntry<>(enemy, opValue.getKey()));
            }
        }

        Collections.sort(noData, (o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));
        Collections.sort(outDated, (o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));
        noData.addAll(outDated);
        for (Map.Entry<DBNation, Double> entry : noData) {
            DBNation nation = entry.getKey();
            alreadySpied.put(nation.getNation_id(), System.currentTimeMillis());

            String title = "Gather Intelligence for: " + finalNation.getNation();
            String response = nation.toEmbedString();
            response += "\n1 spy on extremely covert: ";
            response += "\n*Please post the result of your spy report here*";
            response += "\nMore info: https://docs.google.com/document/d/1gEeSOjjSDNBpKhrU9dhO_DN-YM3nYcklYzSYzSqq8k0";
            channel.create().embed(title, response).send();
            return null;
        }
        return "No results found";
    }
}
