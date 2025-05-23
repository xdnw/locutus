package link.locutus.discord.commands.external.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class Logout extends Command {

    public Logout() {
        super(CommandCategory.USER_SETTINGS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        DBNation me = DiscordUtil.getNation(user);
        return (Locutus.imp().getDiscordDB().getUserPass2(user.getIdLong()) != null || (me != null && Locutus.imp().getDiscordDB().getUserPass2(me.getNation_id()) != null));
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.credentials.logout.cmd);
    }
    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (checkPermission(guild, author)) {
            Locutus.imp().getDiscordDB().logout(author.getIdLong());
            if (me != null) {
                Locutus.imp().getDiscordDB().logout(me.getNation_id());
                Auth cached = me.auth;
                if (cached != null) {
                    cached.setValid(false);
                }
                me.auth = null;
            }
            return "Logged out.";
        }
        return "You are not logged in.";
    }
}