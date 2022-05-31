package com.boydti.discord.commands.account;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.manager.v2.impl.pw.commands.FACommands;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;

public class Embassy extends Command {
    public Embassy() {
        super(CommandCategory.FOREIGN_AFFAIRS, CommandCategory.USER_COMMANDS);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        DBNation me = DiscordUtil.getNation(event);
        if (me == null) {
            return "Please use `!validate`";
        }
        GuildDB db = Locutus.imp().getGuildDB(event);
        Category category = db.getOrThrow(GuildDB.Key.EMBASSY_CATEGORY);
        if (category == null) {
            return "Embassies are disabled. To set it up, use `!KeyStore " + GuildDB.Key.EMBASSY_CATEGORY + " <category>`";
        }
        if (args.size() == 1 && args.get(0).equalsIgnoreCase("*")) {
            if (!Roles.ADMIN.has(event.getAuthor(), event.getGuild())) return "No permission";
            Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(event.getGuild().getRoles());
            for (TextChannel channel : category.getTextChannels()) {
                String[] split = channel.getName().split("-");
                if (split.length <= 1) continue;
                String allianceIdStr = split[split.length - 1];
                if (MathMan.isInteger(allianceIdStr)) {
                    int allianceId = Integer.parseInt(split[split.length - 1]);
                    Role role = aaRoles.get(allianceId);
                    if (role != null) {
                        FACommands.updateEmbassyPerms(channel, role, event.getAuthor(), true);
                    }
                }
            }
            return "Done!";
        } else if (args.size() == 1) {
            if (Roles.ADMIN.has(event.getAuthor(), event.getGuild()));
        }

        if (me.getAlliance_id() == 0) {
            return "You are not in an alliance";
        }
        int aa = me.getAlliance_id();
        String aaName = Locutus.imp().getNationDB().getAllianceName(aa);

        Role role = DiscordUtil.getAARoles(event.getGuild().getRoles()).get(aa);
        if (role == null) {
            return "No role found (try using `!autorole` ?)";
        }

        for (TextChannel channel : category.getTextChannels()) {
            String[] split = channel.getName().split("-");
            if (MathMan.isInteger(split[split.length - 1]) && Integer.parseInt(split[split.length - 1]) == aa) {
                FACommands.updateEmbassyPerms(channel, role, event.getAuthor(), true);
                return "Embassy: <#" + channel.getId() + ">";
            }
        }
        if (me.getPosition() <= 1) {
            return "You must be a member to create an embassy";
        }

        String embassyName = aaName + "-" + aa;

        TextChannel channel = com.boydti.discord.util.RateLimitUtil.complete(category.createTextChannel(embassyName).setParent(category));
        FACommands.updateEmbassyPerms(channel, role, event.getAuthor(), true);

        return "Embassy: <#" + channel.getId() + ">";
    }
}
