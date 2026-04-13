package link.locutus.discord.commands.war;

import link.locutus.discord.util.RateLimitedSources;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.scheduler.KeyValue;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.stream.Collectors;

public class Damage extends Command {
    public Damage() {
        super("damage", "whales", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.war.find.damage.cmd);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "damage <alliance|coalition|*> [options...]";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return "Find a raid target, with optional alliance and sorting (default: active nations, sorted by top city infra).\n\t" +
                "To see a list of coalitions, use `" + Settings.commandPrefix(true) + "coalitions`.\n\t" +
                "Add `-a` To include applicants\n" +
                "Add `-i` to include inactives\n" +
                "Add `-w` to filter out nations with strong ground\n" +
                "Add `-s` to filter out nations with >2 ships\n" +
                "Add `-n:40` to filter out nations with more than 40% of your ships\n" +
                "Add `-m` to sort by mean infra instead of city max\n" +
                "Add `-c` to sort by city max instead of damage estimate\n" +
                "Add `-b` to include beige targets" +
                "Add `score:1234` to filter by war range";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        Double score = DiscordUtil.parseArgDouble(args, "score");
        if (args.size() != 1) return usage();

        boolean includeApps = flags.contains('a');
        boolean includeInactives = flags.contains('i');
        boolean filterWeak = flags.contains('w');
        boolean filterNoShips = flags.contains('s');
        boolean mean = flags.contains('m');
        boolean includeBeige = flags.contains('b');

        Set<DBNation> nations;
        if (args.get(0).equalsIgnoreCase("counters")) {
            nations = new HashSet<>();
            for (DBWar war : me.getActiveWars()) {
                if (war.getAttacker_id() != me.getNation_id() && war.getAttacker_aa() != 0) {
                    nations.addAll(Locutus.imp().getNationDB().getNationsByAlliance(Collections.singleton(war.getAttacker_aa())));
                }
            }
        } else {
            nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, false);
        }
        me = DiscordUtil.getNation(author);
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention();
        if (score == null) score = me.getScore();

        WarTargetFinder.DamageTargets damageTargets = WarTargetFinder.getDamageTargets(
                me,
                nations,
                includeApps,
                includeInactives,
                filterWeak,
                filterNoShips,
                includeBeige,
                score
        );

        if (flags.contains('d')) {
            channel = DiscordChannelIO.privateOutput(author, RateLimitedSources.COMMAND_RESULT);
        }

        List<Map.Entry<DBNation, Double>> maxInfraSorted = damageTargets.topTargets(15, flags.contains('m'), flags.contains('c'), false);
        if (maxInfraSorted.isEmpty()) {
            channel.sendMessage("No results (found", RateLimitedSources.COMMAND_RESULT);
            return null;
        }

        StringBuilder response = new StringBuilder("**Results for " + me.getNation() + ":**\n");
        for (int i = 0; i < Math.min(15, maxInfraSorted.size()); i++) {
            Map.Entry<DBNation, Double> entry = maxInfraSorted.get(i);
            DBNation nation = entry.getKey();
            double cost = damageTargets.damageEstByNation().getOrDefault(nation.getNation_id(), 0d);
            String moneyStr = "$" + MathMan.format(cost);
            response.append(moneyStr + " | " + nation.toMarkdown(true));
        }
        channel.send(response.toString().trim(), RateLimitedSources.COMMAND_RESULT);
        return null;
    }

    public double damageEstimate(DBNation me, int nationId, List<Double> cityInfra) {
        return WarTargetFinder.damageEstimate(me, nationId, cityInfra);
    }
}
