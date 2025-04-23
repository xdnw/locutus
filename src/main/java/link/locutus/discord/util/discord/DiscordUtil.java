package link.locutus.discord.util.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.shrink.EmbedShrink;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationModifier;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import com.google.common.base.Charsets;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.entities.UserImpl;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONObject;
import org.jsoup.internal.StringUtil;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import link.locutus.discord.util.scheduler.KeyValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static graphql.Assert.assertTrue;

public class DiscordUtil {
    public static String getSupportServer() {
        return "https://discord.gg/" + Settings.INSTANCE.SUPPORT_INVITE;
    }
    public static Color BACKGROUND_COLOR = Color.decode("#36393E");

    public static String trimContent(String content) {
        if (content.isEmpty()) return content;
        if (content.charAt(0) == '<') {
            int end = content.indexOf('>');
            if (end != -1) {
                String idStr = content.substring(1, end);
                content = content.substring(end + 1).trim();
                if (content.length() > 0 && Character.isAlphabetic(content.charAt(0)) && MathMan.isInteger(idStr) && Long.parseLong(idStr) == Settings.INSTANCE.APPLICATION_ID) {
                    String prefix = Settings.commandPrefix(true);
                    if (!content.startsWith(prefix)) {
                        content = prefix + content;
                    }
                }
            }
        }
        return content;
    }

    public static boolean setSymbol(StandardGuildMessageChannel channel, String symbol, boolean value) {
        if (channel != null) {
            String name = channel.getName();
            if (name.contains(symbol) != value) {
                if (value) {
                    name = symbol + name;
                } else if (name.contains(symbol)) {
                    name = name.replace(symbol, "");
                    if (name.endsWith("-")) name = name.substring(0, name.length() - 1);
                } else {
                    return false;
                }
                RateLimitUtil.queue(channel.getManager().setName(name));
                return true;
            }
        }
        return false;
    }

    public static void deleteChannelSafe(GuildChannel channel) {
        try {
            if (channel != null) {
                RateLimitUtil.queue(channel.delete());
            }
        } catch (ErrorResponseException e) {
            if (e.getErrorCode() != 10003) {
                e.printStackTrace();
            }
        }
    }

    public static String userUrl(long discordId, boolean redirect) {
        if (redirect) {
            return "https://tinyurl.com/3xbkmzjm/" + discordId;
        }
        return "discord://discordapp.com/users/" + discordId;
    }

    public static String getGuildName(long id) {
        Guild guild = Locutus.imp().getDiscordApi().getGuildById(id);
        return guild == null ? id + "" : guild.getName() + "/" + id;
    }

    public static String getGuildUrl(long id) {
        return "https://discordapp.com/channels/" + id;
    }

    public static String getUserName(long id) {
        User user = Locutus.imp().getDiscordApi().getUserById(id);
        return user == null ? "<@" + id + ">" : getFullUsername(user);
    }

    public static CompletableFuture<Message> upload(MessageChannel channel, String title, String body) {
        if (!title.contains(".")) title += ".txt";
        return RateLimitUtil.queue(channel.sendFiles(FileUpload.fromData(body.getBytes(StandardCharsets.ISO_8859_1), title)));
    }

    public static void sortInterviewChannels(List<? extends GuildMessageChannel> channels, Map<Long, List<InterviewMessage>> messages, Map<? extends GuildMessageChannel, User> interviewUsers) {
        Collections.sort(channels, new Comparator<GuildMessageChannel>() {
            @Override
            public int compare(GuildMessageChannel o1, GuildMessageChannel o2) {
                List<InterviewMessage> m1s = messages.getOrDefault(o1.getIdLong(), Collections.emptyList());
                List<InterviewMessage> m2s = messages.getOrDefault(o2.getIdLong(), Collections.emptyList());

                long created1 = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(o1).toEpochSecond();
                long created2 = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(o2).toEpochSecond();

                long lastMessage1 = o1.getLatestMessageIdLong() > 0 ? net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(o1.getLatestMessageIdLong()).toEpochSecond() : created1;
                long lastMessage2 = o2.getLatestMessageIdLong() > 0 ? net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(o2.getLatestMessageIdLong()).toEpochSecond() : created2;

                if (m1s.isEmpty()) {
                    if (m2s.isEmpty()) {
                        return Long.compare(lastMessage2, lastMessage1);
                    }
                    return 1;
                } else if (m2s.isEmpty()) {
                    return -1;
                }

                InterviewMessage m1Latest = m1s.get(0);
                InterviewMessage m2Latest = m2s.get(0);

                lastMessage1 = m1Latest.date_created;
                lastMessage2 = m2Latest.date_created;

                User user1 = interviewUsers.get(o1);
                User user2 = interviewUsers.get(o2);

                if (user1 == null) {
                    if (user2 == null) {
                        return Long.compare(lastMessage2, lastMessage1);
                    } else {
                        return 1;
                    }
                } else if (user2 == null) {
                    return -1;
                }

                if (m1Latest.sender != user1.getIdLong()) {
                    if (m2Latest.sender != user2.getIdLong()) {
                        return Long.compare(lastMessage2, lastMessage1);
                    } else {
                        return 1;
                    }
                } else if (m2Latest.sender != user2.getIdLong()) {
                    return -1;
                }

                return Long.compare(lastMessage2, lastMessage1);
            }
        });
    }

    public static String toRoleString(Map<Long, Role> roleMap) {
        List<String> sub = new ArrayList<>();
        Role discordRole = roleMap.get(0);
        if (discordRole != null) {
            sub.add("@" + discordRole.getName());
        }
        for (Map.Entry<Long, Role> entry : roleMap.entrySet()) {
            if (entry.getKey() != null) {
                Role role = entry.getValue();
                sub.add(PW.getName(entry.getKey(), true) + ": @" + role.getName());
            }
        }
        if (!sub.isEmpty()) {
            return StringMan.join(sub, ", ");
        } else {
            return "";
        }
    }

    public static Long getChannelId(Guild guild, String keyOrLong) {
        if (keyOrLong.charAt(0) == '<' && keyOrLong.charAt(keyOrLong.length() - 1) == '>') {
            keyOrLong = keyOrLong.substring(1, keyOrLong.length() - 1);
        }
        if (keyOrLong.charAt(0) == '#') {
            keyOrLong = keyOrLong.substring(1);
        }
        if (MathMan.isInteger(keyOrLong)) {
            return Long.parseLong(keyOrLong);
        }
        if (guild != null) {
            MessageChannel channel = getChannel(guild, keyOrLong);
            if (channel != null) {
                return channel.getIdLong();
            }
            throw new IllegalArgumentException("Channel not found: `" + keyOrLong + "`");
        }
        throw new IllegalArgumentException("Channel not found (not a number): `" + keyOrLong + "`");
    }

