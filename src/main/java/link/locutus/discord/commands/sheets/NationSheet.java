package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class NationSheet extends Command implements Noformat {
    public NationSheet() {
        super(CommandCategory.GOV, CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.sheets_ia.NationSheet.cmd);
    }

    @Override
    public String help() {
        return super.help() + " [nations] <column1> <column2> ...";
    }

    @Override
    public String desc() {
        return """
                Create a nation sheet, with the following column placeholders
                <https://github.com/xdnw/locutus/wiki/nation_placeholders>
                Add `-s` to force update spies
                Add `snapshot:20/10/2022` to use a snapshot""";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return (Roles.MILCOM.has(user, server) || Roles.ECON.has(user, server) || Roles.INTERNAL_AFFAIRS.has(user, server));
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String snapshotStr = DiscordUtil.parseArg(args, "snapshot");
        Long snapshot = snapshotStr == null ? null : PrimitiveBindings.timestamp(snapshotStr);
        if (args.size() < 2) return usage(args.size(), 2, channel);



        GuildDB db = Locutus.imp().getGuildDB(guild);
        SpreadSheet sheet = null;
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String arg = iter.next();
            if (arg.startsWith("sheet:")) {
                sheet = SpreadSheet.create(arg);
                iter.remove();;
            }
        }

        List<String> header = new ArrayList<>(args.subList(1, args.size()));
        header.remove(0);
        for (int i = 0; i < header.size(); i++) {
            String arg = header.get(i);
            arg = arg.replace("{", "").replace("}", "").replace("=", "");
            header.set(i, arg);
        }

        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        String formatted = placeholders.format2(guild, me, author, args.get(0), me, true);
        Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, formatted, false, false);
        if (nations.isEmpty()) return "No nations found for `" + args.get(0) + "`";

        if (sheet == null) {
            sheet = SpreadSheet.create(Locutus.imp().getGuildDB(guild), SheetKey.NATION_SHEET);
        }

        SimpleNationList nationList = new SimpleNationList(nations).setFilter(formatted);
        UtilityCommands.NationSheet(placeholders, channel, me, author, db, nationList, args.subList(1, args.size()), snapshot, flags.contains('s'), sheet);
        return null;
    }
}
