package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ICategorizableChannel;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class WarCat extends Command {
    public WarCat() {
        super(CommandCategory.MILCOM, CommandCategory.MEMBER);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MILCOM.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " [category]";
    }

    @Override
    public String desc() {
        return "Run this command in a war room to assign it to a category e.g.\n" +
                "`" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "WarCat raid` or `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "WarCat @borg`";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);

        GuildDB db = Locutus.imp().getGuildDB(event);
        WarCategory warChannels = db.getWarChannel();
        if (warChannels == null) return "War channels are not enabled";

        WarCategory.WarRoom waRoom = warChannels.getWarRoom(event.getGuildChannel());
        if (waRoom == null) return "This command must be run in a war room";

        String categoryName = args.get(0);
        if (categoryName.startsWith("<")) {
            DBNation nation = DiscordUtil.parseNation(categoryName);
            if (nation == null) {
                return "Unregistered: `" + args.get(0) + "`";
            }
            categoryName = nation.getNation();
        }

        categoryName = warChannels.getCatPrefix() + "-" + categoryName;

        List<Category> categories = guild.getCategoriesByName(categoryName, true);
        Category category;
        if (categories == null || categories.isEmpty()) {
            category = RateLimitUtil.complete(guild.createCategory(categoryName));
            Role milcomRole = Roles.MILCOM.toRole(guild);
            if (milcomRole != null) {
                category.putPermissionOverride(milcomRole).grant(Permission.VIEW_CHANNEL, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_CHANNEL, Permission.MESSAGE_MANAGE).complete();
            }
            RateLimitUtil.queue(category.putPermissionOverride(guild.getRolesByName("@everyone", false).get(0))
                    .deny(net.dv8tion.jda.api.Permission.VIEW_CHANNEL));
        } else {
            category = categories.get(0);
        }

        MessageChannel currentChannel = event.getChannel();
        if (!(currentChannel instanceof ICategorizableChannel)) return "This channel cannot have a category";
        ICategorizableChannel cc = (ICategorizableChannel) currentChannel;
        if (category.equals(cc.getParentCategory())) {
            return "Already in category: " + categoryName;
        }

        cc.getManager().setParent(category).complete();

        return "Set category for " + currentChannel.getAsMention() + " to " + categoryName;
    }
}
