package link.locutus.discord.commands.fun;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Commend extends Command {
    private final boolean isCommend;
    Set<Integer> history = new HashSet<>();

    public Commend(String id, boolean isCommend) {
        super(id, CommandCategory.FUN);
        this.isCommend = isCommend;
    }

    @Override
    public String help() {
        return super.help() + " <nation>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage();
        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation == null) return "Invalid nation: `" + args.get(0) + "`";

        if (history.contains(nation.getNation_id())) {
            return "I already did that today.";
        }
        history.add(nation.getNation_id());

        return nation.commend(isCommend);
    }
}
