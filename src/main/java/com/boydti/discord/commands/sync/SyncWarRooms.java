package com.boydti.discord.commands.sync;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.war.WarCategory;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
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
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (guild == null) return "No guild";
        GuildDB guildDB = Locutus.imp().getGuildDB(guild);
        WarCategory warCat = guildDB.getWarChannel();
        if (warCat != null) {
            WarCategory.WarRoom room = warCat.getWarRoom(event.getGuildChannel());
            if (room != null) {
                if (args.size() == 1) {
                    switch (args.get(0)) {
                        case "update":
                            room.addInitialParticipants(false);
                            return "Updated " + event.getGuildChannel().getAsMention();
                        case "delete":
                            String name = event.getGuildChannel().getName();
                            room.delete();
                            return "Deleted " + name;
                    }

                } else if (args.size() > 1) {
                    return usage(event);
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
                                com.boydti.discord.util.RateLimitUtil.queue(channel.delete());
                            }
                            iter.remove();
                        }
                        for (Category category : categories) {
                            if (category.getName().startsWith("warcat-")) {
                                com.boydti.discord.util.RateLimitUtil.queue(category.delete());
                            }
                        }
                        return "Deleted!";
                    case "*":
                        long start = System.currentTimeMillis();
                        warCat.sync(true);
                        long diff = System.currentTimeMillis() - start;
                        return "Done! Took: " + diff + "ms";
                    default:
                        return usage(event);
                }
            }
        } else {
            return "No war category found";
        }
        return "Not enabled.";
    }
}
