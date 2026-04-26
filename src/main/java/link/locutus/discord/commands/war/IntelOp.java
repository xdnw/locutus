package link.locutus.discord.commands.war;

import link.locutus.discord.util.RateLimitedSources;
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
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
        return """
                Find nations to conduct intel ops on (sorted by infra days * inactive)
                Use `nation:Borg` to specify nation
                Use `score:1234` to specify score""";
    }

    private Map<Integer, Long> alreadySpied = new ConcurrentHashMap<>();

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String nationStr = DiscordUtil.parseArg(args, "nation");
        String scoreStr = DiscordUtil.parseArg(args, "score");
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention();
        if (args.size() > 1) return usage(args.size(), 0, 1, channel);

        DBNation finalNation = nationStr == null ? me : PWBindings.parseNation(runtimeServices(), null, guild, nationStr, null);
        double score = scoreStr == null ? finalNation.getScore() : PrimitiveBindings.Double(scoreStr);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        int topX = 25;
        if (args.size() == 1) {
            topX = Integer.parseInt(args.get(0));
        } else {
            Integer dnr = db.getOrNull(GuildKey.DO_NOT_RAID_TOP_X);
            if (dnr != null) topX = dnr;
        }

        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30);
        Set<Integer> recentlySpiedNationIds = alreadySpied.entrySet().stream()
                .filter(entry -> entry.getValue() > cutoff)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        SpyOpsService.IntelResult result = SpyOpsService.findIntelTargets(
                finalNation,
                db,
                topX,
                flags.contains('d'),
                score,
                recentlySpiedNationIds,
                1);
        if (result.hasError()) {
            return result.message();
        }

        for (SpyOpsService.IntelRecommendation recommendation : result.recommendations()) {
            DBNation nation = recommendation.target();
            alreadySpied.put(nation.getNation_id(), System.currentTimeMillis());

            String title = "Gather Intelligence for: " + finalNation.getNation();
            String response = nation.toEmbedString();
            response += "\n1 spy on extremely covert: ";
            response += "\n*Please post the result of your spy report here*";
            response += "\nMore info: https://docs.google.com/document/d/1gEeSOjjSDNBpKhrU9dhO_DN-YM3nYcklYzSYzSqq8k0";
            channel.create().embed(title, response).send(RateLimitedSources.COMMAND_RESULT);
            return null;
        }
        return "No results found";
    }
}
