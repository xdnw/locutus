package link.locutus.discord.commands.manager.v2.impl.discord.binding;

import cn.easyproject.easyocr.ImageType;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.BeigeReason;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.discord.GuildShardManager;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DiscordBindings extends BindingHelper {
    @Binding(examples = {"@user", "borg"}, value = "A discord user mention, or if a nation name, id or url if they are registered")
    public static User user(@Me User selfUser, String name) {
        User user = DiscordUtil.getUser(name);
        if (user == null) {
            if (selfUser != null && name.equalsIgnoreCase("%user%")) {
                return selfUser;
            }
            throw new IllegalArgumentException("No user found for: `" + name + "`");
        }
        return user;
    }

    @Binding(examples = {"@member", "borg"}, value = "A discord user mention, or if a nation name, id or url if they are registered")
    public static Member member(@Me Guild guild, @Me User selfUser, String name) {
        if (guild == null) throw new IllegalArgumentException("Event did not happen inside a guild.");
        User user = user(selfUser, name);
        Member member = guild.getMember(user);
        if (member == null) throw new IllegalArgumentException("No such member: " + user.getName());
        return member;
    }

    @Binding(examples = {"@role", "role"}, value = "A discord role name or mention")
    public static Role role(@Me Guild guild, String role) {
        if (guild == null) throw new IllegalArgumentException("Event did not happen inside a guild.");
        Role discordRole = DiscordUtil.getRole(guild, role);
        if (discordRole == null) throw new IllegalArgumentException("No role found for " + role);
        return discordRole;
    }

    @Binding(examples = "@role1,@role2", value = "A comma separated list of discord role names or mentions")
    public static Set<Role> roles(@Me Guild guild, String input) {
        Set<Role> roles = new LinkedHashSet<>();
        for (String arg : input.split(",")) {
            roles.add(role(guild, arg));
        }
        return roles;
    }

    @Binding(examples = "interview,warcat,public", value = "A comma separated list of discord categories")
    public static Set<Category> categories(@Me Guild guild, String input) {
        Set<Category> categories = new LinkedHashSet<>();
        for (String arg : input.split(",")) {
            categories.add(category(guild, arg, null));
        }
        return categories;
    }

    @Binding(value = "A discord role permission")
    public Permission key(String input) {
        return emum(Permission.class, input);
    }

    @Binding(value = "The behavior for a command button")
    public CommandBehavior behavior(String input) {
        return emum(CommandBehavior.class, input);
    }

    @Binding(value = "A discord category name or mention", examples = "category-name")
    public static Category category(@Me Guild guild, String category, @Default ParameterData param) {
        if (guild == null) throw new IllegalArgumentException("Event did not happen inside a guild.");
        if (category.charAt(0) == '<' && category.charAt(category.length() - 1) == '>') {
            category = category.substring(1, category.length() - 1);
        }
        if (category.charAt(0) == '#') {
            category = category.substring(1);
        }
        List<Category> categories = guild.getCategoriesByName(category, true);
        Filter filter = param == null ? null : param.getAnnotation(Filter.class);
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

    @Binding(value = "A discord user online status")
    public OnlineStatus onlineStatus(String input) {
        return StringMan.parseUpper(OnlineStatus.class, input);
    }

    @Binding(value = "A comma separated list of bot Roles")
    public Set<Roles> botRoles(String input) {
        return emumSet(Roles.class, input);
    }

    @Binding(examples = {"@member1,@member2", "`*`"}, value = "A comma separated list of discord user mentions, or if a nation name, id or url if they are registered")
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

    @Binding(examples = "647252780817448972", value = "A discord guild id. See: <https://en.wikipedia.org/wiki/Template:Discord_server#Getting_Guild_ID>")
    public Guild guild(long guildId) {
        Guild guild = Locutus.imp().getDiscordApi().getGuildById(guildId);
        if (guild == null) throw new IllegalArgumentException("No guild found for: " + guildId);
        return guild;
    }

    @Binding
    @Me
    public Guild guild() {
        throw new IllegalStateException("No guild set in command locals.");
    }

    @Binding(examples = "#channel", value = "A discord channel name or mention")
    public MessageChannel channel(@Me Guild guild, String channel) {
        MessageChannel GuildMessageChannel = DiscordUtil.getChannel(guild, channel);
        if (GuildMessageChannel == null) throw new IllegalArgumentException("No channel found for " + channel);
        return GuildMessageChannel;
    }

    @Binding(examples = "#channel", value = "A discord guild channel name or mention")
    public TextChannel textChannel(@Me Guild guild, String input) {
        MessageChannel channel = DiscordUtil.getChannel(guild, input);
        if (channel == null) throw new IllegalArgumentException("No channel found for " + null);
        if (!(channel instanceof TextChannel))
            throw new IllegalArgumentException("Channel " + channel + " is not a " + TextChannel.class.getSimpleName() + " but is instead of type " + channel.getClass().getSimpleName());
        return (TextChannel) channel;
    }

    @Binding(value = "A map of a discord role to a set of roles (comma separated)",
            examples = "@Role1=@Role2,@Role3\n" +
                        "@role4=@role5,@role6"
    )
    public Map<Role, Set<Role>> roleSetMap(@Me Guild guild, String input) {
        Map<Role, Set<Role>> result = new LinkedHashMap<>();
        for (String line : input.split("[\n|\\n|;]")) {
            String[] split = line.split("[:=]");
            String key = split[0].trim();
            Role roleKey = DiscordUtil.getRole(guild, key);
            if (roleKey != null) {
                for (String roleId : split[1].split(",")) {
                    roleId = roleId.trim();
                    Role roleValue = DiscordUtil.getRole(guild, roleId);

                    if (roleValue != null) {
                        result.computeIfAbsent(roleKey, f -> new HashSet<>()).add(roleValue);
                    }
                }
            }
        }
        return result;
    }

    @Binding(examples = "#channel", value = "A categorized discord guild channel name or mention")
    public ICategorizableChannel categorizableChannel(@Me Guild guild, String input) {
        MessageChannel channel = DiscordUtil.getChannel(guild, input);
        if (channel == null) throw new IllegalArgumentException("No channel found for " + null);
        if (!(channel instanceof ICategorizableChannel))
            throw new IllegalArgumentException("Channel " + channel + " is not a " + ICategorizableChannel.class.getSimpleName() + " but is instead of type " + channel.getClass().getSimpleName());
        return (ICategorizableChannel) channel;
    }

    @Binding(examples = "https://discord.com/channels/647252780817448972/973848742769885194/975827690877780050", value = "A discord message url")
    public Message message(String message) {
        return DiscordUtil.getMessage(message);
    }

    @Binding
    public MessageReceivedEvent event() {
        throw new IllegalStateException("No event set in command locals.");
    }

    @Binding
    @Me
    public MessageChannel channel() {
        throw new IllegalStateException("No channel set in command locals.");
    }

    @Binding
    @Me
    public GuildMessageChannel guildChannel(@Me TextChannel channel) {
        if (channel == null) throw new IllegalArgumentException("This command can only be used in a guild channel.");
        return channel;
    }

    @Binding
    @Me
    public ICategorizableChannel categorizableChannel(@Me TextChannel channel) {
        if (channel == null)
            throw new IllegalArgumentException("This command can only be used in a categorize channel.");
        return channel;
    }

    @Binding
    @Me
    public TextChannel textChannel(@Me MessageChannel channel) {
        if (!(channel instanceof TextChannel))
            throw new IllegalArgumentException("This command can only be used in a guild text channel.");
        return (TextChannel) channel;
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
        throw new IllegalStateException("No user set in command locals.");
    }

    @Binding
    @Me
    public Message message() {
        throw new IllegalStateException("No message set in command locals.");
    }

    @Binding
    @Me
    public JSONObject command() {
        throw new IllegalArgumentException("No command binding found.");
    }

    @Binding
    @Me
    public IMessageIO channelIO() {
        throw new IllegalArgumentException("No channel binding found.");
    }

    @Binding
    public CommandCallable command(String input) {
        CommandCallable callable = Locutus.imp().getCommandManager().getV2().getCommands().get(input);
        if (callable == null) {
            throw new IllegalArgumentException("No command found for " + input);
        }
        return callable;
    }

    @Binding
    public PermissionHandler permissionHandler() {
        return Locutus.imp().getCommandManager().getV2().getPermisser();
    }

    @Binding(value = "An image or captcha type for Optical Character Recognition (OCR)",
            examples = "CLEAR\n" +
                        "CAPTCHA_NORMAL"
    )
    public ImageType ImageType(String input) {
        return emum(ImageType.class, input);
    }
}
