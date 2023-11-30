package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.AlliancePlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AllianceSheet extends Command implements Noformat {
    public AllianceSheet() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return super.help() + " [nations] <column1> <column2> ...";
    }

    @Override
    public String desc() {
        return "Create a nation sheet, with the following column placeholders:\n" +
                "<https://github.com/xdnw/locutus/wiki/alliance_placeholders>";

    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && (Roles.MILCOM.has(user, server) || Roles.ECON.has(user, server) || Roles.INTERNAL_AFFAIRS.has(user, server));
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(args.size(), 2, channel);

        List<String> header = new ArrayList<>(args);
        header.remove(0);
        for (int i = 0; i < header.size(); i++) {
            String arg = header.get(i);
            arg = arg.replace("{", "").replace("}", "").replace("=", "");
            header.set(i, arg);
        }

        Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, false);
        if (nations.isEmpty()) return "No nations found for `" + args.get(0) + "`";

        AlliancePlaceholders aaPlaceholders = Locutus.imp().getCommandManager().getV2().getAlliancePlaceholders();
        NationPlaceholders nationPlaceholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        SpreadSheet sheet = SpreadSheet.create(Locutus.imp().getGuildDB(guild), SheetKey.ALLIANCES_SHEET);
        GuildDB db = Locutus.imp().getGuildDB(guild);
        return UtilityCommands.AllianceSheet(nationPlaceholders, aaPlaceholders, guild, channel, me, author, db, nations, args, sheet);
    }
}
