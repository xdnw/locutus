package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.ArrayList;
import java.util.HashSet;
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
        return """
                Create a channel in a category
                Add `-i` to add IA
                Add `-m` to add milcom
                Add `-f` to add FA
                Add `-e` to add econ
                Add `-a` to ping the author
                Add `-p` to ping the users added""";
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.channel.create.cmd);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return server != null && Roles.INTERNAL_AFFAIRS.toRoles(Locutus.imp().getGuildDB(server)) != null;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(args.size(), 2, channel);
        DBNation nation = me;

        NationPlaceholders formatter = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        String channelName = formatter.format2(guild, nation, author, args.get(0), nation, true);
        List<Category> categories = new ArrayList<>();
        List<TextChannel> channels = new ArrayList<>();
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
        assert member != null;
        holders.addAll(member.getUnsortedRoles());
        holders.add(guild.getRolesByName("@everyone", false).get(0));

        boolean hasOverride = !(channel instanceof DiscordChannelIO);

        Set<Roles> roles = new HashSet<>();
        if (flags.contains('i')) roles.add(Roles.INTERNAL_AFFAIRS);
        if (flags.contains('m')) roles.add(Roles.MILCOM);
        if (flags.contains('f')) roles.add(Roles.FOREIGN_AFFAIRS);
        if (flags.contains('e')) {
            roles.add(Roles.ECON);
            roles.add(Roles.ECON_STAFF);
        }
        if (roles.isEmpty()) roles.add(Roles.INTERNAL_AFFAIRS);

        TextChannel createdChannel = null;
        for (TextChannel catChan : channels) {
            if (catChan.getName().equalsIgnoreCase(channelName)) {
                createdChannel = updateChannel(catChan, member, roles);
                break;
            }
        }
        if (createdChannel == null) {
            if (freeCategory == null) {
                return "No free category.";
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
                    GuildDB db = Locutus.imp().getGuildDB(guild);
                    IACommands.interview(db, author);
                } else {
                    String copyPasta = Locutus.imp().getGuildDB(guild).getCopyPasta(arg, true);
                    if (copyPasta != null) {
                        RateLimitUtil.queue(createdChannel.sendMessage(copyPasta));
                    }
                }
            }
            if (flags.contains('p')) {
                StringBuilder pings = new StringBuilder();
                for (Roles dept : roles) {
                    Role role = dept.toRole2(guild);
                    if (role != null) {
                        pings.append(role.getAsMention());
                    }
                }
                if (pings.length() > 0) {
                    RateLimitUtil.queue(createdChannel.sendMessage(pings));
                }
            }
            if (flags.contains('a')) {
                RateLimitUtil.queue(createdChannel.sendMessage(author.getAsMention()));
            }
        }

        return "Channel: " + createdChannel.getAsMention();
    }

    private TextChannel updateChannel(TextChannel channel, IPermissionHolder holder, Set<Roles> depts) {
        RateLimitUtil.complete(channel.upsertPermissionOverride(channel.getGuild().getRolesByName("@everyone", false).get(0))
                .deny(Permission.VIEW_CHANNEL));
        RateLimitUtil.complete(channel.upsertPermissionOverride(holder).grant(Permission.VIEW_CHANNEL));

        for (Roles dept : depts) {
            Role role = dept.toRole2(channel.getGuild());
            if (role != null) {
                RateLimitUtil.complete(channel.upsertPermissionOverride(role).grant(Permission.VIEW_CHANNEL));
            }
        }
        return channel;
    }
}