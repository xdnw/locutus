package com.boydti.discord.commands.external.guild;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.manager.dummy.DelegateChannelMessage;
import com.boydti.discord.commands.manager.dummy.DelegateContentMessage;
import com.boydti.discord.commands.manager.dummy.DelegateMessage;
import com.boydti.discord.commands.manager.dummy.DelegateMessageEvent;
import com.boydti.discord.config.Settings;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ChannelCommand extends Command {
    public ChannelCommand() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return super.help() + " <channel-name> <category> [copypasta]";
    }

    @Override
    public String desc() {
        return "Create a channel in a category\n" +
                "Add `-i` to add IA\n" +
                "Add `-m` to add milcom\n" +
                "Add `-f` to add FA\n" +
                "Add `-e` to add econ\n" +
                "Add `-a` to ping the author\n" +
                "Add `-p` to ping the users added";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.INTERNAL_AFFAIRS.toRole(server) != null;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(event);
        DBNation nation = DiscordUtil.getNation(event);

        String channelName = DiscordUtil.format(guild, event.getChannel(), author, nation, args.get(0));
        List<Category> categories = new LinkedList<>();
        List<TextChannel> channels = new LinkedList<>();
        Category freeCategory = null;
        for (String arg : args.get(1).split(",")) {
            Category category = DiscordUtil.getCategory(guild, arg);
            if (category == null) throw new IllegalArgumentException("Invalid category: `" + args.get(1) + "`");
            categories.add(category);
            channels.addAll(category.getTextChannels());
            if (freeCategory == null && category.getChannels().size() < 50) {
                freeCategory = category;
            }
        }


        Member member = guild.getMember(author);

        List<IPermissionHolder> holders = new ArrayList<>();
        holders.add(member);
        holders.addAll(member.getRoles());
        holders.add(guild.getRolesByName("@everyone", false).get(0));

        boolean hasOverride = event.getResponseNumber() == -1;

        Set<Roles> roles = new HashSet<>();
        if (flags.contains('i')) roles.add(Roles.INTERNAL_AFFAIRS);
        if (flags.contains('m')) roles.add(Roles.MILCOM);
        if (flags.contains('f')) roles.add(Roles.FOREIGN_AFFAIRS);
        if (flags.contains('e')) {
            roles.add(Roles.ECON);
            roles.add(Roles.ECON_LOW_GOV);
        }
        if (roles.isEmpty()) roles.add(Roles.INTERNAL_AFFAIRS);

        TextChannel createdChannel = null;
        for (TextChannel channel : channels) {
            if (channel.getName().equalsIgnoreCase(channelName)) {
                createdChannel = updateChannel(channel, member, roles);
                break;
            }
        }
        if (createdChannel == null) {
            if (freeCategory == null) {
                return "No free category";
            }
            if (!hasOverride) {
                for (IPermissionHolder holder : holders) {
                    PermissionOverride overrides = freeCategory.getPermissionOverride(holder);
                    if (overrides == null) continue;
                    if (overrides.getAllowed().contains(Permission.MANAGE_CHANNEL)) {
                        hasOverride = true;
                        break;
                    }
                }
                if (!hasOverride) {
                    return "No permission to create channel in: " + freeCategory.getName();
                }
            }

            createdChannel = updateChannel(RateLimitUtil.complete(freeCategory.createTextChannel(channelName)), member, roles);
            if (args.size() == 3) {
                String arg = args.get(2).toLowerCase();
                if (arg.equalsIgnoreCase("#interview")) {
                    DelegateMessage msg = new DelegateContentMessage(event.getMessage(), "!interview " + author.getAsMention() + " 0");
                    msg = new DelegateChannelMessage(msg, createdChannel);
                    MessageReceivedEvent finalEvent = new DelegateMessageEvent(event.isFromGuild() ? event.getGuild() : null, event.getResponseNumber(), msg);
                    Locutus.imp().getCommandManager().run(finalEvent);
                } else {
                    String key = "copypasta." + arg;
                    String copyPasta = Locutus.imp().getGuildDB(event).getInfo(key);
                    if (copyPasta != null) {
                        com.boydti.discord.util.RateLimitUtil.queue(createdChannel.sendMessage(copyPasta));
                    }
                }
            }
            if (flags.contains('p')) {
                StringBuilder pings = new StringBuilder();
                for (Roles dept : roles) {
                    Role role = dept.toRole(guild);
                    if (role != null) {
                        pings.append(role.getAsMention());
                    }
                }
                if (pings.length() > 0) {
                    com.boydti.discord.util.RateLimitUtil.queue(createdChannel.sendMessage(pings).complete().delete());
                }
            }
            if (flags.contains('a')) {
                com.boydti.discord.util.RateLimitUtil.queue(createdChannel.sendMessage(author.getAsMention()));
            }
        }

        return "Channel: " + createdChannel.getAsMention();
    }

    private TextChannel updateChannel(TextChannel channel, IPermissionHolder holder, Set<Roles> depts) {
        channel.putPermissionOverride(channel.getGuild().getRolesByName("@everyone", false).get(0))
                .deny(Permission.VIEW_CHANNEL).complete();
        channel.putPermissionOverride(holder).grant(Permission.VIEW_CHANNEL).complete();

        for (Roles dept : depts) {
            Role role = dept.toRole(channel.getGuild());
            if (role != null) {
                channel.putPermissionOverride(role).grant(Permission.VIEW_CHANNEL).complete();
            }
        }
        return channel;
    }
}