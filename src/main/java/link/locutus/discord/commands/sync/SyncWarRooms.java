package link.locutus.discord.commands.sync;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

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
        if (warCat != null) {
            WarCategory.WarRoom room = warCat.getWarRoom(channel);
            if (room != null) {
                if (args.size() == 1) {
                    switch (args.get(0)) {
                        case "update":
                            room.addInitialParticipants(false);
                            return "Updated " + channel.getAsMention();
                        case "delete":
                            String name = channel.getName();
                            room.delete("Deleted by " + author.getName() + "#" + author.getDiscriminator());
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
                        GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(Long.parseLong(args.get(1)));
                        room = WarCategory.getGlobalWarRoom(channel);
                        room.addInitialParticipants(false);
                        return "Updated <#" + args.get(1) + ">";
                    }
                    case "delete":
                        Set<Category> categories = new HashSet<>();
                        Iterator<Map.Entry<Integer, WarCategory.WarRoom>> iter = warCat.getWarRoomMap().entrySet().iterator();
                        while (iter.hasNext()) {
                            Map.Entry<Integer, WarCategory.WarRoom> entry = iter.next();
                            TextChannel channel = entry.getValue().getChannel(false);
                            if (channel != null) {
                                Category category = channel.getParentCategory();
                                if (category != null) categories.add(category);
                                RateLimitUtil.queue(channel.delete());
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
                        return usage(args.size(), unkown, channel);
                }
            }
        } else {
            return "No war category found";
        }
        return "Not enabled.";
    }
}