    public static MessageChannel getChannel(Guild guild, String keyOrLong) {
        if (keyOrLong.charAt(0) == '<' && keyOrLong.charAt(keyOrLong.length() - 1) == '>') {
            keyOrLong = keyOrLong.substring(1, keyOrLong.length() - 1);
        }
        if (keyOrLong.charAt(0) == '#') {
            keyOrLong = keyOrLong.substring(1);
        }
        if (MathMan.isInteger(keyOrLong)) {
            long idLong = Long.parseLong(keyOrLong);
            GuildChannel channel = guild != null ? guild.getGuildChannelById(idLong) : null;
            if (channel == null) {
                channel = Locutus.imp().getDiscordApi().getGuildChannelById(idLong);
            }
            if (channel == null) {
//                channel = Locutus.imp().getDiscordApi().getPrivateChannelById(keyOrLong);
//                if (channel == null) {
//                    throw new IllegalArgumentException("Invalid channel " + keyOrLong);
//                }
            } else {
                if (!(channel instanceof MessageChannel)) {
                    throw new IllegalArgumentException("Invalid channel <#" + keyOrLong + "> (not a message channel)");
                }
                long channelGuildId = channel.getGuild().getIdLong();
                if (guild != null) {
                    GuildDB db = Locutus.imp().getGuildDB(guild);
                    GuildDB faServer = db.getOrNull(GuildKey.FA_SERVER);
                    Guild maServer = db.getOrNull(GuildKey.WAR_SERVER);
                    if (faServer != null && channelGuildId == faServer.getIdLong()) {
                        return (MessageChannel) channel;
                    }
                    if (maServer != null && channelGuildId == maServer.getIdLong()) {
                        return (MessageChannel) channel;
                    }
                }
                if (guild != null && channelGuildId != guild.getIdLong()) {
                    GuildDB otherDB = Locutus.imp().getGuildDB(channel.getGuild());
                    Map.Entry<Integer, Long> delegate = otherDB.getOrNull(GuildKey.DELEGATE_SERVER);
                    if (delegate == null || delegate.getValue() != guild.getIdLong()) {
                        throw new IllegalArgumentException("Channel: " + keyOrLong + " not in " + guild + " (" + guild + ")");
                    }
                }
            }
            return (MessageChannel) channel;
        }
        List<TextChannel> channel = guild.getTextChannelsByName(keyOrLong, true);
        if (channel.size() == 1) {
            return channel.get(0);
        }
        return null;
    }

    public static String parseArg(List<String> args, String prefix) {
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String next = iter.next();
            if (next.startsWith(prefix + ":")) {
                iter.remove();
                return next.split(":", 2)[1];
            }
            if (next.startsWith(prefix + "=")) {
                iter.remove();
                return next.split("=", 2)[1];
            }
        }
        return null;
    }

    public static <T> T parseArgFunc(List<String> args, String prefix, Function<String, T> parser) {
        String arg = parseArg(args, prefix);
        return arg == null ? null : parser.apply(arg);
    }
    public static Integer parseArgInt(List<String> args, String prefix) {
        String arg = parseArg(args, prefix);
        return arg == null ? null : MathMan.parseInt(arg);
    }

    public static Double parseArgDouble(List<String> args, String prefix) {
        String arg = parseArg(args, prefix);
        return arg == null ? null : MathMan.parseDouble(arg);
    }

    public static void paginate(MessageChannel channel, String title, String command, Integer page, int perPage, List<String> results) {
        paginate(channel, title, command, page, perPage, results, null);
    }

    public static void paginate(MessageChannel channel, String title, String command, Integer page, int perPage, List<String> results, String footer) {
        paginate(channel, null, title, command, page, perPage, results, footer, false);
    }

    public static void paginate(MessageChannel channel, Message message, String title, String command, Integer page, int perPage, List<String> results, String footer, boolean inline) {
        DiscordChannelIO io = new DiscordChannelIO(channel, () -> message);
        paginate(io, title, command, page, perPage, results, footer, inline);
    }

    public static String timestamp(long timestamp, String type) {
        if (type == null) type = "R";
        return "<t:" + (timestamp / 1000L) + ":" + type + ">";
    }

    public static void paginate(IMessageIO io, String title, String command, Integer page, int perPage, List<String> results, String footer, boolean inline) {
        if (results.isEmpty()) {
            return;
        }

        int numResults = results.size();
        int maxPage = (numResults - 1) / perPage;
        if (page == null) page = 0;

        int start = page * perPage;
        int end = Math.min(numResults, start + perPage);

        StringBuilder body = new StringBuilder();
        for (int i = start; i < end; i++) {
            body.append(results.get(i)).append('\n');
        }

        Map<String, String> reactions = new LinkedHashMap<>();
        boolean hasPrev = page > 0;
        boolean hasNext = page < maxPage;

        String previousPageCmd;
        String nextPageCmd;

        if (command.charAt(0) == Settings.commandPrefix(true).charAt(0)) {
            String cmdCleared = command.replaceAll("page:" + "[0-9]+", "");
            previousPageCmd = cmdCleared + " page:" + (page - 1);
            nextPageCmd = cmdCleared + " page:" + (page + 1);
        } else if (command.charAt(0) == '{') {
            JSONObject json = new JSONObject(command, JSONObject.getNames(command));
            Map<String, Object> arguments = json.toMap();

            previousPageCmd = new JSONObject(arguments).put("page", page - 1).toString();
            nextPageCmd = new JSONObject(arguments).put("page", page + 1).toString();
        } else {
            String cmdCleared = command.replaceAll("-p " + "[0-9]+", "");
            if (!cmdCleared.isEmpty() && !Locutus.cmd().isModernPrefix(cmdCleared.charAt(0))) {
                cmdCleared = Settings.commandPrefix(false) + cmdCleared;
            }
            previousPageCmd = cmdCleared + " -p " + (page - 1);
            nextPageCmd = cmdCleared + " -p " + (page + 1);
        }

        String prefix = inline ? "~" : "";
        if (hasPrev) {
            reactions.put("\u2B05\uFE0F Previous", prefix + previousPageCmd);
        }
        if (hasNext) {
            reactions.put("Next \u27A1\uFE0F", prefix + nextPageCmd);
        }

        IMessageBuilder message = io.getMessage();
        if (message == null || message.getAuthor() == null || message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
            message = io.create();
        }

        EmbedShrink builder = new EmbedShrink();
        builder.setTitle(title);
        builder.setDescription(body.toString());
        if (footer != null) builder.setFooter(footer);

        message.clearEmbeds();
        message.embed(builder);
        message.clearButtons();
        message.addCommands(reactions);

        message.send();
    }

    public static void createEmbedCommand(long chanelId, String title, String message, String... reactionArguments) {
        MessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(chanelId);
        if (channel == null) {
            throw new IllegalArgumentException("Invalid channel " + chanelId);
        }
        createEmbedCommand(channel, title, message, reactionArguments);
    }

    public static void createEmbedCommand(MessageChannel channel, String title, String message, String... reactionArguments) {
        createEmbedCommandWithFooter(channel, title, message, null, reactionArguments);
    }

    public static void createEmbedCommandWithFooter(MessageChannel channel, String title, String message, String footer, String... reactionArguments) {
        if (message.length() + (footer == null ? 0 : footer.length()) > MessageEmbed.DESCRIPTION_MAX_LENGTH && reactionArguments.length == 0) {
            if (title.length() > MessageEmbed.TITLE_MAX_LENGTH) title = title.substring(0, MessageEmbed.TITLE_MAX_LENGTH - 3) + "...";
            DiscordUtil.upload(channel, title, message);
            return;
        }
        String finalTitle = title;
        createEmbedCommand(channel, new Consumer<EmbedShrink>() {
            @Override
            public void accept(EmbedShrink builder) {
                String titleFinal = finalTitle;
                if (titleFinal.length() > MessageEmbed.TITLE_MAX_LENGTH) {
                    titleFinal = titleFinal.substring(0, MessageEmbed.TITLE_MAX_LENGTH - 3) + "..";
                }
                builder.setTitle(titleFinal);
                builder.setDescription(message);
                if (footer != null) builder.setFooter(footer);
                builder.shrinkDefault();
            }
        }, reactionArguments);
    }

    public static void createEmbedCommand(MessageChannel channel, Consumer<EmbedShrink> builder, String... reactionArguments) {
        if (reactionArguments.length % 2 != 0) {
            throw new IllegalArgumentException("invalid pairs: " + StringMan.getString(reactionArguments));
        }
        HashMap<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < reactionArguments.length; i+=2) {
            map.put(reactionArguments[i], reactionArguments[i + 1]);
        }
        createEmbedCommand(channel, builder, map);
    }

    public static Message getMessage(String url) {
        String[] split = url.split("/");
        long messageId = Long.parseLong(split[split.length - 1]);
        long channelId = Long.parseLong(split[split.length - 2]);
        long guildId = Long.parseLong(split[split.length - 3]);

        return getMessage(guildId, channelId, messageId);
    }

    public static Message getMessage(long guildId, long channelId, long messageId) {
        Guild guild = Locutus.imp().getDiscordApi().getGuildById(guildId);
        if (guild == null) return null;
        GuildChannel channel = guild.getGuildChannelById(channelId);
        if (channel instanceof GuildMessageChannel gmc) {
            Message message = RateLimitUtil.complete(gmc.retrieveMessageById(messageId));
            return message;
        }
        return null;
    }


    public static void createEmbedCommand(MessageChannel channel, Consumer<EmbedShrink> consumer, Map<String, String> reactionArguments) {
        EmbedShrink builder = new EmbedShrink();
        consumer.accept(builder);

        new DiscordChannelIO(channel, null).create().embed(builder).addCommands(reactionArguments).send();
    }

