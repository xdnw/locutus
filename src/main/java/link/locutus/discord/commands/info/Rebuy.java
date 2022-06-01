package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Rebuy extends Command {
    public Rebuy() {
        super("Rebuy", "Daychange", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public String help() {
        return super.help() + " <nation>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation == null) return "Unknown nation `" + args.get(0) + "`";

        Map<Integer, Long> dcProb = nation.findDayChange();
        if (dcProb.isEmpty() || dcProb.size() == 12) return "Unknown day change. Try `!unithistory`";

        if (dcProb.size() == 1) {
            Map.Entry<Integer, Long> entry = dcProb.entrySet().iterator().next();
            Integer offset = (entry.getKey() * 2 + 2) % 24;
            if (offset > 12) offset -= 24;
            return "Day change at UTC" + (offset >= 0 ? "+" : "") + offset + " (turn " + entry.getKey() + ")";
        }

        String title = "Possible DC times:";

        StringBuilder body = new StringBuilder("*date calculated | daychange time*\n\n");
        for (Map.Entry<Integer, Long> entry : dcProb.entrySet()) {
            Integer offset = (entry.getKey() * 2 + 2) % 24;
            if (offset > 12) offset -= 24;
            String dcStr = "UTC" + (offset >= 0 ? "+" : "") + offset + " (turn " + entry.getKey() + ")";
            Long turn = entry.getValue();
            long timestamp = TimeUtil.getTimeFromTurn(turn);
            String dateStr = TimeUtil.format(TimeUtil.MMDDYYYY_HH_MM_A, new Date(timestamp));
            body.append(dateStr + " | " + dcStr + "\n");
        }

        DiscordUtil.createEmbedCommand(event.getChannel(), title, body.toString());
        return null;
    }
}
