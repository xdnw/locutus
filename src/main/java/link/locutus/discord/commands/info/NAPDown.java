package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class NAPDown extends Command {
    public NAPDown() {
        super("NAPDown", "NAP");
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.treaty.gw_nap.cmd);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return false;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        return UtilityCommands.nap(false);
    }
}
