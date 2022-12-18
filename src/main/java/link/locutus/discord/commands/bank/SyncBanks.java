package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class SyncBanks extends Command {
    public SyncBanks() {
        super(CommandCategory.ECON, CommandCategory.LOCUTUS_ADMIN);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "syncbanks [epoch]";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() > 1) return usage();
        Long latest = args.size() == 1 ? Long.parseLong(args.get(0)) : null;

        GuildDB db = Locutus.imp().getGuildDB(event);
        Auth auth = db.getAuth(AlliancePermission.VIEW_BANK);
        if (auth == null) return "No authentication found for this guild";

        event.getChannel().sendMessage("Syncing bank for " + db.getGuild());
        OffshoreInstance bank = db.getHandler().getBank();
        bank.sync(latest, false);
        return "Done!";
    }
}