package com.boydti.discord.commands.info;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.PendingTreaty;
import com.boydti.discord.db.entities.Treaty;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.offshore.Auth;
import com.boydti.discord.apiv1.enums.TreatyType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.boydti.discord.db.entities.PendingTreaty.TreatyStatus.EXPIRED;
import static com.boydti.discord.db.entities.PendingTreaty.TreatyStatus.THEY_CANCELED;
import static com.boydti.discord.db.entities.PendingTreaty.TreatyStatus.WE_CANCELED;

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
            Auth auth = db.getAuth();
            if (auth != null) {
                List<PendingTreaty> treaties = auth.getTreaties();
                if (!flags.contains('f')) treaties.removeIf(f -> f.status == EXPIRED || f.status == WE_CANCELED || f.status == THEY_CANCELED);
                for (PendingTreaty treaty : treaties) {
                    response.append("#" + treaty.treatyId + ": " + PnwUtil.getName(treaty.from, true) + " | " + treaty.type + " -> " + PnwUtil.getName(treaty.to, true) + " (" + treaty.remaining + "|" + treaty.status + ")").append("\n");
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
                String from = PnwUtil.getMarkdownUrl(treaty.from, true);
                String to = PnwUtil.getMarkdownUrl(treaty.to, true);
                TreatyType type = treaty.type;

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
