package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.LinkedHashMap;
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
        Set<Integer> aaIds = db.getAllianceIds();
        Set<Treaty> allTreaties = new LinkedHashSet<>();
        Map<Integer, Treaty> treaties = new LinkedHashMap<>();
        for (Integer alliance : alliances) {
            DBAlliance.getOrCreate(alliance).getTreaties(alliances.size() == 1 && aaIds.contains(alliance));
            treaties.putAll(Locutus.imp().getNationDB().getTreaties(alliance));
            allTreaties.addAll(treaties.values());
        }
        for (Map.Entry<Integer, Treaty> entry : treaties.entrySet()) {
            Treaty treaty = entry.getValue();
            if (allTreaties.contains(treaty)) continue;
            String from = PnwUtil.getMarkdownUrl(treaty.getFromId(), true);
            String to = PnwUtil.getMarkdownUrl(treaty.getToId(), true);
            TreatyType type = treaty.getType();

            response.append(from + " | " + type + " -> " + to).append("\n");
        }

        if (allTreaties.isEmpty()) return "No treaties.";

        String title = allTreaties.size() + " treaties";
        DiscordUtil.createEmbedCommand(event.getChannel(), title, response.toString());
        return null;
    }
}
