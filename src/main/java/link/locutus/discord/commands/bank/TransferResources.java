package link.locutus.discord.commands.bank;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;

public class TransferResources extends Command {
    private final BankWith cmd;

    public TransferResources(BankWith cmd) {
        super("tr", "transferresources", CommandCategory.ECON);
        this.cmd = cmd;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return cmd.checkPermission(server, user);
    }

    @Override
    public String desc() {
        return "Transfer resources to yourself";
    }

    @Override
    public String help() {
        return "!tr <resource> <amount>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.isEmpty()) return usage();
        DBNation me = DiscordUtil.getNation(event);
        if (me == null) return "Please use `!verify`";
        if (me.isGray()) {
            me.getPnwNation();
            if (me.isGray()) {
                return "Please set your color off gray: <https://politicsandwar.com/nation/edit/>";
            }
        }
        String mention = event.getAuthor().getAsMention();
        args = new ArrayList<>(args);
        args.add(0, mention);
        args.add("#deposit");
        return cmd.onCommand(event, args);
    }
}
