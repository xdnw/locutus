package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
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

        TextChannel channel = RateLimitUtil.complete(category.createTextChannel(embassyName).setParent(category));
        FACommands.updateEmbassyPerms(channel, role, event.getAuthor(), true);

        return "Embassy: <#" + channel.getId() + ">";
    }
}
