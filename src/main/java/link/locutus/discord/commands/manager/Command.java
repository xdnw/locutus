package link.locutus.discord.commands.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.*;

public abstract class Command {
    private static final Gson gson = new Gson();
    private static final JsonParser parser = new JsonParser();
    public final Set<Long> WHITELIST_USERS = new HashSet<>();
    private final List<String> aliases;
    private final Set<CommandCategory> categories;

    public Command(Object... args) {
        this.aliases = new ArrayList<>();
        this.categories = new LinkedHashSet<>();
        for (Object arg : args) {
            if (arg instanceof String) aliases.add(arg.toString());
            else if (arg instanceof CommandCategory) categories.add((CommandCategory) arg);
        }
        if (aliases.isEmpty()) {
            aliases.add(getClass().getSimpleName().replace("Command", "").toLowerCase());
        }
    }

    public static Command create(ICommand command) {
        return new Command() {
            @Override
            public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
                return command.onCommand(guild, channel, author, me, fullCommandRaw, args, flags);
            }
        };
    }

    public static JsonElement j(String str) {
        return new JsonPrimitive(str);
    }

    public static JsonElement success(String str) {
        return success(str, true);
    }

    public static JsonElement error(String str) {
        return success(str, false);
    }

    public static JsonElement success(String str, boolean value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", value);
        if (value) {
            result.put("message", str);
        } else {
            result.put("err_msg", str);
        }
        return gson.toJsonTree(result).getAsJsonObject();
    }

    public Set<CommandCategory> getCategories() {
        return categories;
    }

    public String usage(String arg, MessageChannel channel) {
        return usage(arg, channel == null ? null : new DiscordChannelIO(channel));
    }

    public String usage(int size, int expectedMin, int expectedMax, IMessageIO channel) {
        return usage("Expected between " + expectedMin + " and " + expectedMax + " arguments, got " + size + ".", channel);
    }

    public String usage(int size, int expected, IMessageIO channel) {
        return usage("Expected " + expected + " arguments, got " + size + ".", channel);
    }

    public String usage(String arg, IMessageIO channel) {
        if (channel != null) {
            StringBuilder response = new StringBuilder();
            if (arg != null) {
                response.append("**").append(arg).append("**\n\n");
            }
            response.append("`").append(help()).append("`").append("- ").append(desc());
            channel.create().embed(aliases.get(0), response.toString().trim()).send();
            throw new IllegalArgumentException("");
        }
        throw new IllegalArgumentException("Usage: " + help());
    }

    public String usage(MessageChannel channel) {
        return usage(null, channel);
    }

    public String usage(MessageReceivedEvent event) {
        return usage(null, event.getChannel());
    }

    public String usage(MessageReceivedEvent event, String arg) {
        return usage(arg, event.getGuildChannel());
    }

    public String usage() {
        return usage(null, (MessageChannel) null);
    }

    public String noPerm(String perm) {
        throw new IllegalArgumentException("No permission: " + perm);
    }

    public List<String> getAliases() {
        return aliases;
    }

    public boolean checkGuildPermission(Guild server) {
        if (server == null) {
            return false;
        }
        GuildDB guild = Locutus.imp().getGuildDB(server);
        return guild.isWhitelisted();
    }

    public boolean checkPermission(Guild server, User user) {
        return checkPermission(server, user, true) || (Roles.MEMBER.has(user, server) && server != null && Locutus.imp().getGuildDB(server).isAllyOfRoot());
    }

    public boolean checkPermission(Guild server, User user, boolean checkGuild) {
        if (user.getIdLong() == Settings.INSTANCE.APPLICATION_ID) {
            return true;
        }
        if (user.getIdLong() == Settings.INSTANCE.ADMIN_USER_ID) {
            return true;
        }
        if (checkGuild && !checkGuildPermission(server)) {
            return false;
        }
        Guild rootGuild = Locutus.imp().getServer();

        long userId = user.getIdLong();
        if (WHITELIST_USERS.contains(userId)) {
            return true;
        }

        if (server == null) {
            server = rootGuild;
        }

        Member rootMember = server.getMemberById(user.getIdLong());
        if (rootMember != null) {
            return Roles.MEMBER.has(rootMember);
        }
        return false;
    }

    public String help() {
        return Settings.commandPrefix(true) + aliases.get(0);
    }

    public String desc() {
        return "Run the " + aliases.get(0) + " command";
    }

    public abstract String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception;

    public String onCommand(Guild guild, MessageChannel channel, User user, DBNation me, String command) throws Exception {
        return onCommand(guild, new DiscordChannelIO(channel), user, me, command);

    }

    public String onCommand(Guild guild, IMessageIO channel, User user, DBNation me, String command) throws Exception {
        List<String> split = StringMan.split(command, ' ');
        return onCommand(guild, channel, user, me, command, split);
    }

    public String onCommand(Guild guild, MessageChannel channel, User user, DBNation me, String fullCommandRaw, List<String> args) throws Exception {
        return onCommand(guild, new DiscordChannelIO(channel), user, me, fullCommandRaw, args, new HashSet<>());
    }

    public String onCommand(Guild guild, IMessageIO channel, User user, DBNation me, String fullCommandRaw, List<String> args) throws Exception {
        Set<Character> flags = new HashSet<>();
        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            String next = iterator.next();
            if (next.isEmpty() || next.contains("http") || next.contains("{")) continue;
            if (next.charAt(0) == '-' && next.length() == 2 && Character.isAlphabetic(next.charAt(1))) {
                flags.add(next.charAt(1));
                iterator.remove();
            }
        }
        return onCommand(guild, channel, user, me, fullCommandRaw, args, flags);
    }

    @Override
    public String toString() {
        return aliases.get(0);
    }
}
