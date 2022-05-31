package com.boydti.discord.commands.bank;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.offshore.Auth;
import com.boydti.discord.util.offshore.OffshoreInstance;
import com.boydti.discord.apiv1.enums.Rank;
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
        return "!syncbanks [epoch]";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() > 1) return usage();
        Long latest = args.size() == 1 ? Long.parseLong(args.get(0)) : null;

        GuildDB db = Locutus.imp().getGuildDB(event);
        Auth auth = db.getAuth(Rank.OFFICER.id);
        if (auth == null) return "No authentication found for this guild";

        event.getChannel().sendMessage("Syncing bank for " + db.getGuild());
        OffshoreInstance bank = db.getHandler().getBank();
        bank.sync(latest, false);
        return "Done!";
    }
}