package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.PendingTreaty;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.apiv1.enums.TreatyType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Treaties extends Command {
    public Treaties() {
        super(CommandCategory.FOREIGN_AFFAIRS, CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return super.help() + " [alliances]";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);

        Set<Integer> alliances = PnwUtil.parseAlliances(Locutus.imp().getGuildDB(guild), args.get(0));

        if (alliances.isEmpty()) return "Invalid alliance: `" + args.get(0) + "`";

        StringBuilder response = new StringBuilder();

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId != null && alliances.size() == 1 && alliances.iterator().next().equals(aaId)) {
            Auth auth = db.getAuth(AlliancePermission.MANAGE_TREATIES);
            if (auth != null) {
                List<PendingTreaty> treaties = auth.getTreaties();
                if (!flags.contains('f')) treaties.removeIf(f -> f.status == PendingTreaty.TreatyStatus.EXPIRED || f.status == PendingTreaty.TreatyStatus.WE_CANCELED || f.status == PendingTreaty.TreatyStatus.THEY_CANCELED);
                for (PendingTreaty treaty : treaties) {
                    long turnsLeft = treaty.getTurnEnds() - TimeUtil.getTurn();
                    response.append("#" + treaty.getId() + ": " + PnwUtil.getName(treaty.getFromId(), true) + " | " + treaty.getType() + " -> " + PnwUtil.getName(treaty.getToId(), true) + " (" + turnsLeft + " turns |" + treaty.status + ")").append("\n");
                }
                return response.toString();
            }
        }

        Set<Treaty> allTreaties = new LinkedHashSet<>();
        for (Integer alliance : alliances) {
            Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(alliance);

            for (Map.Entry<Integer, Treaty> entry : treaties.entrySet()) {
                Treaty treaty = entry.getValue();
                if (allTreaties.contains(treaty)) continue;
                String from = PnwUtil.getMarkdownUrl(treaty.getFromId(), true);
                String to = PnwUtil.getMarkdownUrl(treaty.getToId(), true);
                TreatyType type = treaty.getType();

                response.append(from + " | " + type + " -> " + to).append("\n");
            }

            allTreaties.addAll(treaties.values());
        }

        if (allTreaties.isEmpty()) return "No treaties";

        String title = allTreaties.size() + " treaties";
        DiscordUtil.createEmbedCommand(event.getChannel(), title, response.toString());
        return null;
    }
}
