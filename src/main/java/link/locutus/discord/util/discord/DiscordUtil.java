package link.locutus.discord.util.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.sheet.SpreadSheet;
import com.google.common.base.Charsets;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.entities.UserImpl;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONObject;
import org.jsoup.internal.StringUtil;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static link.locutus.discord.util.MathMan.parseFilter;
import static link.locutus.discord.util.MathMan.parseStringFilter;

public class DiscordUtil {
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
            System.out.println("Results are empty");
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
            JSONObject json = new JSONObject(command);
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

        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(title);
        builder.setDescription(body.toString());
        if (footer != null) builder.setFooter(footer);

        message.clearEmbeds();
        message.embed(builder.build());
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
        if (message.length() > 2000 && reactionArguments.length == 0) {
            if (title.length() >= 1024) title = title.substring(0, 1020) + "...";
            DiscordUtil.upload(channel, title, message);
            return;
        }
        String finalTitle = title;
        createEmbedCommand(channel, new Consumer<EmbedBuilder>() {
            @Override
            public void accept(EmbedBuilder builder) {
                String titleFinal = finalTitle;
                if (titleFinal.length() >= 200) {
                    titleFinal = titleFinal.substring(0, 197) + "..";
                }
                builder.setTitle(titleFinal);
                builder.setDescription(message);
                if (footer != null) builder.setFooter(footer);
            }
        }, reactionArguments);
    }

    public static void createEmbedCommand(MessageChannel channel, Consumer<EmbedBuilder> builder, String... reactionArguments) {
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


    public static void createEmbedCommand(MessageChannel channel, Consumer<EmbedBuilder> consumer, Map<String, String> reactionArguments) {
        EmbedBuilder builder = new EmbedBuilder();
        consumer.accept(builder);
        MessageEmbed embed = builder.build();

        new DiscordChannelIO(channel, null).create().embed(embed).addCommands(reactionArguments).send();
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
            System.out.println("ID is null");
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

        Long channelId = null;
        if (id.startsWith("<#")) {
            String channelIdStr = id.substring(0, id.indexOf('>') + 1);
            channelId = DiscordUtil.getChannelId(guild, channelIdStr);
            id = id.substring(id.indexOf(' ') + 1);
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
            return List.of(new CommandInfo(channelId, behavior, id));
        } else if (!id.isEmpty()) {
            throw new IllegalArgumentException("Unknown command (5): `" + id + "`");
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
        if (name.charAt(0) != 'c') return null;

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
        return new AbstractMap.SimpleEntry<>(start, end);
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

    public static DBNation parseNation(String arg) {
        return parseNation(arg, false);
    }

    public static DBNation parseNation(String arg, boolean allowDeleted) {
        return parseNation(arg, allowDeleted, false);
    }

    public static DBNation parseNation(String arg, boolean allowDeleted, boolean useLeader) {
        if (arg.toLowerCase().contains("/alliance/") || arg.toLowerCase().startsWith("aa:") || arg.toLowerCase().startsWith("alliance:")) return null;
        if (arg.startsWith("leader:")) {
            arg = arg.substring(7);
            useLeader = true;
        }
        if (useLeader) {
            DBNation nation = Locutus.imp().getNationDB().getNationByLeader(arg);
            if (nation != null) return nation;
        }
        Integer id = parseNationId(arg);
        if (id != null) {
            DBNation nation = Locutus.imp().getNationDB().getNation(id);
            if (nation == null && allowDeleted) {
                nation = new DBNation();
                nation.setNation_id(id);
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

    public static Integer parseNationId(String arg) {
        if (arg.isEmpty()) return null;
        if (arg.charAt(0) == '"' && arg.charAt(arg.length() - 1) == '"') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.toLowerCase().startsWith("nation:")) arg = arg.substring(7);
        if (arg.contains("/nation/id=") || arg.contains("politicsandwar.com/nation/war/declare/id=") || arg.contains("politicsandwar.com/nation/espionage/eid=")) {
            String[] split = arg.split("=");
            if (split.length == 2) {
                arg = split[1].replaceAll("/", "");
            }
        } else if (arg.charAt(0) == '@') {
            String idStr = arg.substring(1);
            if (idStr.charAt(0) == '!') idStr = idStr.substring(1);
            if (MathMan.isInteger(idStr)) {
                long discordId = Long.parseLong(idStr);
                PNWUser user = Locutus.imp().getDiscordDB().getUserFromDiscordId(discordId);
                if (user != null) {
                    return user.getNationId();
                }

            }
        }
        if (MathMan.isInteger(arg)) {
            long id = Long.parseLong(arg);
            DBNation nation;
            if (id > Integer.MAX_VALUE) {
                nation = getNation(id);
            } else {
                nation = DBNation.getById((int) id);
            }
            if (nation != null) {
                return (int) id;
            }
        }
        DBNation nation = Locutus.imp().getNationDB().getNation(arg);
        if (nation != null) {
            return nation.getNation_id();
        }
        PNWUser user = Locutus.imp().getDiscordDB().getUser(null, arg, arg);
        if (user != null) {
            return user.getNationId();
        }
        List<User> discordUsers = Locutus.imp().getDiscordApi().getUsersByName(arg, true);
        User discordUser = !discordUsers.isEmpty() ? discordUsers.get(0) : null;
        if (discordUser != null) {
            nation = DiscordUtil.getNation(discordUser);
            if (nation != null) return nation.getId();
        }
        if (!MathMan.isInteger(arg)) {
            if (arg.contains("=HYPERLINK") && arg.contains("nation/id=")) {
                String regex = "nation/id=([0-9]+)";
                Matcher m = Pattern.compile(regex).matcher(arg);
                m.find();
                arg = m.group(1);
                return Integer.parseInt(arg);
            }
            return null;
        }
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
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
        DBNation nation = parseNation(arg);
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
        DBNation nation = parseNation(arg);
        if (nation != null) {
            return nation.getUser();
        }
        return null;
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
        message = WordUtils.wrap(message, 2000, "\n", true, ",");
        if (message.length() > 2000) {
            String[] lines = message.split("\\r?\\n");
            StringBuilder buffer = new StringBuilder();
            for (String line : lines) {
                if (buffer.length() + 1 + line.length() > 2000) {
                    String str = buffer.toString().trim();
                    if (!str.isEmpty()) {
                        RateLimitUtil.complete(hook.sendMessage(str));
                    }
                    buffer.setLength(0);
                }
                buffer.append('\n').append(line);
            }
            String finalMsg = buffer.toString().trim();
            if (finalMsg.length() != 0) {
                return RateLimitUtil.queue(hook.sendMessage(finalMsg));
            }
        } else if (!message.isEmpty()) {
            return RateLimitUtil.queue(hook.sendMessage(message));
        }
        return null;
    }

    public static CompletableFuture<Message> sendMessage(MessageChannel channel, String message) {
        if (message.length() > 20000) {
            if (message.length() < 200000) {
                return DiscordUtil.upload(channel, "message.txt", message);
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
        message = WordUtils.wrap(message, 2000, "\n", true, ",");
        if (message.length() > 2000) {
            String[] lines = message.split("\\r?\\n");
            StringBuilder buffer = new StringBuilder();
            for (String line : lines) {
                if (buffer.length() + 1 + line.length() > 2000) {
                    String str = buffer.toString().trim();
                    if (!str.isEmpty()) {
                        RateLimitUtil.complete(channel.sendMessage(str));
                    }
                    buffer.setLength(0);
                }
                buffer.append('\n').append(line);
            }
            if (buffer.length() != 0) {
                return RateLimitUtil.queue(channel.sendMessage(buffer));
            }
        } else if (!message.isEmpty()) {
            return RateLimitUtil.queue(channel.sendMessage(message));
        }
        return null;
    }

//    public static Set<DBNation> parseNations(Guild guild, String query) {
//        return parseNations(guild, query, false, false);
//    }
//
//    public static Set<DBNation> parseNations(Guild guild, String aa, boolean noApplicants, boolean starIsSelf) {
//        return parseNations(guild, aa, noApplicants, starIsSelf, false);
//    }

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
        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        LocalValueStore store = placeholders.createLocals(guild, user, nation);
        Set<DBNation> set = placeholders.parseSet(store, input);
        if (!allowDeleted) {
            set.removeIf(f -> !f.isValid());
        }
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
                Activity activity = new Activity(nation.getNation_id(), twoWeeks);
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
        Set<Integer> aaIds = new HashSet<>();
        for (String aaName : aa.split(",")) {
            aaName = aaName.trim();
            Integer aaId = null;

            if (aaName.startsWith("~") && allowCoalitions) {
                aaName = aaName.substring(1);
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

    public static Set<DBNation> getNationsByAA(int alliance_id) {
        return DBAlliance.getOrCreate(alliance_id).getNations();
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
}
