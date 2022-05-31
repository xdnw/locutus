package com.boydti.discord.commands.sheets;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static com.boydti.discord.db.GuildDB.Key.COALITION_SHEET;
import static com.boydti.discord.db.GuildDB.Key.FA_CONTACT_SHEET;

public class FASheet extends Command {
    public FASheet() {
        super("FASheet", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV, CommandCategory.FOREIGN_AFFAIRS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.FOREIGN_AFFAIRS.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {

        GuildDB db = Locutus.imp().getGuildDB(guild);
        SpreadSheet sheet = SpreadSheet.create(db, FA_CONTACT_SHEET);
        List<List<Object>> existing = sheet.get("A:Z");
        if (existing == null) existing = new ArrayList<>();
        Map<Integer, String> notes = new HashMap<>();
        Map<Integer, String> lastContacted = new HashMap<>();
        Map<Integer, String> iterations = new HashMap<>();

        for (int i = 1; i < existing.size(); i++) {
            List<Object> row = existing.get(i);
            if (row.size() < 2) continue;
            Set<Integer> alliances = DiscordUtil.parseAlliances(null, "" + row.get(1));
            if (alliances == null || alliances.size() != 1)continue;

            Integer aaId = alliances.iterator().next();
            String note = "" + row.get(0);
            String last = "" + (row.size() > 4 ?row.get(4) : "");
            String iteration = "" + (row.size() > 5 ?row.get(5) : "");

            notes.put(aaId, note);
            lastContacted.put(aaId, last);
            iterations.put(aaId, iteration);

        }

        sheet.setHeader(Arrays.asList(
                "note",
                "alliance",
                "rank",
                "score",
                "last contact",
                "iterations",
                "next contact",
                "treaties"
        ));
        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (Map.Entry<Integer, String> entry : notes.entrySet()) {
            Integer aaId = entry.getKey();
            Alliance aa = new Alliance(aaId);

            String note = notes.getOrDefault(aaId, "");
            String last = lastContacted.getOrDefault(aaId, "");
            String iteration = iterations.getOrDefault(aaId, "");

            if (last.isEmpty() || last.equals("null")) last = formatter.format(new Date());
            if (iteration.isEmpty() || iteration.equals("null")) iteration = "1";

            String treatyStr = StringMan.join(aa.getTreatiedAllies(), ",");

            int row = sheet.getValues().size() + 1;
            String nextStr = "=E" + row + "+POWER(2,F" + row + ")/2";
            sheet.addRow(note, MarkupUtil.sheetUrl(aa.getName(), aa.getUrl()), aa.getRank(), aa.getScore(), last, iteration, nextStr, treatyStr);
        }

        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }
}
