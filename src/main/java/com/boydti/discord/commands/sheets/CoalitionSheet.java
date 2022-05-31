package com.boydti.discord.commands.sheets;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.boydti.discord.db.GuildDB.Key.COALITION_SHEET;

public class CoalitionSheet extends Command {
    public CoalitionSheet() {
        super("CoalitionSheet", "CoalitionsSheet", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV, CommandCategory.FOREIGN_AFFAIRS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).isValidAlliance() && Roles.FOREIGN_AFFAIRS.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Map<Integer, List<String>> coalitionsInverse = new LinkedHashMap<>();
        Map<String, Set<Integer>> coalitions = db.getCoalitions();

        for (Map.Entry<String, Set<Integer>> entry : coalitions.entrySet()) {
            for (Integer aaId : entry.getValue()) {
                coalitionsInverse.computeIfAbsent(aaId, f -> new ArrayList<>()).add(entry.getKey());
            }
        }

        SpreadSheet sheet = SpreadSheet.create(db, COALITION_SHEET);
        sheet.setHeader("Alliance", "Coalitions");
        for (Map.Entry<Integer, List<String>> entry : coalitionsInverse.entrySet()) {
            String aaUrl = MarkupUtil.sheetUrl(PnwUtil.getName(entry.getKey(), true), PnwUtil.getUrl(entry.getKey(), true));
            sheet.addRow(aaUrl, StringMan.join(entry.getValue(), ","));
        }

        sheet.clearAll();
        sheet.set(0, 0);
        return "<" + sheet.getURL() + ">";
    }
}
