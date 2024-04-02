package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ValidateBlitzSheet extends Command {
    public ValidateBlitzSheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return super.help() + " <sheet> [max-wars=3] [nations-filer=*]";
    }

    @Override
    public String desc() {
        return "Check that all nations are in range of their blitz targets and that they have no more than the provided number of offensive wars\n" +
                "Use `-l` to use leader name instead of nation name";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        GuildDB db = Locutus.imp().getGuildDB(server);
        return (db.isValidAlliance() || db.isWhitelisted()) && Roles.MILCOM.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage(args.size(), 1, channel);

        Integer maxWars = 3;
        if (args.size() >= 2) maxWars = Integer.parseInt(args.get(1));

        boolean useLeader = flags.contains('l');

        Function<DBNation, Boolean> isValidTarget = f -> true;
        if (args.size() >= 3) {
            Set<DBNation> filter = DiscordUtil.parseNations(guild, author, me, args.get(2), false, false);
            isValidTarget = n -> filter.contains(n);
        }

        SpreadSheet sheet = SpreadSheet.create(args.get(0));
        StringBuilder response = new StringBuilder();
        Integer finalMaxWars = maxWars;
        BlitzGenerator.getTargets(sheet, useLeader, 0, f -> finalMaxWars, 0.75, PW.WAR_RANGE_MAX_MODIFIER, true, true, false, isValidTarget, new BiConsumer<Map.Entry<DBNation, DBNation>, String>() {
            @Override
            public void accept(Map.Entry<DBNation, DBNation> dbNationDBNationEntry, String msg) {
                response.append(msg + "\n");
            }
        }, a -> {});

        if (response.length() <= 1) return "All checks passed";

        return response.toString();
    }
}
