package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class InactiveAlliances extends Command {
    public InactiveAlliances() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "InactiveAlliances <alliances|coalition> [days=7]";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() == 0 || args.size() > 2) {
            return usage(args.size(), 1, 2, channel);
        }
        String group;
        Collection<DBNation> nations;
        if (args.get(0).equalsIgnoreCase("*")) {
            group = "*";
            nations = (Locutus.imp().getNationDB().getNations().values());
        } else {
            group = args.get(0);
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, group);
            if (alliances == null || alliances.isEmpty()) {
                return "Invalid aa/coalition: " + group;
            }
            nations = Locutus.imp().getNationDB().getNations(alliances);
        }
        nations.removeIf(n -> n.getPosition() <= 1);

        Integer days = 7;
        if (args.size() == 2) {
            days = MathMan.parseInt(args.get(1));
            if (days == null) {
                return "Invalid number of days: `" + args.get(1) + "`";
            }
        }
        long minutes = TimeUnit.DAYS.toMinutes(days);

        Map<Integer, Integer> allianceSize = new RankBuilder<>(nations).group(DBNation::getAlliance_id).sumValues(i -> 1).get();

        new RankBuilder<>(nations)
                .removeIf(nation -> nation.active_m() > minutes)
                .group(DBNation::getAlliance_id)
                .sumValues(nation -> 1)
                .sort((o1, o2) -> {
                    double size1 = allianceSize.get(o1.getKey()) / (double) (1 + o1.getValue());
                    double size2 = allianceSize.get(o2.getKey()) / (double) (1 + o2.getValue());
                    return Double.compare(size2, size1);
                })
                .name(e -> PnwUtil.getName(e.getKey(), true) + ": " + e.getValue() + "/" + allianceSize.get(e.getKey())).build(author, channel, fullCommandRaw, "Active in " + group + " (" + days + " days)");

        return null;
    }
}
