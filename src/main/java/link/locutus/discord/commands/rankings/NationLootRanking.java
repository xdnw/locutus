package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static link.locutus.discord.util.MathMan.format;

public class NationLootRanking extends Command {
    public NationLootRanking() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "NationLootRanking <alliances|coalitions|*> <days>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) {
            return usage(args.size(), 2, channel);
        }
        Collection<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));
        if (nations == null || nations.isEmpty()) {
            return "Invalid alliance or coalition: `" + args.get(0) + "`";
        }
        Map<Integer, DBNation> nationMap = nations.stream().collect(Collectors.toMap(DBNation::getNation_id, n -> n));

        long diff = TimeUtil.timeToSec(args.get(1)) * 1000L;
        long cutoffMs = System.currentTimeMillis() - diff;
        String diffStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff);

        Map<Integer, Double> totals = new HashMap<>();
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(cutoffMs);
        for (DBAttack attack : attacks) {
            if (attack.getVictor() != 0 && attack.getMoney_looted() != 0) {
                if (nationMap.containsKey(attack.getVictor())) {
                    totals.put(attack.getVictor(), totals.getOrDefault(attack.getVictor(), 0d) + attack.getMoney_looted());
                }
            }
        }

        List<Map.Entry<Integer, Double>> sorted = totals.entrySet().stream().sorted((o1, o2) -> -Double.compare(o1.getValue(), o2.getValue())).toList();

        String title = args.get(0) + " Looted from nations (" + diffStr + "):";
        StringBuilder response = new StringBuilder();

        for (int i = 0; i < Math.min(20, sorted.size()); i++) {
            Map.Entry<Integer, Double> entry = sorted.get(i);
            int nationId = entry.getKey();
            double value = entry.getValue();

            DBNation nation = nationMap.get(nationId);
            String name = nation == null ? Integer.toString(nationId) : nation.getNation();
            name = name.substring(0, Math.min(32, name.length()));

            response.append('\n').append(String.format("%4s", i + 1)).append(". ").append(String.format("%32s", name)).append(": $").append(format(value));
        }

        String emoji = "Refresh";
        response.append("\n\nPress `").append(emoji).append("` to refresh");
        DiscordUtil.createEmbedCommand(channel, title, response.toString(), emoji, DiscordUtil.trimContent(fullCommandRaw));

        return null;
    }
}