//    public static String format2(Guild guild, User callerUser, DBNation callerNation, String message, User user, DBNation nation) {
//        if (user != null && message.contains("%user%")) {
//            message = message.replace("%user%", user.getAsMention());
//        }
//        return Locutus.imp().getCommandManager().getV2().getNationPlaceholders().format(guild, callerNation, callerUser, message, nation);
//    }

//    public static String format(Guild guild, MessageChannel channel, User callerUser, DBNation callerNation, String message) {
//        if (user != null && message.contains("%user%")) {
//            message = message.replace("%user%", user.getAsMention());
//        }
//        String result = parser.parse(guild, channel, user, nation, message);
//        if (result.indexOf('{') != -1 && result.indexOf('}') != -1) {
//            for (NationMeta value : NationMeta.values) {
//                String arg = "{" + value.name().toLowerCase() + "}";
//                if (message.contains(arg)) {
//                    message = message.replace(arg, value.toString(nation.getMeta(value)));
//                }
//            }
//        }
//        return result;
//    }

    public static class CommandInfo {
        public final Long channelId;
        public final CommandBehavior behavior;
        public final String command;

        public CommandInfo(Long channelId, CommandBehavior behavior, String command) {
            this.channelId = channelId;
            this.behavior = behavior;
            this.command = command;
        }
    }

    private static List<CommandInfo> parseCommands(Guild guild, String label, String id, Map<String, String> reactions, String ref) {
        if (id == null) {
            return null;
        }
        if (id.isBlank()) {
            CommandInfo info = new CommandInfo(null, CommandBehavior.DELETE_MESSAGE, " ");
            return List.of(info);
        }
        if (MathMan.isInteger(id)) {
            if (reactions.isEmpty()) {
                throw new IllegalArgumentException("No command info found: " + ref);
            }
            String cmd = reactions.get(id);
            if (cmd == null) {
                throw new IllegalArgumentException("No command info found: " + ref + " | " + id + " | " + StringMan.getString(reactions));
            }
            id = cmd;
        }

        Long channelId;
        if (id.startsWith("<#")) {
            String channelIdStr = id.substring(0, id.indexOf('>') + 1);
            channelId = DiscordUtil.getChannelId(guild, channelIdStr);
            id = id.substring(id.indexOf(' ') + 1);
        } else {
            channelId = null;
        }

        CommandBehavior behavior = null;
        if (!id.isEmpty()) {
            char char0 = id.charAt(0);
            behavior = CommandBehavior.getOrNull(char0 + "");
            if (behavior != null) {
                id = id.substring(behavior.getValue().length());
            } else {
                behavior = CommandBehavior.DELETE_MESSAGE;
            }
        }

        if (!id.isEmpty() && (id.startsWith(Settings.commandPrefix(true)) || Locutus.cmd().isModernPrefix(id.charAt(0)))) {
            List<String> split = Arrays.asList(id.split("\\r?\\n(?=[" + StringMan.join(Locutus.cmd().getAllPrefixes(), "|") + "|{])"));
            List<CommandInfo> infos = new ArrayList<>(split.size());
            for (String cmd : split) {
                infos.add(new CommandInfo(channelId, behavior, cmd));
            }
            return infos;
        } else if (id.startsWith("{")){
            List<String> split;
            if (id.contains("\n{")) {
                split = Arrays.asList(id.split("\\r?\\n(?=\\{)"));
            } else {
                split = List.of(id);
            }
            CommandBehavior finalBehavior = behavior;
            return split.stream().map(cmd -> new CommandInfo(channelId, finalBehavior, cmd)).toList();
        } else if (!id.isEmpty()) {
            throw new IllegalArgumentException("Unknown discord command: `" + id + "`");
        } else {
            return List.of(new CommandInfo(channelId, behavior, id));
        }
    }

    public static Map<String, List<CommandInfo>> getCommands(Guild guild, MessageEmbed embed, List<Button> buttons, String ref, boolean checkNonButtons) {
        Map<String, List<CommandInfo>> commands = new LinkedHashMap<>();

        Map<String, String> reactions = null;
        boolean initReactions = true;

        for (Button button : buttons) {
            if (initReactions) {
                if (embed == null) throw new IllegalArgumentException("No embed found");
                reactions = DiscordUtil.getReactions(embed);
                initReactions = false;
            }
            String id = button.getId();
            String label = button.getLabel();
            List<CommandInfo> cmds = parseCommands(guild, label, id, reactions, ref);
            if (cmds == null) continue;
            commands.put(label, cmds);
        }
        if (checkNonButtons && buttons.isEmpty()) {
            if (initReactions) {
                if (embed == null) throw new IllegalArgumentException("No embed found");
                reactions = DiscordUtil.getReactions(embed);
            }
            if (reactions == null) {
                throw new IllegalArgumentException("No command info found (no buttons or reactions): " + ref);
            }
            for (Map.Entry<String, String> entry : reactions.entrySet()) {
                String label = entry.getKey();
                String id = entry.getValue();
                List<CommandInfo> cmds = parseCommands(guild, label, id, reactions, ref);
                if (cmds == null) continue;
                commands.put(label, cmds);
            }
        }
        return commands;
    }

    public static Map<String, String> getReactions(MessageEmbed embed) {
        MessageEmbed.ImageInfo img = embed.getImage();
        MessageEmbed.Thumbnail thumb = embed.getThumbnail();
        if (thumb == null && img == null) {
            return null;
        }

        String url = thumb != null && thumb.getUrl() != null && !thumb.getUrl().isEmpty() ? thumb.getUrl() : img.getUrl();
        if (url == null) {
            return null;
        }

        String placeholder = "https://example.com?";
        if (!url.startsWith(placeholder)) {
            return null;
        }
        String query = url.substring(placeholder.length());
        List<NameValuePair> entries = URLEncodedUtils.parse(query, Charsets.UTF_8);
        Map<String, String> map = new LinkedHashMap<>();
        for (NameValuePair entry : entries) {
            map.put(entry.getName(), entry.getValue());
        }
        return map;
    }

    public static Map.Entry<Integer, Integer> getCityRange(String name) {
        var char0 = name.charAt(0);
        if (char0 != 'c' && char0 != 'C') return null;

        String[] range = name.substring(1).split("-");
        int start = -1;
        int end = -1;
        switch (range.length) {
            default:
                return null;
            case 2:
                if (!StringUtil.isNumeric(range[1])) return null;
                end = Integer.parseInt(range[1]);
            case 1:
                if (range[0].endsWith("+")) {
                    range[0] = range[0].substring(0, range[0].length() - 1);
                    end = Integer.MAX_VALUE;
                }
                if (!StringUtil.isNumeric(range[0])) return null;
                start = Integer.parseInt(range[0]);
                if (end == -1) {
                    end = start;
                }
        }
        if (end < start) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        start = Math.max(1, start);
        end = Math.min(70, end);
        return new KeyValue<>(start, end);
    }

    public static Map<Integer, Set<Role>> getCityRoles(Collection<Role> roles) {
        Map<Integer, Set<Role>> rolesByCity = new HashMap<>();
        for (Role role : roles) {
            String name = role.getName();
            if (name.isEmpty()) continue;
            Map.Entry<Integer, Integer> range = getCityRange(name);
            if (range == null) continue;
            int start = range.getKey();
            int end = range.getValue();
            for (int city = start; city <= end; city++) {
                rolesByCity.computeIfAbsent(city, f -> new HashSet<>()).add(role);
            }
        }
        return rolesByCity;
    }

    public static Map<Integer, Set<Role>> getAARolesIncDuplicates(Collection<Role> roles) {
        Map<Integer, Set<Role>> allianceRoles = new HashMap<>();
        for (Role role : roles) {
            if (role.getName().startsWith("AA ")) {
                String[] split = role.getName().split(" ");
                if (split.length < 2) continue;
                String idStr = split[1];
                if (!MathMan.isInteger(idStr)) continue;
                int id = Integer.parseInt(idStr);
                allianceRoles.computeIfAbsent(id, f -> new HashSet<>()).add(role);
            }
        }
        return allianceRoles;
    }

    public static Map<Integer, Role> getAARoles(Collection<Role> roles) {
        Map<Integer, Role> allianceRoles = null;
        for (Role role : roles) {
            if (role.getName().startsWith("AA ")) {
                String[] split = role.getName().split(" ");
                if (split.length < 2) continue;
                String idStr = split[1];
                if (!MathMan.isInteger(idStr)) continue;
                int id = Integer.parseInt(idStr);
                if (allianceRoles == null) {
                    allianceRoles = new HashMap<>();
                }
                allianceRoles.put(id, role);
            } else {

            }
        }
        return allianceRoles == null ? Collections.emptyMap() : allianceRoles;
    }

    public static Role getRole(Guild server, String arg) {
        List<Role> discordRoleOpt = server.getRolesByName(arg, true);
        if (discordRoleOpt.size() == 1) {
            return discordRoleOpt.get(0);
        }
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '@') {
            arg = arg.substring(1);
            if (arg.charAt(0) == '&') arg = arg.substring(1);
        }
        if (MathMan.isInteger(arg)) {
            long discordId = Long.parseLong(arg);
            return server.getRoleById(discordId);
        }
        return null;
    }

    public static User getMention(String arg) {
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '@') {
            String idStr = arg.substring(1);
            if (idStr.charAt(0) == '!') idStr = idStr.substring(1);
            if (MathMan.isInteger(idStr)) {
                long discordId = Long.parseLong(idStr);
                return Locutus.imp().getDiscordApi().getUserById(discordId);
            }
        }
        if (MathMan.isInteger(arg)) {
            User user = Locutus.imp().getDiscordApi().getUserById(Long.parseLong(arg));
            if (user != null) return user;
        }
        String[] split = arg.split("#");
        if (split.length == 2) {
            return Locutus.imp().getDiscordApi().getUserByTag(split[0], split[1]);
        }
        if (split[0].isEmpty()) return null;
        List<User> users = Locutus.imp().getDiscordApi().getUsersByName(split[0], true);
        return users.size() == 1 ? users.get(0) : null;
    }

    private final static Map<Long, DBNation> temp = new ConcurrentHashMap<>();

    public static DBNation getNation(MessageReceivedEvent event) {
        DBNation tempUser = temp.get(Thread.currentThread().getId());
        if (tempUser != null) {
            return tempUser;
        }
        User user = event.getAuthor();
        return getNation(user.getIdLong());
    }

    public static DBNation getNation(User user) {
        return getNation(user.getIdLong());
    }

    public static DBNation getNation(long userId) {
        DiscordDB disDb = Locutus.imp().getDiscordDB();
        PNWUser pnwUser = disDb.getUserFromDiscordId(userId);
        if (pnwUser != null) {
            return DBNation.getById(pnwUser.getNationId());
        }
        return null;
    }

    public static void initThreadLocals() {
        temp.remove(Thread.currentThread().getId());
    }

    public static <T> T withNation(DBNation nation, Callable<T> task) throws Exception {
        return withNation(nation, task, true);
    }

    public static <T> T withNation(DBNation nation, Callable<T> task, boolean remove) throws Exception {
        DBNation previous = null;
        if (remove) initThreadLocals();
        else previous = temp.get(Thread.currentThread().getId());
        try {
            if (nation != null) {
                temp.put(Thread.currentThread().getId(), nation);
            }
            return task.call();
        } finally {
            if (remove || previous == null) temp.remove(Thread.currentThread().getId());
            else {
                temp.put(Thread.currentThread().getId(), previous);
            }
        }
    }

    public static PNWUser getUser(DBNation nation) {
        return  Locutus.imp().getDiscordDB().getUserFromNationId(nation.getNation_id());
    }

    public static DBNation parseNation(String arg, boolean throwError) {
        return parseNation(arg, false, throwError);
    }

    public static DBNation parseNation(String arg, boolean allowDeleted, boolean throwError) {
        return parseNation(arg, allowDeleted, false, throwError);
    }

    public static DBNation parseNation(String arg, boolean allowDeleted, boolean useLeader, boolean throwError) {
        return parseNation(Locutus.imp().getNationDB(), arg, allowDeleted, useLeader, throwError);
    }

    public static DBNation parseNation(INationSnapshot snapshot, String arg, boolean allowDeleted, boolean useLeader, boolean throwError) {
        String argLower = arg.toLowerCase();
        if (argLower.contains("/alliance/") || argLower.startsWith("aa:") || argLower.startsWith("alliance:")) return null;
        if (arg.startsWith("leader:")) {
            arg = arg.substring(7);
            useLeader = true;
        }
        if (useLeader) {
            DBNation nation = snapshot.getNationByLeader(arg);
            if (nation != null) return nation;
        }
        Integer id = parseNationId(snapshot, arg, throwError);
        if (id != null) {
            DBNation nation = snapshot.getNationById(id);
            if (nation == null) {
                if (allowDeleted) {
                    nation = new SimpleDBNation(new DBNationData());
                    nation.setNation_id(id);
                }
                if (throwError) {
                    throw new IllegalArgumentException("Nation not found by id: `" + id + "` (did they delete?)");
                }
            }
            return nation;
        }
        return null;
    }


    public static String toDiscordChannelString(String name) {
        name = name.toLowerCase().replace("-", " ").replaceAll("[ ]+", " ").replaceAll("[^a-z0-9 ]", "");
        name = name.replaceAll(" +", " ");
        name = name.trim();
        name = name.replaceAll(" ", "-");
        return name;
    }

    public static Integer parseNationId(String arg, boolean throwError) {
        return parseNationId(Locutus.imp().getNationDB(), arg, throwError);
    }

    public static Integer parseNationIdUser(INationSnapshot snapshot, String arg, boolean throwError) {
        String argNoBrackets = arg.substring(1, arg.length() - 1);
        if (argNoBrackets.charAt(0) == '@') {
            String idStr = argNoBrackets.substring(1);
            if (idStr.charAt(0) == '!') idStr = idStr.substring(1);
            if (MathMan.isInteger(idStr)) {
                long discordId = Long.parseLong(idStr);
                PNWUser dbUser = Locutus.imp().getDiscordDB().getUserFromDiscordId(discordId);
                if (dbUser != null) {
                    return dbUser.getNationId();
                }

                if (throwError) {
                    User user = Locutus.imp().getDiscordApi().getUserById(discordId);
                    if (user != null) {
                        throw new IllegalArgumentException("User: `" + user + "` is not registered to a nation. See: " + CM.register.cmd.toSlashMention());
                    }
                    throw new IllegalArgumentException("No registered user found by id: `" + discordId + "` (are you sure they are registered?)");
                }

            }
        }
        if (MathMan.isInteger(argNoBrackets)) {
            long id = Long.parseLong(argNoBrackets);
            DBNation nation;
            if (id > Integer.MAX_VALUE) {
                nation = snapshot.getNationByUser(id);
            } else {
                nation = snapshot.getNationById((int) id);
            }
            if (nation != null) {
                return (int) nation.getId();
            }
            if (throwError) {
                if (id > Integer.MAX_VALUE) {
                    User user = Locutus.imp().getDiscordApi().getUserById(id);
                    if (user != null) {
                        throw new IllegalArgumentException("User: `" + user + "` is not registered to a nation. See: " + CM.register.cmd.toSlashMention());
                    }
                    throw new IllegalArgumentException("No registered user found by id: `" + id + "` (are you sure they are registered?)");
                } else {
                    throw new IllegalArgumentException("No registered nation found by id: `" + id + "` (did they delete?. See also `" + CM.admin.sync.syncNations.cmd.nations(argNoBrackets) + "`)");
                }
            }
        }
        if (throwError) {
            throw new IllegalArgumentException("Invalid user syntax: `" + arg + "`");
        }
        return null;
    }

    public static Integer parseNationId(INationSnapshot snapshot, String arg, boolean throwError) {
        if (arg.isEmpty()) return null;
        if (arg.charAt(0) == '"' && arg.charAt(arg.length() - 1) == '"') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            return parseNationIdUser(snapshot, arg, throwError);
        }
        boolean checkUser = true;
        if (arg.toLowerCase().startsWith("nation:")) {
            arg = arg.substring(7);
            checkUser = false;
        }
        if (arg.toLowerCase().startsWith("leader:")) {
            arg = arg.substring(7);
            DBNation nation = snapshot.getNationByLeader(arg);
            if (nation != null) return nation.getId();
            if (MathMan.isInteger(arg)) {
                Long id = Long.parseLong(arg);
                nation = snapshot.getNationById(id.intValue());
            }
            if (throwError) {
                throw new IllegalArgumentException("No registered nation found by leader: `" + arg + "`");
            }
            return null;
        }
        if (arg.contains("/nation/id=") || arg.contains("politicsandwar.com/nation/war/declare/id=") || arg.contains("politicsandwar.com/nation/espionage/eid=")) {
            String[] split = arg.split("=");
            if (split.length == 2) {
                arg = split[1].replaceAll("/", "");
            }
            if (MathMan.isInteger(arg)) {
                return Integer.parseInt(arg);
            }
            if (throwError) {
                throw new IllegalArgumentException("Invalid nation id: `" + arg + "`");
            }
            return null;
        }
        if (arg.charAt(0) == '@') {
            String idStr = arg.substring(1);
            if (idStr.charAt(0) == '!') idStr = idStr.substring(1);
            if (MathMan.isInteger(idStr)) {
                long discordId = Long.parseLong(idStr);
                PNWUser dbUser = Locutus.imp().getDiscordDB().getUserFromDiscordId(discordId);
                if (dbUser != null) {
                    return dbUser.getNationId();
                }
                if (throwError) {
                    User user = Locutus.imp().getDiscordApi().getUserById(discordId);
                    if (user != null) {
                        throw new IllegalArgumentException("User: `" + user + "` is not registered to a nation. See: " + CM.register.cmd.toSlashMention());
                    }
                    throw new IllegalArgumentException("No registered user found by id: `" + discordId + "` (are you sure they are registered?)");
                }
            }
            PNWUser dbUser = Locutus.imp().getDiscordDB().getUser(null, arg, arg);
            if (dbUser != null) {
                return dbUser.getNationId();
            }
            List<User> discUsers = Locutus.imp().getDiscordApi().getUsersByName(arg, true);
            if (discUsers != null && !discUsers.isEmpty()) {
                User user = discUsers.get(0);
                DBNation nation = snapshot.getNationByUser(user);
                if (nation != null) return nation.getId();
                if (throwError) {
                    throw new IllegalArgumentException("User: `" + user + "` is not registered to a nation. See: " + CM.register.cmd.toSlashMention());
                }
            }
            return null;
        }
        if (MathMan.isInteger(arg)) {
            long id = Long.parseLong(arg);
            DBNation nation;
            if (id > Integer.MAX_VALUE) {
                nation = snapshot.getNationByUser(id);
            } else {
                nation = snapshot.getNationById((int) id);
            }
            if (nation != null) {
                return (int) nation.getId();
            }
            if (throwError) {
                if (id > Integer.MAX_VALUE) {
                    User user = Locutus.imp().getDiscordApi().getUserById(id);
                    if (user != null) {
                        throw new IllegalArgumentException("User: `" + user + "` is not registered to a nation. See: " + CM.register.cmd.toSlashMention());
                    }
                    throw new IllegalArgumentException("No registered user found by id: `" + id + "` (are you sure they are registered?)");
                } else {
                    throw new IllegalArgumentException("No registered nation found by id: `" + id + "` (did they delete?. See also `" + CM.admin.sync.syncNations.cmd.nations(arg) + "`)");
                }
            }
            return null;
        }
        if (arg.startsWith("=")) {
            if (arg.contains("=HYPERLINK") && arg.contains("nation/id=")) {
                String regex = "nation/id=([0-9]+)";
                Matcher m = Pattern.compile(regex).matcher(arg);
                m.find();
                arg = m.group(1);
                return Integer.parseInt(arg);
            }
            if (throwError) {
                throw new IllegalArgumentException("Invalid formula: `" + arg + "`");
            }
            return null;
        }
        DBNation nation = snapshot.getNationByNameOrLeader(arg);
        if (nation != null) {
            return nation.getNation_id();
        }
        PNWUser user = Locutus.imp().getDiscordDB().getUser(null, arg, arg);
        if (user != null) {
            return user.getNationId();
        }
        if (checkUser && arg.matches("([a-zA-Z0-9_.]{2,32})")) {
            List<User> discordUsers = Locutus.imp().getDiscordApi().getUsersByName(arg, true);
            if (discordUsers != null && !discordUsers.isEmpty()) {
                nation = snapshot.getNationByUser(discordUsers.get(0));
                if (nation != null) return nation.getId();
            }
            if (throwError) {
                throw new IllegalArgumentException("No registered nation or discord user: `" + arg + "`");
            }
        }
        if (throwError) {
            throw new IllegalArgumentException("Invalid nation syntax: `" + arg + "`");
        }
        return null;
    }

    public static Long parseUserId(Guild guild, String arg) {
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '@') {
            arg = arg.substring(1);
        }
        if (arg.charAt(0) == '!') {
            arg = arg.substring(1);
        }
        if (MathMan.isInteger(arg)) {
            return Long.parseLong(arg);
        }
        if (arg.contains("#")) {
            String[] split = arg.split("#");
            if (split.length != 2) {
                return null;
            }
            User user = Locutus.imp().getDiscordApi().getUserByTag(split[0], split[1]);
            return user != null ? user.getIdLong() : null;
        }
        User user = Locutus.imp().getDiscordApi().getUserByTag(arg, "");
        if (user != null) {
            return user.getIdLong();
        }
        DBNation nation = parseNation(arg, false);
        if (nation != null) {
            return nation.getUserId();
        }
        return null;
    }

    public static User getUser(String arg) {
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '@') {
            arg = arg.substring(1);
        }
        if (arg.charAt(0) == '!') {
            arg = arg.substring(1);
        }
        if (MathMan.isInteger(arg)) {
            return Locutus.imp().getDiscordApi().getUserById(Long.parseLong(arg));
        }
        DBNation nation = parseNation(arg, false);
        if (nation != null) {
            return nation.getUser();
        }
        return null;
    }

