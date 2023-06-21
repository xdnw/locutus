package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
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
                "`" + Settings.commandPrefix(true) + "WarCat raid` or `" + Settings.commandPrefix(true) + "WarCat @borg`";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(args.size(), 1, channel);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        WarCategory warChannels = db.getWarChannel();
        if (warChannels == null) return "War channels are not enabled.";

        WarCategory.WarRoom waRoom = warChannels.getWarRoom(channel);
        if (waRoom == null) return "This command must be run in a war room.";

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
        if (categories.isEmpty()) {
            category = RateLimitUtil.complete(guild.createCategory(categoryName));
            Role milcomRole = Roles.MILCOM.toRole(guild);
            if (milcomRole != null) {
                RateLimitUtil.complete(category.upsertPermissionOverride(milcomRole).grant(Permission.VIEW_CHANNEL, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_CHANNEL, Permission.MESSAGE_MANAGE));
            }
            RateLimitUtil.queue(category.upsertPermissionOverride(guild.getRolesByName("@everyone", false).get(0))
                    .deny(net.dv8tion.jda.api.Permission.VIEW_CHANNEL));
        } else {
            category = categories.get(0);
        }

        MessageChannel currentChannel = channel;
        if (!(currentChannel instanceof ICategorizableChannel cc)) return "This channel cannot have a category.";
        if (category.equals(cc.getParentCategory())) {
            return "Already in category: " + categoryName;
        }

        RateLimitUtil.complete(cc.getManager().setParent(category));

        return "Set category for " + currentChannel.getAsMention() + " to " + categoryName;
    }
}
