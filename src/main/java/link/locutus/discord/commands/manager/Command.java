package link.locutus.discord.commands.manager;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.dummy.DelegateMessage;
import link.locutus.discord.commands.manager.dummy.DelegateMessageEvent;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.StringMan;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class Command {
    public final Set<Long> WHITELIST_USERS = new HashSet<>();
    private final List<String> aliases;
    private static final Gson gson = new Gson();
    private static final JsonParser parser = new JsonParser();
    private final Set<CommandCategory> categories;

    public static Command create(ICommand command) {
        return new Command() {
            @Override
            public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
                return command.onCommand(event, guild, author, me, args, flags);
            }
        };
    }

    public Set<CommandCategory> getCategories() {
        return categories;
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

    public String usage(String arg, MessageChannel channel) {
        if (channel != null) {
            StringBuilder response = new StringBuilder();
            if (arg != null) {
                response.append("**").append(arg).append("**\n\n");
            }
            response.append("`").append(help()).append("`").append(" - ").append(desc());
            DiscordUtil.createEmbedCommand(channel, aliases.get(0), response.toString().trim());
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
        return usage((String) null, null);
    }

    public String noPerm(String perm) {
        throw new IllegalArgumentException("No permission: " + perm);
    }

    public List<String> getAliases() {
        return aliases;
    }

    public boolean checkGuildPermission(Guild server) {
        if (server == null) {
            return true;
        }
        GuildDB guild = Locutus.imp().getGuildDB(server);
        if (guild.isWhitelisted()) return true;
        Integer perm = guild.getPermission(getClass());
        return perm != null && perm > 0;
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

    public String onCommand(Guild guild, MessageChannel channel, User user, DBNation nation, List<String> args) throws Exception {
        List<String> args2 = new ArrayList<>();
        for (String arg : args) {
            if (arg.contains(" ") && !StringMan.isQuote(arg.charAt(0)) && !StringMan.isQuote(arg.charAt(arg.length() - 1))) {
                arg = '\u201C' + arg + '\u201C';
            }
            args2.add(arg);
        }
        String cmd = StringMan.join(args2, " ");
        return onCommand(guild, channel, user, nation, cmd);
    }

    public String onCommand(Guild guild, MessageChannel channel, User user, DBNation nation, String cmd) throws Exception {
        Message msg = DelegateMessage.create(cmd, guild, user, channel);
        MessageReceivedEvent event = new DelegateMessageEvent(guild, 0, msg);
        return onCommand(event, StringMan.split(cmd, ' '), nation);
    }

    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        return onCommand(event, args, null);
    }

    private String onCommand(MessageReceivedEvent event, List<String> args, DBNation me) throws Exception {
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
        Guild guild = event.isFromGuild() ? event.getGuild() : null;
        if (me == null) me = DiscordUtil.getNation(event);
        return onCommand(event, guild, event.getAuthor(), me, args, flags);
    }

    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        return null;
    }

    @Override
    public String toString() {
        return aliases.get(0);
    }

    public boolean checkPermission(MessageReceivedEvent event) {
        return checkPermission(event.isFromGuild() ? event.getGuild() : null, event.getAuthor());
    }
}