//    public static List<String> wrap(String input, int maxSize) {
//        // Markdown based breakpoints
//        // 1st priority: Avoid splitting inside brackets, quotes, or code blocks unless not possible to avoid
//        // - quotes should be assumed ending within the line, so errant quotes aren't a problem
//        // - Code blocks are multi line and use ```
//        // - If forced to split within a code bloc, add ``` to the new sequence
//        // - Ignore quotes that don't have a match (you can split them)
//
//        // 2nd Priority Avoid breaking mid line unless not possible to avoid
//        // If breaking within the line, try to break at a space, comma, or hyphen (in that order)
//        // - Avoid breaking with code blocks, quotes, or brackets unless not possible
//        // - Ignore quotes that don't have a match (you can split them)
//
//        // Relevant methods:
//        // - StringMan.isQuote(char c): boolean
//        // - StringMan.isBracketForwards(char c): boolean
//        // - StringMan.getMatchingBracket(char c): char (returns the opposite bracket character)
//        // - StringMan.findMatchingBracket(CharSequence sequence, int index): int (the index)
//    }

//    public static void main(String[] args) {
//        {//         Simple test with spaces
//            System.out.println("\n\n------ split by word ------\n\n");
//            String input1 = "Hello world an other word";
//            int maxSize = 5;
//            List<String> result1 = DiscordUtil.wrap(input1, maxSize, 0);
//            for (String line : result1) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize,
//                        () -> "Line exceeds max size: " + line);
//            }
//        }
//
//        { // ensure it splits within a word when a word exceeds maxSize
//            System.out.println("\n\n------ split long word but not other words ------\n\n");
//            String input = "Hello thisisareallylongword world an other word";
//            int maxSize = 5;
//            List<String> result = DiscordUtil.wrap(input, maxSize, 0);
//            for (String line : result) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize,
//                        () -> "Line exceeds max size: " + line);
//            }
//
//        }
//
//        { // Ensure it splits on newline when safe
//            System.out.println("\n\n------ split by newline ------\n\n");
//            String input = "Hello world\nan other word";
//            int maxSize = 15;
//            List<String> result = DiscordUtil.wrap(input, maxSize, 0);
//            for (String line : result) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize,
//                        () -> "Line exceeds max size: " + line);
//            }
//        }
//
//        { // ensure it breaks within a word when a word exceeds maxSize
//            System.out.println("\n\n------ split long text without spaces ------\n\n");
//            String input2 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
//            int maxSize2 = 4;
//            List<String> result2 = DiscordUtil.wrap(input2, maxSize2, 0);
//            for (String line : result2) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize2,
//                        () -> "Should force break without spaces" + line);
//            }
//        }
//
//        {// Code block test
//            System.out.println("\n\n------ split outside code block ------\n\n");
//            String input3 = "before code block\n```\ncodeblock\n```\nsome text afterwards";
//            int maxSize3 = 20;
//            List<String> result3 = DiscordUtil.wrap(input3, maxSize3, 0);
//            for (String line : result3) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize3,
//                        () -> "Line in code block test exceeds " + maxSize3);
//            }
//        }
//
//        {// Quote test
//            System.out.println("\n\n------ split outside quoted text ------\n\n");
//            String input4 = "Adklsaj dsa dasdsads asd \"qu o-td s-d\" section with a small-break test testing.";
//            int maxSize4 = 15;
//            List<String> result4 = DiscordUtil.wrap(input4, maxSize4, 0);
//            for (String line : result4) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize4,
//                        () -> "Line in quote test exceeds " + maxSize4);
//            }
//        }
//
//        // Ensure it splits mid quote when quoted text exceeds size
//        {
//            System.out.println("\n\n------ quote small break ------\n\n");
//            String input5 = "A dsa das asd asd asd \"quo dsa sad sa ted\" section with a small-break test.";
//            int maxSize5 = 12;
//            List<String> result5 = DiscordUtil.wrap(input5, maxSize5, 0);
//            for (String line : result5) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize5,
//                        () -> "Line in quote test exceeds " + maxSize5);
//            }
//        }
//
//        {// Bracket test
//            System.out.println("\n\n------ split bracketed text ------\n\n");
//            String input6 = "aaaaaaa[bracket] [bracket] [bracket] [bracket][bracket]s[bracket]dd[bracket]ddd[bracket]dddd[bracket]ddddd[bracket] section with a small-break test.";
//            int maxSize6 = 10;
//            List<String> result6 = DiscordUtil.wrap(input6, maxSize6, 0);
//            for (String line : result6) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize6,
//                        () -> "Line in bracket test exceeds " + maxSize6);
//            }
//        }
//        {// All spaces
//            System.out.println("\n\n------ split by space ------\n\n");
//            String input1 = "                                                                                                                                                                                ";
//            int maxSize = 5;
//            List<String> result1 = DiscordUtil.wrap(input1, maxSize, 0);
//            for (String line : result1) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize,
//                        () -> "Line exceeds max size: " + line);
//            }
//        }
//        {// All newline
//            System.out.println("\n\n------ split by space ------\n\n");
//            String input1 = "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n";
//            int maxSize = 5;
//            List<String> result1 = DiscordUtil.wrap(input1, maxSize, 0);
//            for (String line : result1) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize,
//                        () -> "Line exceeds max size: " + line);
//            }
//        }
//        {// All tick
//            System.out.println("\n\n------ split by tick ------\n\n");
//            String input1 = "````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````````";
//            int maxSize = 15;
//            List<String> result1 = DiscordUtil.wrap(input1, maxSize, 0);
//            for (String line : result1) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize,
//                        () -> "Line exceeds max size: " + line);
//            }
//        }
//        {// All tick
//            System.out.println("\n\n------ tick newline ------\n\n");
//            String input1 = "```\n```\n```\n``````\n``````\n```\n\n```\n```\n\n```\n`````````````\n```\n\n\n```\n``````\n```\n```\n`````````\n`````````\n```\n\n```\n\n``````\n``````\n\n``````";
//            int maxSize = 15;
//            List<String> result1 = DiscordUtil.wrap(input1, maxSize, 0);
//            for (String line : result1) {
//                System.out.println("Line {" + line + "}");
//                assertTrue(line.length() <= maxSize,
//                        () -> "Line exceeds max size: " + line);
//            }
//        }
//    }

    public static List<String> wrap(String input, int maxSize, int minSize) {
        if (input.length() <= maxSize) {
            return List.of(input);
        }
        List<String> lines = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            return lines;
        }

        int i = 0;
        int startCodeBlock = -1;
        int endCodeBlock = -1;
        int quoteI = -1;
        int bracketOpenI = -1;

        int lastI = 0;

        int foundSplitI = -1;
        int foundSplitPriority = -1;

        while (i < input.length()) {
            char c = input.charAt(i);
            boolean forceSplit = false;
            int size = i - lastI;

            if (i - lastI <= maxSize) {
                switch (c) {
                    case '`':
                        if (i + 2 < input.length()
                                && input.charAt(i + 1) == '`'
                                && input.charAt(i + 2) == '`') {
                            if (startCodeBlock == -1) {
                                startCodeBlock = i;
                                endCodeBlock = input.indexOf("```", i + 3);
                                if (endCodeBlock != -1) {
                                    int codeBlockSize = endCodeBlock - i + (startCodeBlock < lastI ? 6 : 3);
                                    if (codeBlockSize <= maxSize) {
                                        foundSplitPriority = 16;
                                        if (codeBlockSize + size > maxSize) {
                                            forceSplit = true;
                                            foundSplitI = i;
                                        } else {
                                            i = endCodeBlock;
                                            foundSplitI = endCodeBlock;
                                            continue;
                                        }
                                    } else {
                                        if (size + 3 > maxSize) {
                                            forceSplit = true;
                                        } else {
                                            i += 3;
                                            continue;
                                        }
                                    }
                                } else {
                                    startCodeBlock = -1;
                                }
                            } else {
                                startCodeBlock = -1;
                                endCodeBlock = -1;
                                if (size + (startCodeBlock < lastI ? 6 : 3) > maxSize) {
                                    forceSplit = true;
                                } else {
                                    i += 3;
                                    continue;
                                }
                            }
                        }
                        break;
                    case '\n': {
                        bracketOpenI = -1;
                        quoteI = -1;
                        int priority = 15;
                        if (size >= minSize && priority >= foundSplitPriority) {
                            foundSplitI = i;
                            foundSplitPriority = priority;
                        }
                        break;
                    }
                    case ' ': {
                        if (size >= minSize) {
                            int priority = 3;
                            if (quoteI != -1) priority += 3;
                            if (bracketOpenI != -1) priority += 6;
                            if (priority >= foundSplitPriority) {
                                foundSplitI = i;
                                foundSplitPriority = priority;
                            }
                        }
                        break;
                    }
                    case ';':
                    case ':': {
                        if (size >= minSize) {
                            int priority = 2;
                            if (quoteI != -1) priority += 3;
                            if (bracketOpenI != -1) priority += 6;
                            if (priority >= foundSplitPriority) {
                                foundSplitI = i;
                                foundSplitPriority = priority;
                            }
                        }
                        break;
                    }
                    case ',':
                        if (size >= minSize) {
                            int priority = 1;
                            if (quoteI != -1) priority += 3;
                            if (bracketOpenI != -1) priority += 6;
                            if (priority >= foundSplitPriority) {
                                foundSplitI = i;
                                foundSplitPriority = priority;
                            }
                        }
                        break;
                    default:
                        if (StringMan.isQuote(c)) {
                            if (quoteI == -1) {
                                quoteI = i;
                                foundSplitI = i;
                                foundSplitPriority = 13;
                            } else {
                                quoteI = -1;
                                if (size < maxSize && size >= minSize) {
                                    foundSplitI = i + 1;
                                    foundSplitPriority = 13;
                                }
                            }
                        } else if (bracketOpenI == -1 && StringMan.isBracketForwards(c)) {
                            bracketOpenI = i;
                            foundSplitI = i;
                            foundSplitPriority = 14;
                        } else if (bracketOpenI != -1 && StringMan.isBracketEnding(c)) {
                            bracketOpenI = -1;
                            if (size < maxSize && size >= minSize) {
                                foundSplitI = i + 1;
                                foundSplitPriority = 14;
                            }
                        }
                }
            }

            // Should determine priority based on the above
            if (i - lastI > maxSize || forceSplit) {
                boolean prependCodeBlock = false;
                if (startCodeBlock != -1 && startCodeBlock < lastI) {
                    prependCodeBlock = true;
                }

                int splitAt = foundSplitI > 0 ? foundSplitI : i - 1;
                int splitAtFinal = splitAt;
                char c1 = input.length() > lastI ? input.charAt(lastI + 1) : '_';
                char c2 = splitAt > 0 ? input.charAt(splitAt - 1) : '_';
                if (c1 == ' ' || c1 == '\n') {
                    lastI++;
                }
                if (c2 == ' ' || c2 == '\n') {
                    splitAtFinal--;
                }

                StringBuilder sequence = new StringBuilder();
                if (prependCodeBlock) {
                    sequence.append("```");
                }
                sequence.append(input, lastI, splitAtFinal);
                if (startCodeBlock != -1 && startCodeBlock != i) {
                    sequence.append("```");
                }
                if (!sequence.isEmpty()) {
                    lines.add(sequence.toString());
                }
                lastI = splitAt;
                foundSplitI = -1;
                foundSplitPriority = -1;
                if (input.length() > lastI && (input.charAt(lastI) == ' ' || input.charAt(lastI) == '\n')) {
                    lastI++;
                    i++;
                }
            }
            i++;
        }
        if (lastI < input.length()) {
            String remaining = input.substring(lastI).trim();
            if (remaining.length() > 0) {
                if (startCodeBlock != -1 && startCodeBlock < lastI) {
                    remaining = "```" + remaining;
                }
                lines.add(remaining);
            }
        }
        return lines;
    }

    // This method tries to break at a space, comma, or hyphen
    private static int findSafeBreak(CharSequence seq) {
        // First try newline
        for (int i = seq.length() - 1; i >= 0; i--) {
            if (seq.charAt(i) == '\n') {
                return i;
            }
        }
        // Then space
        for (int i = seq.length() - 1; i >= 0; i--) {
            if (seq.charAt(i) == ' ') {
                return i;
            }
        }
        // Then comma or hyphen (keep punctuation)
        for (int i = seq.length() - 1; i >= 0; i--) {
            char c = seq.charAt(i);
            if (c == ',' || c == '-') {
                return i + 1;
            }
        }
        return -1;
    }

    public static CompletableFuture<Message> sendMessage(InteractionHook hook, String message) {
        if (message.length() > 20000) {
            if (message.length() < 2000000) {
                return RateLimitUtil.queue(hook.sendFiles(FileUpload.fromData(message.getBytes(StandardCharsets.ISO_8859_1), "message.txt")));
            }
            new Exception().printStackTrace();
            throw new IllegalArgumentException("Cannot send message of this length: " + message.length());
        }
        if (message.contains("@everyone")) {
            message = message.replace("@everyone", "");
        }
        if (message.contains("@here")) {
            message = message.replace("@here", "");
        }
        List<String> lines = DiscordUtil.wrap(message, Message.MAX_CONTENT_LENGTH, 500);
        CompletableFuture<Message> last = null;
        for (String line : lines) {
            if (last == null) {
                last = RateLimitUtil.queue(hook.sendMessage(line));
            } else {
                last = last.thenCompose(previousMessage -> RateLimitUtil.queue(hook.sendMessage(line)));
            }
        }
        return last;
    }

    public static CompletableFuture<List<Message>> sendMessage(MessageChannel channel, String message) {
        if (message.length() > 20000) {
            if (message.length() < 200000) {
                return DiscordUtil.upload(channel, "message.txt", message).thenApply(List::of);
            }
            new Exception().printStackTrace();
            throw new IllegalArgumentException("Cannot send message of this length: " + message.length());
        }
        if (message.contains("@everyone")) {
            message = message.replace("@everyone", "");
        }
        if (message.contains("@here")) {
            message = message.replace("@here", "");
        }
        List<String> lines = DiscordUtil.wrap(message, Message.MAX_CONTENT_LENGTH, 500);
        // If the message fits into one message, send it directly.
        if (lines.size() == 1) {
            return RateLimitUtil.queue(channel.sendMessage(message)).thenApply(List::of);
        }

        // Otherwise, send each line sequentially and collect the results.
        List<Message> sentMessages = new ArrayList<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (String line : lines) {
            chain = chain.thenCompose(ignored ->
                    RateLimitUtil.queue(channel.sendMessage(line))
                            .thenAccept(sentMessages::add)
            );
        }

        return chain.thenApply(ignored -> sentMessages);
    }

    public static void withWebhook(String name, String url, WebhookEmbed embed) {
        WebhookClientBuilder builder = new WebhookClientBuilder(url); // or id, token
        builder.setThreadFactory((job) -> {
            Thread thread = new Thread(job);
            thread.setName(name);
            thread.setDaemon(true);
            return thread;
        });
        builder.setWait(true);
        try (WebhookClient client = builder.build()) {
            client.send(embed);
        }
    }
