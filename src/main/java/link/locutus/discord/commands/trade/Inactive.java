package link.locutus.discord.commands.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Inactive extends Command {
    public Inactive() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "inactive <alliance|coalition|*> [days=7] [page]";
    }

    @Override
    public String desc() {
        return "Get the top X inactive players. Use `-a` to include applicants";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return usage(event);
        }
        Integer days = 7;
        if (args.size() >= 2) {
            days = MathMan.parseInt(args.get(1));
            if (days == null) {
                return "Invalid number of days: `" + args.get(1) + "`";
            }
        }
        long minutes = TimeUnit.DAYS.toMinutes(days);

        Set<Integer> allianceIds = DiscordUtil.parseAlliances(DiscordUtil.getDefaultGuild(event), args.get(0));
        if (allianceIds == null) {
            if (args.get(0).equalsIgnoreCase("*")) {
                allianceIds = new HashSet<>();
            } else {
                return "Invalid aa or coaltion: `" + args.get(0) + "`";
            }
        }
        List<DBNation> nations = new ArrayList<>(Locutus.imp().getNationDB().getNations(allianceIds));
        nations.removeIf(nation -> nation.getActive_m() < minutes);

        boolean applicants = flags.contains('a');
        if (!applicants) nations.removeIf(nation -> nation.getPosition() <= 1);

        nations.sort((o1, o2) -> Integer.compare(o2.getActive_m(), o1.getActive_m()));

        int perPage = 5;
        int pages = (nations.size() + perPage - 1) / perPage;

        Integer page = 1;
        if (args.size() == 3) {
            page = MathMan.parseInt(args.get(2));
            if (page == null || page > pages || page <= 0) {
                return "Invalid page: `" + args.get(2) + "`";
            }
        }

        StringBuilder response = new StringBuilder();
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, nations.size());
        for (int i = start; i < end; i++) {
            response.append('\n').append(nations.get(i).toMarkdown());
        }
        String title = "Inactive Players in `" + args.get(0) + "`" + "(" + page + "/" + pages + ")";
        String prev = Settings.commandPrefix(true) + "inactive " + args.get(0) + " " + days + " " + (page - 1) + (applicants ? " -a" : "");
        String next = Settings.commandPrefix(true) + "inactive " + args.get(0) + " " + days + " " + (page + 1) + (applicants ? " -a" : "");

        List<String> actions = new ArrayList<>();
        if (page > 1) {
            actions.add("\u2b05\ufe0f");
            actions.add(prev);
        }
        if (page < pages) {
            actions.add("\u27a1\ufe0f");
            actions.add(next);
        }
        String[] actionsArr = actions.toArray(new String[0]);

        DiscordUtil.createEmbedCommand(event.getChannel(), title, response.toString(), actionsArr);

        return null;
    }
}
