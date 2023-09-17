package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class FASheet extends Command {
    public FASheet() {
        super("FASheet", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV, CommandCategory.FOREIGN_AFFAIRS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.FOREIGN_AFFAIRS.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {

        GuildDB db = Locutus.imp().getGuildDB(guild);
        SpreadSheet sheet = SpreadSheet.create(db, SheetKeys.FA_CONTACT_SHEET);
        List<List<Object>> existing = sheet.getAll();
        if (existing == null) existing = new ArrayList<>();
        Map<Integer, String> notes = new HashMap<>();
        Map<Integer, String> lastContacted = new HashMap<>();
        Map<Integer, String> iterations = new HashMap<>();

        for (int i = 1; i < existing.size(); i++) {
            List<Object> row = existing.get(i);
            if (row.size() < 2) continue;
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(null, "" + row.get(1));
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
            DBAlliance aa = DBAlliance.getOrCreate(aaId);

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

        sheet.attach(channel.create(), "fa").send();
        return null;
    }
}
