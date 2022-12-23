package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NoteSheet extends Command {
    public NoteSheet() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).isValidAlliance() && (Roles.MILCOM.has(user, server) || Roles.INTERNAL_AFFAIRS.has(user, server));
    }

    @Override
    public String help() {
        return super.help() + " <nations>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));
        if (nations == null || nations.isEmpty()) return usage(event);

        GuildDB db = Locutus.imp().getGuildDB(event);
        if (db == null) return "Not in guild";

        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.NOTE_SHEET);

        List<String> header = new ArrayList<>(Arrays.asList("nation", "cities", "active_m", "note1", "note2", "note3"));
        sheet.setHeader(header);

        Map<DBNation, List<Object>> rows = new HashMap<>();

        List<List<Object>> existing = sheet.getAll();
        if (existing == null) existing = new ArrayList<>();
        for (int i = 1; i < existing.size(); i++) {
            List<Object> row = existing.get(i);
            if (row.size() < 3) {
                continue;
            }
            String name = row.get(0).toString();
            DBNation nation = Locutus.imp().getNationDB().getNationByName(name);

            if (nation != null) {
                rows.put(nation, row);
            }
        }

        for (DBNation nation : nations) {
            List<Object> row = rows.computeIfAbsent(nation, f -> new ArrayList<>());
            for (int i = 0; i < (header.size() - row.size()); i++) {
                row.add("");
            }

            row.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            row.set(1, nation.getCities());
            row.set(2, nation.getActive_m());

            sheet.addRow(row);
        }

        sheet.clearAll();

        sheet.set(0, 0);

        return sheet.getURL(true, true);
    }
}