//
//    public static Set<DBNation> parseNations(Guild guild, String aa, boolean noApplicants, boolean starIsSelf, boolean ignoreErrors) {
//        return parseNations(guild, aa, noApplicants, starIsSelf, ignoreErrors, false);
//    }

    public static Set<DBNation> parseNations(Guild guild, User user, DBNation nation, String input, boolean ignoreErrors, boolean allowDeleted) {
        long start = System.currentTimeMillis();
        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        LocalValueStore store = placeholders.createLocals(guild, user, nation);
        Set<DBNation> set = placeholders.parseSet(store, input, new NationModifier(null, allowDeleted, false));
        if (set.isEmpty() && !ignoreErrors) {
            throw new IllegalArgumentException("No nations found for input: `" + input + "`");
        }
        return set;
    }

    public static Set<DBNation> filter(Set<DBNation> nations, long inactiveTurns, double inactivePct) {
        int finalInactiveTurns = (int) inactiveTurns;
        double activeProbability = 1 - (inactivePct / 100);
        nations.removeIf(nation -> {
            if (nation.getVm_turns() != 0 || nation.isBeige()) return true;
            if (nation.active_m() < 10000) {
                int twoWeeks = 14 * 12;
                long turnNow = TimeUtil.getTurn();
                Activity activity = new Activity(nation.getNation_id(), turnNow - twoWeeks, Long.MAX_VALUE);
                double chance = activity.loginChance(finalInactiveTurns, true);
                return chance > activeProbability;
            }
            return false;
        });
        return nations;
    }

    public static Set<Integer> parseAllianceIds(Guild guild, String aa) {
        return parseAllianceIds(guild, aa, true);
    }

    public static Set<Integer> parseAllianceIds(Guild guild, String aa, boolean allowCoalitions) {
        Set<Integer> aaIds = new IntOpenHashSet();
        for (String aaName : aa.split(",")) {
            aaName = aaName.trim();
            Integer aaId = null;

            if (aaName.startsWith("~") && allowCoalitions) {
                aaName = aaName.substring(1);
            } else if (aaName.startsWith("coalition:") && allowCoalitions) {
                aaName = aaName.substring(10);
            } else {
                aaId = PW.parseAllianceId(aaName);
            }

            if (aaId == null) {
                if (allowCoalitions && guild != null) {
                    Set<Integer> coa = Locutus.imp().getGuildDB(guild).getCoalition(aaName);
                    if (coa.isEmpty()) {
                        GuildDB locutusStats = Locutus.imp().getGuildDB(Settings.INSTANCE.ROOT_COALITION_SERVER);
                        if (locutusStats != null) {
                            coa = locutusStats.getCoalition(aaName);
                        }
                        if (coa.isEmpty()) {
                            return null;
                        }
                    }
                    aaIds.addAll(coa);
                } else {
                    return null;
                }
            } else {
                aaIds.add(aaId);
            }
        }
        return aaIds;
    }

    public static Category getCategory(Guild guild, String categoryStr) {
        if (categoryStr.startsWith("<") && categoryStr.endsWith(">")) {
            categoryStr = categoryStr.substring(1, categoryStr.length() - 1);
        }
        if (categoryStr.charAt(0) == '#') categoryStr = categoryStr.substring(1);
        if (MathMan.isInteger(categoryStr)) {
            Category category = guild.getCategoryById(Long.parseLong(categoryStr));
            if (category != null) return category;

            if (guild != null) {
                GuildDB db = Locutus.imp().getGuildDB(guild);
                GuildDB faServer = db.getOrNull(GuildKey.FA_SERVER);
                Guild maServer = db.getOrNull(GuildKey.WAR_SERVER);
                Set<Guild> guilds = new HashSet<>();
                if (faServer != null) {
                    guilds.add(faServer.getGuild());
                }
                if (maServer != null) {
                    guilds.add(maServer);
                }
                for (GuildDB otherDB : Locutus.imp().getGuildDatabases().values()) {
                    Map.Entry<Integer, Long> delegate = otherDB.getOrNull(GuildKey.DELEGATE_SERVER);
                    if (delegate != null && delegate.getValue() == guild.getIdLong()) {
                        guilds.add(otherDB.getGuild());
                    }
                }
                for (Guild other : guilds) {
                    if (other == guild) continue;
                    category = other.getCategoryById(Long.parseLong(categoryStr));
                    if (category != null) return category;
                }
            }
        }
        List<Category> categories = guild.getCategoriesByName(categoryStr, true);
        if (categories.size() == 1) {
            return categories.get(0);
        }
        return null;
    }

    public static void pending(IMessageIO output, JSONObject command, String title, String desc) {
        pending(output, command, title, desc, "force");
    }
    public static void pending(IMessageIO output, JSONObject command, String title, String desc, String forceFlag) {
        String forceCmd = command.put(forceFlag, "true").toString();
        output.create()
                .embed("Confirm: " + title, desc)
                .commandButton(CommandBehavior.DELETE_MESSAGE, forceCmd, "Confirm")
                .send();
    }

    public static void pending(MessageChannel channel, Message message, String title, String desc, char f) {
        pending(channel, message, title, desc, f, "");
    }

    public static void pending(MessageChannel channel, Message message, String title, String desc, char f, String cmdAppend) {
        String cmd = DiscordUtil.trimContent(message.getContentRaw()) + " -" + f + cmdAppend;
        DiscordUtil.createEmbedCommand(channel, "Confirm: " + title, desc, "Confirm", cmd);
    }

    public static String getChannelUrl(GuildMessageChannel channel) {
        return "https://discord.com/channels/" + channel.getGuild().getIdLong() + "/" + channel.getIdLong();
    }

    public static String cityRangeToString(Map.Entry<Integer, Integer> range) {
        if (range.getValue() == Integer.MAX_VALUE) {
            return "c" + range.getKey() + "+";
        }
        return "c" + range.getKey() + "-" + range.getValue();
    }

    public static Category findFreeCategory(Collection<Category> categories) {
        for (Category cat : categories) {
            if (cat.getChannels().size() < 50) return cat;
        }
        return null;
    }

    public static User getUser(long userId) {
        return Locutus.imp().getDiscordApi().getUserById(userId);
    }

    public static User getUserByNationId(int nationId) {
        DBNation nation = DBNation.getById(nationId);
        return nation != null ? nation.getUser() : null;
    }

    public static long getUserIdByNationId(int nationId) {
        PNWUser pwUser = Locutus.imp().getDiscordDB().getUserFromNationId(nationId);
        if (pwUser != null) return pwUser.getDiscordId();
        User user = getUserByNationId(nationId);
        if (user != null) return user.getIdLong();
        return 0;
    }

    public static String getFullUsername(User user) {
        return user.getName() + getDiscriminator(user);
    }

    public static String getDiscriminator(User user) {
        if (user instanceof UserImpl impl) {
            short number = impl.getDiscriminatorInt();
            if (number > 999) {
                return "#" + number;
            } else if (number > 99) {
                return "#0" + number;
            } else if (number > 9) {
                return "#00" + number;
            } else if (number > 0) {
                return "#000" + number;
            } else {
                return "";
            }
        }
        String discriminator = user.getDiscriminator();
        if (discriminator.length() != 4 || discriminator.equals("0000")) {
            return "";
        }
        return "#" + discriminator;
    }

    public static long getMessageGuild(String url) {
        int index = url.indexOf("channels/");
        if (index == -1) return 0;
        String[] split = url.substring(index + 9).split("/");
        return Long.parseLong(split[0]);
    }

    public static void threadDump() {
        ThreadPoolExecutor executor = Locutus.imp().getExecutor();
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread thread : threadSet) {
            System.err.println(Arrays.toString(thread.getStackTrace()));
        }
        System.err.print("\n\nQueue: " + executor.getQueue().size() + " | Active: " + executor.getActiveCount() + " | task count: " + executor.getTaskCount());
        executor.submit(() -> System.err.println("- COMMAND EXECUTOR RAN SUCCESSFULLY!!!"));
    }
}
