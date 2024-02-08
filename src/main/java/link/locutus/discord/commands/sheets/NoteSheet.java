package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(args.size(), 1, channel);
        Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, false);
        if (nations == null || nations.isEmpty()) return usage("Invalid nations: `" + args.get(0) + "`", channel);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db == null) return "Not in guild";

        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.NOTE_SHEET);

        List<String> header = new ArrayList<>(Arrays.asList("nation", "cities", "active_m", "note1", "note2", "note3"));
        sheet.setHeader(header);

        Map<DBNation, List<Object>> rows = new HashMap<>();

        List<List<Object>> existing = sheet.fetchAll(null);
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
            row.set(2, nation.active_m());

            sheet.addRow(row);
        }

        sheet.updateClearCurrentTab();

        sheet.updateWrite();

        sheet.attach(channel.create(), "notes").send();
        return null;
    }
}
