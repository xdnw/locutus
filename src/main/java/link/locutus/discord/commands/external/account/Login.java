package link.locutus.discord.commands.external.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class Login extends Command {

    public Login() {
        super(CommandCategory.USER_SETTINGS);
    }


    @Override
    public String help() {
        return super.help() + " <username> <password>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        try {
            if (me == null) return "Please use " + CM.register.cmd.toSlashMention() + "";
            if (guild != null) {
                return "This command must be used via private message with Locutus. DO NOT USE THIS COMMAND HERE";
            }
            GuildDB db = Locutus.imp().getGuildDBByAA(me.getAlliance_id());
            if (db == null) return "Your alliance " + me.getAlliance_id() + " is not registered with Locutus";
            db.getOrThrow(GuildDB.Key.ALLIANCE_ID);
            if (args.size() < 2) return usage(event);
            Auth existingAuth = db.getAuth();

//            if (!Roles.MEMBER.has(author, Locutus.imp().getServer())) {
//                OffshoreInstance offshore = db.getOffshore();
//                if (offshore == null) return "You have no offshore";
//                if (!Roles.MEMBER.has(author, Locutus.imp().getServer()) && existingAuth != null && existingAuth.isValid() && existingAuth.getNation().getPosition() >= Rank.OFFICER.id && existingAuth.getNationId() != me.getNation_id())
//                    return "An officer is already connected";
//            }

            String[] split = DiscordUtil.trimContent(event.getMessage().getContentRaw()).split(" ", 3);
            String username = split[1];
            String password = split[2];

            Auth auth = new Auth(me.getNation_id(), username, password);
            ApiKeyPool.ApiKey key = auth.fetchApiKey();

            Locutus.imp().getDiscordDB().addUserPass2(me.getNation_id(), username, password);

            if (existingAuth != null) existingAuth.setValid(false);
            Auth myAuth = me.getAuth(null);
            if (myAuth != null) myAuth.setValid(false);

            return "Login successful.";
        } finally {
            if (guild != null) {
                RateLimitUtil.queue(event.getMessage().delete());
            }
        }
    }
}
