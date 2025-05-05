package link.locutus.discord.commands.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class Unregister extends Command {
    public Unregister() {
        super("Unregister", "unverify", CommandCategory.USER_SETTINGS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.unregister.cmd);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return super.help() + " <nation>";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(args.size(), 1, channel);
        String arg0 = args.get(0);
        String errMsg = null;
        DBNation nation = null;
        try {
             nation = DiscordUtil.parseNation(arg0, true, guild);
        } catch (IllegalArgumentException e) {
            errMsg = e.getMessage();
        }
        DBNation selfNation = DiscordUtil.getNation(author);
        if (selfNation == null) {
            return "You aren't registered.";
        }
        if (!selfNation.equals(nation) && !Roles.ADMIN.hasOnRoot(author)) {
            return "You can only unregister yourself.";
        }
        if (nation != null) {
            Locutus.imp().getDiscordDB().unregister(nation.getNation_id(), null);
            Locutus.imp().getDiscordDB().deleteApiKeyPairByNation(nation.getNation_id());
        } else {
            try {
                User user = DiscordBindings.user(author, guild, arg0);
                Locutus.imp().getDiscordDB().unregister(null, user.getIdLong());
            } catch (IllegalArgumentException e) {
                return "No user or nation found:\n" +
                        "- " + errMsg + "\n" +
                        "- " + e.getMessage();
            }
        }
        return "Unregistered user.";
    }
}
