package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.util.PW;
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
    public List<CommandRef> getSlashReference() {
        return List.of(CM.treaty.list.cmd);
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(args.size(), 1, channel);

        Set<Integer> alliances = PW.parseAlliances(Locutus.imp().getGuildDB(guild), args.get(0));

        if (alliances.isEmpty()) return "Invalid alliance: `" + args.get(0) + "`";

        StringBuilder response = new StringBuilder();

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Set<Integer> aaIds = db.getAllianceIds();
        Set<Treaty> allTreaties = new LinkedHashSet<>();
        Map<Integer, Treaty> treaties = new LinkedHashMap<>();
        for (Integer alliance : alliances) {
            DBAlliance.getOrCreate(alliance).getTreaties(alliances.size() == 1 && aaIds.contains(alliance));
            treaties.putAll(Locutus.imp().getNationDB().getTreaties(alliance));
        }
        for (Map.Entry<Integer, Treaty> entry : treaties.entrySet()) {
            Treaty treaty = entry.getValue();
            if (allTreaties.contains(treaty)) continue;
            allTreaties.add(entry.getValue());

            String from = PW.getMarkdownUrl(treaty.getFromId(), true);
            String to = PW.getMarkdownUrl(treaty.getToId(), true);
            TreatyType type = treaty.getType();

            response.append(from + " | " + type + " -> " + to).append("\n");
        }

        if (allTreaties.isEmpty()) return "No treaties.";

        String title = allTreaties.size() + " treaties";
        channel.create().embed(title, response.toString());
        return null;
    }
}
