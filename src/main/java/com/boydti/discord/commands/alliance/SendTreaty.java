package com.boydti.discord.commands.alliance;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.offshore.Auth;
import com.boydti.discord.apiv1.enums.TreatyType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class SendTreaty extends Command {
    public SendTreaty() {
        super(CommandCategory.GOV, CommandCategory.FOREIGN_AFFAIRS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).hasAuth() && Roles.FOREIGN_AFFAIRS.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " <alliance> <type> <days> <message>";
    }

    @Override
    public String desc() {
        return "Send a treaty to an alliance";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 3) return usage(event);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Auth auth = db.getAuth();
        if (auth == null) return "No authentication enabled for this guild";

        Integer aaId = PnwUtil.parseAllianceId(args.get(0));
        if (aaId == null) return "Invalid alliance: `" + args.get(0) + "`";
        TreatyType type;
        try {
            type = TreatyType.parse(args.get(1));
        } catch (IllegalArgumentException e) {
            return "Invalid treaty type: `" + args.get(1) + "`. Options: " + StringMan.getString(TreatyType.values());
        }
        int days = Integer.parseInt(args.get(2));
        for (int i = 0; i < 3; i++) args.remove(0);
        String message = StringMan.join(args, " ");
        if (message.isEmpty() && !Roles.ADMIN.has(author, guild)) {
            return "Admin is required to send a treaty with a message";
        }
        return auth.sendTreaty(aaId, type, message, days);
    }
}
