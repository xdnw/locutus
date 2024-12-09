package link.locutus.discord.commands.sync;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.war.WarCatReason;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.war.WarRoom;
import link.locutus.discord.commands.war.WarRoomUtil;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SyncWarRooms extends Command {
    public SyncWarRooms() {
        super(CommandCategory.DEBUG, CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.sync.warrooms.cmd);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MILCOM.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " [update|delete]";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (guild == null) return "No guild";
        GuildDB guildDB = Locutus.imp().getGuildDB(guild);
        WarCategory warCat = guildDB.getWarChannel(true);
        MessageChannel textChannel = channel instanceof DiscordChannelIO ? ((DiscordChannelIO) channel).getChannel() : null;
        if (warCat != null) {
            WarRoom room = warCat.getWarRoom((StandardGuildMessageChannel) textChannel, WarCatReason.SYNC_COMMAND);
            if (room != null) {
                if (args.size() == 1) {
                    switch (args.get(0)) {
                        case "update":
                            room.addInitialParticipants(false);
                            return "Updated " + textChannel.getAsMention();
                        case "delete":
                            String name = textChannel.getName();
                            warCat.deleteRoom(room, "Deleted by " + DiscordUtil.getFullUsername(author));
                            return "Deleted " + name;
                    }

                } else if (args.size() > 1) {
                    return usage(args.size(), 1, channel);
                }
                room.addInitialParticipants(false);
                return "Done!";
            } else if (args.isEmpty()) {
                return "No war room found";
            }

            if (args.size() >= 1) {
                switch (args.get(0).toLowerCase()) {
                    case "update": {
                        GuildMessageChannel guildChan = Locutus.imp().getDiscordApi().getGuildChannelById(Long.parseLong(args.get(1)));
                        room = WarRoomUtil.getGlobalWarRoom(guildChan, WarCatReason.SYNC_COMMAND);
                        room.addInitialParticipants(false);
                        return "Updated <#" + args.get(1) + ">";
                    }
                    case "delete":
                        Set<Category> categories = new HashSet<>();
                        Iterator<Map.Entry<Integer, WarRoom>> iter = warCat.getWarRoomMap().entrySet().iterator();
                        while (iter.hasNext()) {
                            Map.Entry<Integer, WarRoom> entry = iter.next();
                            StandardGuildMessageChannel guildChan = entry.getValue().getChannel();
                            if (guildChan != null) {
                                Category category = guildChan.getParentCategory();
                                if (category != null) categories.add(category);
                                RateLimitUtil.queue(guildChan.delete());
                            }
                            iter.remove();
                        }
                        for (Category category : categories) {
                            if (category.getName().startsWith("warcat-")) {
                                RateLimitUtil.queue(category.delete());
                            }
                        }
                        return "Deleted!";
                    case "*":
                        long start = System.currentTimeMillis();
                        warCat.sync(true);
                        long diff = System.currentTimeMillis() - start;
                        return "Done! Took: " + diff + "ms";
                    default:
                        return usage("Expected one of: `*|delete|update` received: `" + args.get(0) + "`", channel);
                }
            }
        } else {
            return "No war category found";
        }
        return "Not enabled.";
    }
}
