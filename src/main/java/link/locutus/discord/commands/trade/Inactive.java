package link.locutus.discord.commands.trade;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.scheduler.KeyValue;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Inactive extends Command {
    public Inactive() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.who.cmd.list("YOU_ALLIANCE,#position>1,#vm_turns=0,#active_m>7200").list("true"));
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return usage(args.size(), 1, channel);
        }
        Integer days = 7;
        if (args.size() >= 2) {
            days = MathMan.parseInt(args.get(1));
            if (days == null) {
                return "Invalid number of days: `" + args.get(1) + "`";
            }
        }
        long minutes = TimeUnit.DAYS.toMinutes(days);

        Set<Integer> allianceIds = DiscordUtil.parseAllianceIds(guild, args.get(0));
        if (allianceIds == null) {
            if (args.get(0).equalsIgnoreCase("*")) {
                allianceIds = new IntOpenHashSet();
            } else {
                return "Invalid aa or coaltion: `" + args.get(0) + "`";
            }
        }
        List<DBNation> nations = new ArrayList<>(Locutus.imp().getNationDB().getNationsByAlliance(allianceIds));
        nations.removeIf(nation -> nation.active_m() < minutes);

        boolean applicants = flags.contains('a');
        if (!applicants) nations.removeIf(nation -> nation.getPosition() <= 1);

        nations.sort((o1, o2) -> Integer.compare(o2.active_m(), o1.active_m()));

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

        List<Map.Entry<String, String>> labelCommandPairs = new ArrayList<>();
        if (page > 1) {
            labelCommandPairs.add(KeyValue.of("\u2b05\ufe0f", prev));
        }
        if (page < pages) {
            labelCommandPairs.add(KeyValue.of("\u27a1\ufe0f", next));
        }

        IMessageBuilder msg = channel.create().embed(title, response.toString());
        for (Map.Entry<String, String> entry : labelCommandPairs) {
            msg = msg.commandButton(entry.getValue(), entry.getKey());
        }
        msg.send();

        return null;
    }
}
