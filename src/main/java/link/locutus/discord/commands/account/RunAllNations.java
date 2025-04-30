package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.*;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class RunAllNations extends Command implements Noformat {

    public RunAllNations() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.command.format_for_nations.cmd);
    }

    @Override
    public String help() {
        return super.help() + " <nations> <command>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) return usage(args.size(), 2, channel);
        Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, false);

        if (nations.size() == 0) return "No nations found for `" + args.get(0) + "`";
        if (nations.size() > 200 && !Roles.ADMIN.hasOnRoot(author)) return "Too many nations (max 200).";

        StringBuilder result = new StringBuilder();
        String cmd = args.get(1);
        Function<DBNation, String> formatter = Locutus.imp().getCommandManager().getV2().getNationPlaceholders().getFormatFunction(guild, me, author, cmd, nations);
        for (DBNation nation : nations) {
            String formatted = formatter.apply(nation);
            Locutus.imp().getCommandManager().run(guild, channel, author, formatted, false, true);
        }
        return "Done!";
    }
}
