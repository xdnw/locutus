package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CoalitionSheet extends Command {
    public CoalitionSheet() {
        super("CoalitionSheet", "CoalitionsSheet", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV, CommandCategory.FOREIGN_AFFAIRS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).isValidAlliance() && Roles.FOREIGN_AFFAIRS.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Map<Integer, List<String>> coalitionsInverse = new LinkedHashMap<>();
        Map<String, Set<Integer>> coalitions = db.getCoalitions();

        for (Map.Entry<String, Set<Integer>> entry : coalitions.entrySet()) {
            for (Integer aaId : entry.getValue()) {
                coalitionsInverse.computeIfAbsent(aaId, f -> new ArrayList<>()).add(entry.getKey());
            }
        }

        SpreadSheet sheet = SpreadSheet.create(db, SheetKeys.COALITION_SHEET);
        sheet.setHeader("Alliance", "Coalitions");
        for (Map.Entry<Integer, List<String>> entry : coalitionsInverse.entrySet()) {
            String aaUrl = MarkupUtil.sheetUrl(PnwUtil.getName(entry.getKey(), true), PnwUtil.getUrl(entry.getKey(), true));
            sheet.addRow(aaUrl, StringMan.join(entry.getValue(), ","));
        }

        sheet.clearAll();
        sheet.set(0, 0);
        sheet.attach(channel.create()).send();
        return null;
    }
}
