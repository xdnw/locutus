package link.locutus.discord.commands.rankings;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class MyLoot extends Command {
    private final WarCostAB parent;

    public MyLoot(WarCostAB parent) {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS);
        this.parent = parent;
    }

    @Override
    public String help() {
        return super.help() + " <days>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(args.size(), 1, channel);
        if (!MathMan.isInteger(args.get(0))) return usage(args.get(0) + " is not a valid number of days", channel);
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention() + "";
        return parent.onCommand(guild, channel, author, me, fullCommandRaw, new ArrayList<>(Arrays.asList(me.getNationUrl(), "*", args.get(0))), flags);
    }
}
