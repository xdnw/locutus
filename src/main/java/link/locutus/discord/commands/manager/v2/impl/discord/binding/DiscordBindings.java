package link.locutus.discord.commands.manager.v2.impl.discord.binding;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.discord.GuildShardManager;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.ICategorizableChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DiscordBindings extends BindingHelper {
    @Binding(examples = {"@user", "nation"})
    public static User user(String name) {
        User user = DiscordUtil.getUser(name);
        if (user == null) {
            throw new IllegalArgumentException("No user found for: `" + name + "`");
        }
        return user;
    }

    @Binding
    public Permission key(String input) {
        return emum(Permission.class, input);
    }

    @Binding(examples = "@member")
    public static Member member(@Me Guild guild, String name) {
        if (guild == null) throw new IllegalArgumentException("Event did not happen inside a guild");
        User user = user(name);
        Member member = guild.getMember(user);
        if (member == null) throw new IllegalArgumentException("No such member: " + user.getName());
        return member;
    }

    @Binding
    public Category category(ParameterData param, Guild guild, String category) {
        if (guild == null) throw new IllegalArgumentException("Event did not happen inside a guild");
        List<Category> categories = guild.getCategoriesByName(category, true);
        Filter filter = param.getAnnotation(Filter.class);
        if (filter != null) {
            categories = new ArrayList<>(categories);
            categories.removeIf(f -> !f.getName().matches(filter.value()));
        }
        if (categories.isEmpty()) {
            if (MathMan.isInteger(category)) {
                Category result = guild.getCategoryById(category);
                if (result != null) return result;
            }
            throw new IllegalArgumentException("No category found for " + category);
        }
        if (categories.size() != 1) throw new IllegalArgumentException("No single category found for " + category);
        return categories.get(0);
    }

    @Binding
    public OnlineStatus onlineStatus(String input) {
        return StringMan.parseUpper(OnlineStatus.class, input);
    }

    @Binding(examples = "@role")
    public static Role role(@Me Guild guild, String role) {
        if (guild == null) throw new IllegalArgumentException("Event did not happen inside a guild");
        Role discordRole = DiscordUtil.getRole(guild, role);
        if (discordRole == null) throw new IllegalArgumentException("No role found for " + role);
        return discordRole;
    }

    @Binding(examples = "@role1,@role2")
    public static Set<Role> roles(@Me Guild guild, String input) {
        Set<Role> roles = new LinkedHashSet<>();
        for (String arg : input.split(",")) {
            roles.add(role(guild, arg));
        }
        return roles;
    }

    @Binding(examples = "@member1,@member2")
    public Set<Member> members(@Me Guild guild, String input) {
        Set<Member> members = new LinkedHashSet<>();
        for (String arg : input.split("[|]+")) {
            if (arg.equalsIgnoreCase("*")) {
                members.addAll(guild.getMembers());
            } else if (arg.equalsIgnoreCase("*,#verified=0") || arg.equalsIgnoreCase("#verified=0,*")) {
                for (Member member : guild.getMembers()) {
                    if (DiscordUtil.getNation(member.getUser()) == null) {
                        members.add(member);
                    }
                }
            } else {
                Set<DBNation> nations = DiscordUtil.parseNations(guild, arg);
                for (Member member : guild.getMembers()) {
                    DBNation nation = DiscordUtil.getNation(member.getUser());
                    if (nation != null && nations.contains(nation)) {
                        members.add(member);
                    }
                }
            }
        }
        return members;
    }

    @Binding
    public GuildShardManager shardManager() {
        return Locutus.imp().getDiscordApi();
    }

    @Binding(examples = "647252780817448972")
    public Guild guild(long guildId) {
        Guild guild = Locutus.imp().getDiscordApi().getGuildById(guildId);
        if (guild == null) throw new IllegalArgumentException("No guild found for: " + guildId);
        return guild;
    }

    @Binding
    @Me
    public Guild guild() {
        throw new IllegalStateException("No guild set in command locals");
    }

    @Binding(examples = "#channel")
    public MessageChannel channel(@Me Guild guild, String channel) {
        MessageChannel GuildMessageChannel = DiscordUtil.getChannel(guild, channel);
        if (GuildMessageChannel == null) throw new IllegalArgumentException("No channel found for " + channel);
        return GuildMessageChannel;
    }

    @Binding(examples = "#channel")
    public TextChannel textChannel(@Me Guild guild, String input) {
        MessageChannel channel = DiscordUtil.getChannel(guild, input);
        if (channel == null) throw new IllegalArgumentException("No channel found for " + channel);
        if (!(channel instanceof TextChannel)) throw new IllegalArgumentException("Channel " + channel + " is not a " + TextChannel.class.getSimpleName() + " but is instead of type " + channel.getClass().getSimpleName());
        return (TextChannel) channel;
    }

    @Binding(examples = "#channel")
    public ICategorizableChannel categorizableChannel(@Me Guild guild, String input) {
        MessageChannel channel = DiscordUtil.getChannel(guild, input);
        if (channel == null) throw new IllegalArgumentException("No channel found for " + channel);
        if (!(channel instanceof ICategorizableChannel)) throw new IllegalArgumentException("Channel " + channel + " is not a " + ICategorizableChannel.class.getSimpleName() + " but is instead of type " + channel.getClass().getSimpleName());
        return (ICategorizableChannel) channel;
    }

    @Binding(examples = "https://discord.com/channels/647252780817448972/973848742769885194/975827690877780050")
    public Message message(String message) {
        return DiscordUtil.getMessage(message);
    }

    @Binding
    public MessageReceivedEvent event() {
        throw new IllegalStateException("No event set in command locals");
    }

    @Binding
    @Me
    public MessageChannel channel() {
        throw new IllegalStateException("No channel set in command locals");
    }

    @Binding
    @Me
    public GuildMessageChannel guildChannel(@Me MessageChannel channel) {
        if (!(channel instanceof GuildMessageChannel)) throw new IllegalArgumentException("This command can only be used in a guild channel");
        return (GuildMessageChannel) channel;
    }

    @Binding
    @Me
    public ICategorizableChannel categorizableChannel(@Me MessageChannel channel) {
        if (!(channel instanceof ICategorizableChannel)) throw new IllegalArgumentException("This command can only be used in a categorizable channel");
        return (ICategorizableChannel) channel;
    }

    @Binding
    @Me
    public TextChannel textChannel(@Me MessageChannel channel) {
        if (!(channel instanceof TextChannel)) throw new IllegalArgumentException("This command can only be used in a guild text channel");
        return (TextChannel) channel;
    }


    @Binding
    @Me
    public Member memberInput(@Me Guild guild, User user) {
        Member member = guild.getMember(user);
        if (member == null) throw new IllegalStateException("No member found for " + user.getName());
        return member;
    }

    @Binding
    @Me
    public Member member(@Me Guild guild, @Me User user) {
        Member member = guild.getMember(user);
        if (member == null) throw new IllegalStateException("No member found for " + user.getName());
        return member;
    }

    @Binding
    @Me
    public User user() {
        throw new IllegalStateException("No user set in command locals");
    }

    @Binding
    @Me
    public Message message() {
        throw new IllegalStateException("No message set in command locals");
    }
}
