package link.locutus.discord.util.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.parser.ArgParser;
import link.locutus.discord.util.sheet.SpreadSheet;
import com.google.common.base.Charsets;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static link.locutus.discord.util.MathMan.parseFilter;
import static link.locutus.discord.util.MathMan.parseStringFilter;

public class DiscordUtil {
    public static Color BACKGROUND_COLOR = Color.decode("#36393E");

    public static String trimContent(String content) {
        if (content.isEmpty()) return content;
        if (content.charAt(0) == '<') {
            int end = content.indexOf('>');
            if (end != -1) {
                content = content.substring(end + 1).trim();
            }
        }
        return content;
    }

    public static Message upload(MessageChannel channel, String title, String body) {
        if (!title.contains(".")) title += ".txt";
         return RateLimitUtil.complete(channel.sendFile(body.getBytes(StandardCharsets.ISO_8859_1), title));
    }

    public static void sortInterviewChannels(List<? extends GuildMessageChannel> channels, Map<Long, List<InterviewMessage>> messages, Map<? extends GuildMessageChannel, User> interviewUsers) {
        Collections.sort(channels, new Comparator<GuildMessageChannel>() {
            @Override
            public int compare(GuildMessageChannel o1, GuildMessageChannel o2) {
                List<InterviewMessage> m1s = messages.getOrDefault(o1.getIdLong(), Collections.emptyList());
                List<InterviewMessage> m2s = messages.getOrDefault(o2.getIdLong(), Collections.emptyList());

                long created1 = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(o1).toEpochSecond();
                long created2 = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(o2).toEpochSecond();

                long lastMessage1 = o1.hasLatestMessage() ? net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(o1.getLatestMessageIdLong()).toEpochSecond() : created1;
                long lastMessage2 = o2.hasLatestMessage() ? net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(o2.getLatestMessageIdLong()).toEpochSecond() : created2;

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

    public static MessageChannel getChannel(Guild guild, String keyOrLong) {
        if (keyOrLong.charAt(0) == '<' && keyOrLong.charAt(keyOrLong.length() - 1) == '>') {
            keyOrLong = keyOrLong.substring(1, keyOrLong.length() - 1);
        }
        if (keyOrLong.charAt(0) == '#') {
            keyOrLong = keyOrLong.substring(1);
        }
        if (MathMan.isInteger(keyOrLong)) {
            GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(Long.parseLong(keyOrLong));
            if (channel == null) {
//                channel = Locutus.imp().getDiscordApi().getPrivateChannelById(keyOrLong);
//                if (channel == null) {
//                    throw new IllegalArgumentException("Invalid channel " + keyOrLong);
//                }
            } else {
                if (guild != null && channel.getGuild().getIdLong() != guild.getIdLong()) {
                    GuildDB otherDB = Locutus.imp().getGuildDB(channel.getGuild());
                    GuildDB delegate = otherDB.getOrNull(GuildDB.Key.DELEGATE_SERVER);
                    if (delegate == null || delegate.getIdLong() != guild.getIdLong()) {
                        throw new IllegalArgumentException("Channel: " + keyOrLong + " not in " + guild + " (" + guild + ")");
                    }
                }
            }
            return channel;
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
        if (results.isEmpty()) return;

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

        String identifier = command.charAt(0) == Settings.commandPrefix(true).charAt(0) ? "page:" : "-p ";

        String cmdCleared = command.replaceAll(identifier + "[0-9]+", "");
        if (inline) cmdCleared = "~" + cmdCleared;
        if (hasPrev) {
            reactions.put("\u2B05\uFE0F", cmdCleared + " " + identifier + (page-1));
        }
        if (hasNext) {
            reactions.put("\u27A1\uFE0F", cmdCleared + " " + identifier + (page+1));
        }

        if (message != null && message.getAuthor().getIdLong() == Settings.INSTANCE.APPLICATION_ID) {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle(title);
            builder.setDescription(body.toString());
            if (footer != null) builder.setFooter(footer);
            updateEmbed(builder, reactions, b -> updateMessage(channel, message, b.build()));
        } else {
            List<String> reactionsList = new ArrayList<>();
            for (Map.Entry<String, String> entry : reactions.entrySet()) {
                reactionsList.add(entry.getKey());
                reactionsList.add(entry.getValue());
            }
            createEmbedCommandWithFooter(channel, title, body.toString(), footer, reactionsList.toArray(new String[0]));
        }
    }

    public static Message createEmbedCommand(long chanelId, String title, String message, String... reactionArguments) {
        MessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(chanelId);
        if (channel == null) {
//            channel = Locutus.imp().getDiscordApi().getPrivateChannelById(chanelId);
//            if (channel == null) {
                throw new IllegalArgumentException("Invalid channel " + chanelId);
//            }
        }
        return createEmbedCommand(channel, title, message, reactionArguments);
    }

    public static Message createEmbedCommand(MessageChannel channel, String title, String message, String... reactionArguments) {
        return createEmbedCommandWithFooter(channel, title, message, null, reactionArguments);
    }

    public static Message createEmbedCommandWithFooter(MessageChannel channel, String title, String message, String footer, String... reactionArguments) {
        if (message.length() > 2000 && reactionArguments.length == 0) {
            return DiscordUtil.upload(channel, title, message);
        }
        return createEmbedCommand(channel, new Consumer<EmbedBuilder>() {
            @Override
            public void accept(EmbedBuilder builder) {
                String titleFinal = title;
                if (titleFinal.length() >= 200) {
                    titleFinal = titleFinal.substring(0, 197) + "..";
                }
                builder.setTitle(titleFinal);
                String tmp = message;
                if (tmp.length() > 2000) {
                    Message msg = DiscordUtil.upload(channel, titleFinal, message);
                    tmp = "(see attachment)";
                }
                builder.setDescription(tmp);
                if (footer != null) builder.setFooter(footer);
            }
        }, reactionArguments);
    }

    public static Message createEmbedCommand(MessageChannel channel, Consumer<EmbedBuilder> builder, String... reactionArguments) {
        if (reactionArguments.length % 2 != 0) {
            throw new IllegalArgumentException("invalid pairs: " + StringMan.getString(reactionArguments));
        }
        HashMap<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < reactionArguments.length; i+=2) {
            map.put(reactionArguments[i], reactionArguments[i + 1]);
        }
        return createEmbedCommand(channel, builder, map);
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
        GuildMessageChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) return null;
        Message message = RateLimitUtil.complete(channel.retrieveMessageById(messageId));
        return message;
    }


    public static Message createEmbedCommand(MessageChannel channel, Consumer<EmbedBuilder> builder, Map<String, String> reactionArguments) {
        EmbedBuilder embed = new EmbedBuilder();
        builder.accept(embed);
        return updateEmbed(embed, reactionArguments, builder1 -> channel.sendMessageEmbeds(builder1.build()).complete());
    }

    public static Message updateMessage(MessageChannel channel, Message message, MessageEmbed embed) {
        try {
            if (message != null && message.getChannel().getIdLong() == channel.getIdLong()) {
                 return RateLimitUtil.complete(channel.editMessageEmbedsById(message.getIdLong(), embed));
            }
        } catch (ErrorResponseException ignore) {}
         return RateLimitUtil.complete(channel.sendMessageEmbeds(embed));
    }

    public static Message appendDescription(Message message, String append) {
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) return null;
        MessageEmbed embed = embeds.get(0);
        EmbedBuilder builder = new EmbedBuilder(embed);
        builder.setDescription(embed.getDescription() + append);
        return DiscordUtil.updateEmbed(builder, null, new Function<EmbedBuilder, Message>() {
            @Override
            public Message apply(EmbedBuilder builder) {
                 return RateLimitUtil.complete(message.getChannel().editMessageEmbedsById(message.getIdLong(), builder.build()));
            }
        });
    }

    public static Message updateEmbed(EmbedBuilder embed, Map<String, String> reactionArguments, Function<EmbedBuilder, Message> createMsg) {
        if (reactionArguments != null && !reactionArguments.isEmpty()) {
            List<NameValuePair> pairs = reactionArguments.entrySet().stream()
                    .map((Function<Map.Entry<String, String>, NameValuePair>)
                            e -> new BasicNameValuePair(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
            String query = URLEncodedUtils.format(pairs, "UTF-8");

            embed.setThumbnail("https://example.com?" + query);
//            embed.setImage("https://example.com?" + query);
        }
        Message msg = createMsg.apply(embed);

        if (reactionArguments != null && msg != null) {
            List<MessageReaction> existing = msg.getReactions();
            if (!existing.isEmpty()) {
                RateLimitUtil.complete(msg.clearReactions());
            }
            for (String emoji : reactionArguments.keySet()) {
                RateLimitUtil.queue(msg.addReaction(emoji));
            }
        }
        return msg;
    }

    private static final ArgParser parser = new ArgParser();

    public static String format(Guild guild, MessageChannel channel, User user, DBNation nation, String message) {
        if (user != null && message.contains("%user%")) {
            message = message.replace("%user%", user.getAsMention());
        }
        String result = parser.parse(guild, channel, user, nation, message);
        if (result.indexOf('{') != -1 && result.indexOf('}') != -1) {
            for (NationMeta value : NationMeta.values) {
                String arg = "{" + value.name().toLowerCase() + "}";
                if (message.contains(arg)) {
                    message = message.replace(arg, value.toString(nation.getMeta(value)));
                }
            }
        }
        return result;
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

    public static ArgParser getParser() {
        return parser;
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
        end = Math.min(50, end);
        return new AbstractMap.SimpleEntry<>(start, end);
    }

    public static Map<Integer, Set<Role>> getCityRoles(Collection<Role> roles) {
        Map<Integer, Set<Role>> rolesByCity = new HashMap<>();
        for (Role role : roles) {
            String name = role.getName();
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

    public static Map<Integer, Role> getAARoles(Collection<Role> roles) {
        Map<Integer, Role> allianceRoles = null;
        for (Role role : roles) {
            if (role.getName().startsWith("AA ")) {
                String[] split = role.getName().split(" ");
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
            return DBNation.byId(pnwUser.getNationId());
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
        if (arg.toLowerCase().contains("/alliance/") || arg.toLowerCase().startsWith("aa:")) return null;
        Integer id = parseNationId(arg);
        if (id != null) {
            return Locutus.imp().getNationDB().getNation(id);
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
        if (arg.charAt(0) == '"' && arg.charAt(arg.length() - 1) == '"') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.contains("politicsandwar.com/nation/id=") || arg.contains("politicsandwar.com/nation/war/declare/id=")) {
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
        } else {
            if (MathMan.isInteger(arg)) {
                long id = Long.parseLong(arg);
                DBNation nation;
                if (id > Integer.MAX_VALUE) {
                    nation = getNation(id);
                } else {
                    nation = DBNation.byId((int) id);
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
        }
        if (!MathMan.isInteger(arg)) {
            return null;
        }
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
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

    public static void sendMessage(MessageChannel channel, String message) {
        if (message.length() > 20000) {
            if (message.length() < 200000) {
                DiscordUtil.upload(channel, "message.txt", message);
                return;
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
                        channel.sendMessage(str).complete();
                    }
                    buffer.setLength(0);
                }
                buffer.append('\n').append(line);
            }
            if (buffer.length() != 0) {
                channel.sendMessage(buffer).complete();
            }
        } else if (!message.isEmpty()) {
            channel.sendMessage(message).complete();
        }
    }

    public static Guild getDefaultGuild(MessageReceivedEvent event) {
        return event.isFromGuild() ? event.getGuild() : Locutus.imp().getServer();
    }

    public static Set<DBNation> parseNations(Guild guild, String aa) {
        return parseNations(guild, aa, false, false);
    }

    public static Set<DBNation> parseNations(Guild guild, String aa, boolean noApplicants, boolean starIsSelf) {
        return parseNations(guild, aa, noApplicants, starIsSelf, false);
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

    public static Set<DBNation> parseNations(Guild guild, String aa, boolean noApplicants, boolean starIsSelf, boolean ignoreErrors) {
        if (aa.equalsIgnoreCase("*")) return new HashSet<>(Locutus.imp().getNationDB().getNations().values());
        Set<DBNation> orNations = null;

        for (String andGroup : aa.split("\\|\\|")) {
            Set<DBNation> nations = new LinkedHashSet<>();
            List<String> filters = new ArrayList<>();
            for (String name : andGroup.split(",")) {
                if (name.equalsIgnoreCase("*")) {
                    if (starIsSelf) {
                        if (guild == null) {
                            if (ignoreErrors) continue;
                            throw new IllegalArgumentException("Not in guild.");
                        }
                        Integer allianceId = Locutus.imp().getGuildDB(guild).getOrNull(GuildDB.Key.ALLIANCE_ID);
                        if (allianceId != null) {
                            nations.addAll(Locutus.imp().getNationDB().getNations(Collections.singleton(allianceId)));
                        } else {
                            nations.addAll(Locutus.imp().getNationDB().getNations(Locutus.imp().getGuildDB(guild).getCoalition(Coalition.ALLIES)));
                        }
                    } else {
                        nations.addAll(Locutus.imp().getNationDB().getNations().values());
                    }

                    continue;
                } else if (name.contains("tax_id=")) {
                    int taxId = PnwUtil.parseTaxId(name);
                    nations.addAll(Locutus.imp().getNationDB().getNationsMatching(f -> f.getTax_id() == taxId));
                    continue;
                } else if (name.startsWith("https://docs.google.com/spreadsheets/d/") || name.startsWith("sheet:")) {
                    String key;
                    if (name.startsWith("sheet:")) {
                        key = name.split(":")[1];
                    } else {
                        key = name.split("/")[5];
                    }

                    SpreadSheet sheet = null;
                    try {
                        sheet = SpreadSheet.create(key);
                    } catch (GeneralSecurityException | IOException e) {
                        throw new RuntimeException(e);
                    }

                    List<List<Object>> rows = sheet.get("A:Z");
                    if (rows == null || rows.isEmpty()) continue;

                    List<DBNation> toAdd = new ArrayList<>();
                    Integer nationI = 0;
                    List<Object> header = rows.get(0);
                    for (int i = 0; i < header.size(); i++) {
                        if (header.get(i) == null) continue;
                        if (header.get(i).toString().equalsIgnoreCase("nation")) {
                            nationI = i;
                            break;
                        }
                    }
                    long start = System.currentTimeMillis();
                    for (int i = 1; i < rows.size(); i++) {
                        List<Object> row = rows.get(i);
                        if (row.size() <= nationI) continue;

                        Object cell = row.get(nationI);
                        if (cell == null) continue;
                        String nationName = cell + "";
                        if (nationName.isEmpty()) continue;

                        DBNation nation = DiscordUtil.parseNation(nationName);
                        if (nation != null) {
                            toAdd.add(nation);
                        } else {
                            if (ignoreErrors) continue;

                            long diff = System.currentTimeMillis() - start;
                            throw new IllegalArgumentException("Unknown nation: " + nationName + " in " + name);
                        }
                    }
                    long diff = System.currentTimeMillis() - start;

                    if (key.contains("#attackers")) {
                        String[] split = key.split("#attackers[=|:]");
                        Set<DBNation> filterNations = DiscordUtil.parseNations(guild, split[1]);
                        Map<DBNation, Set<DBNation>> targets = BlitzGenerator.getTargets(sheet, 0);

                        toAdd.removeIf(f -> {
                            Set<DBNation> attackers = targets.get(f);
                            if (attackers == null) return true;
                            for (DBNation attacker : attackers) {
                                if (filterNations.contains(attacker)) return true;
                            }
                            return false;
                        });
                    }

                    nations.addAll(toAdd);

                    continue;
                } else if (name.startsWith("#not")) {
                    Set<DBNation> not = parseNations(guild, name.split("=", 2)[1], noApplicants, starIsSelf, ignoreErrors);
                    nations.removeIf(f -> not.contains(f));
                    continue;
                } else if (name.toLowerCase().startsWith("aa:")) {
                    Set<DBNation> allianceMembers = Locutus.imp().getNationDB().getNations(parseAlliances(guild, name.split(":", 2)[1]));
                    if (noApplicants) {
                        allianceMembers.removeIf(n -> n.getPosition() <= 1);
                    }
                    nations.addAll(allianceMembers);
                    continue;
                }
                if (name.length() == 0) continue;
                if (name.charAt(0) == '#') {
                    filters.add(name);
                    continue;
                }

                DBNation nation = name.contains("/alliance/") ? null : parseNation(name);
                if (nation == null || name.contains("/alliance/")) {
                    Set<Integer> alliances = parseAlliances(guild, name);
                    if (alliances == null) {
                        Role role = guild != null ? getRole(guild, name) : null;
                        if (role != null) {
                            List<Member> members = guild.getMembersWithRoles(role);
                            for (Member member : members) {
                                PNWUser user = Locutus.imp().getDiscordDB().getUserFromDiscordId(member.getIdLong());
                                if (user == null) continue;
                                nation = Locutus.imp().getNationDB().getNation(user.getNationId());
                                if (nation != null) nations.add(nation);
                            }

                        } else if (name.contains("#")) {
                            String[] split = name.split("#");
                            PNWUser user = Locutus.imp().getDiscordDB().getUser(null, split[0], name);
                            if (user != null) {
                                nation = Locutus.imp().getNationDB().getNation(user.getNationId());
                            }
                            if (nation == null) {
                                if (ignoreErrors) continue;
                                throw new IllegalArgumentException("Invalid nation/aa: " + name);
                            }
                        } else {
//                        if (acronyms) {
//                            // TODO acronym
//                        }
                            if (ignoreErrors) continue;
                            throw new IllegalArgumentException("Invalid nation/aa: " + name);
                        }
                    } else {
                        Set<DBNation> allianceMembers = Locutus.imp().getNationDB().getNations(alliances);
                        if (noApplicants) {
                            allianceMembers.removeIf(n -> n.getPosition() <= 1);
                        }
                        nations.addAll(allianceMembers);
                    }
                } else {
                    nations.add(nation);
                }
            }
            double inactivePct = 0;
            long inactiveTurns = 0;

            Set<Integer> attIds = null;
            Set<Integer> defIds = null;
            Set<Integer> fightingIds = null;

            int depth = 0;
            Iterator<String> iter = filters.iterator();
            while (iter.hasNext()) {
                String filterArg = iter.next();
                Map.Entry<String, Function<String, Boolean>> strFilter = parseStringFilter(filterArg);
                if (strFilter != null) {
                    switch (strFilter.getKey().toLowerCase()) {
                        case "#enemies": {
                            GuildDB db = Locutus.imp().getGuildDB(guild);

                            Set<Integer> allies;
                            String[] argSplit = filterArg.split("=", 2);
                            if (argSplit.length == 2) {
                                allies = parseAlliances(guild, argSplit[1]);
                            } else {
                                allies = new HashSet<>();
                                Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
                                if (aaId != null) {
                                    allies.add(aaId);
                                    Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(aaId);
                                    treaties.entrySet().removeIf(e -> e.getValue().getType() != TreatyType.PROTECTORATE);

                                    Set<DBNation> aaNations = Locutus.imp().getNationDB().getNations(Collections.singleton(aaId));
                                    double totalScore = aaNations.stream().mapToDouble(DBNation::getScore).sum();

                                    for (Map.Entry<Integer, Treaty> entry : treaties.entrySet()) {
                                        int protId = entry.getKey();
                                        Set<DBNation> protNations = Locutus.imp().getNationDB().getNations(Collections.singleton(protId));
                                        double protScore = aaNations.stream().mapToDouble(DBNation::getScore).sum();
                                        if (protScore < totalScore) {
                                            allies.add(protId);
                                        }
                                    }

                                } else {
                                    allies = db.getAllies();
                                }
                            }
                            nations.addAll(Locutus.imp().getNationDB().getNations(db.getCoalition("enemies")));
                        }
                        case "#sort": {
                            String arg = filterArg.split("=")[1];
                            break;
                        }
                        case "#fighting": {
                            String arg = filterArg.split("=", 2)[1];
                            attIds = parseAlliances(guild, arg);
                            defIds = parseAlliances(guild, arg);
                            break;
                        }
                        case "#attacking": {
                            String arg = filterArg.split("=", 2)[1];
                            attIds = parseAlliances(guild, arg);
                            break;
                        }
                        case "#defending": {
                            String arg = filterArg.split("=", 2)[1];
                            defIds = parseAlliances(guild, arg);
                            break;
                        }
                        case "#spyrange": {
                            double score = Double.parseDouble(filterArg.split("=")[1]);
                            double min = score * 0.4;
                            double max = score * 1.5;
                            nations.removeIf(n -> n.getScore() < min || n.getScore() > max);
                            break;
                        }
                        case "#warrange": {
                            double score = Double.parseDouble(filterArg.split("=")[1]);
                            double min = score * 0.75;
                            double max = score * 1.75;
                            nations.removeIf(n -> n.getScore() < min || n.getScore() > max);
                            break;
                        }
                        case "#inactive":
                            String[] arg = filterArg.split("=");
                            String[] split = arg[1].split("%");
                            if (split.length == 1) split = new String[]{"95", split[0]};
                            inactivePct = MathMan.parseDouble(split[0]);
                            inactiveTurns = TimeUtil.timeToSec(split[1]) / (60 * 60);
                            break;
                        case "#treaty": {
                            Set<Integer> alliances = new HashSet<>();
                            for (DBNation nation : nations) {
                                alliances.add(nation.getAlliance_id());
                            }
                            alliances.remove(0);
                            Set<TreatyType> treatyTypes = new HashSet<>();
                            for (String typeArg : filterArg.split("=")[1].split("[;|]")) {
                                treatyTypes.add(TreatyType.parse(typeArg));
                            }

                            Set<Integer> newAlliances = new HashSet<>(alliances);

                            for (int allianceId : alliances) {
                                Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(allianceId);
                                if (treatyTypes.size() == 1 && treatyTypes.contains(TreatyType.NONE)) {
                                    if (!treaties.isEmpty()) {
                                        nations.removeIf(n -> n.getAlliance_id() == allianceId);
                                    }
                                    continue;
                                }
                                for (Map.Entry<Integer, Treaty> entry : treaties.entrySet()) {
                                    if (newAlliances.contains(entry.getKey())) continue;
                                    Treaty treaty = entry.getValue();
                                    if (treatyTypes.contains(treaty.getType())) {
                                        if (depth == 0 || treaty.getType() != TreatyType.PROTECTORATE || newAlliances.contains(treaty.getFromId())) {
                                            newAlliances.add(entry.getKey());
                                        }
                                    }
                                }
                            }
                            newAlliances.removeAll(alliances);
                            if (!newAlliances.isEmpty()) {
                                Set<DBNation> allianceMembers = Locutus.imp().getNationDB().getNations(newAlliances);
                                if (noApplicants) {
                                    allianceMembers.removeIf(n -> n.getPosition() <= 1);
                                }
                                nations.addAll(allianceMembers);
                            }

                            depth++;
                        }
                        break;
                    }
                }
            }

            if (attIds != null || defIds != null) {

                List<DBWar> wars = Locutus.imp().getWarDb().getActiveWarsByAlliance(defIds, attIds);
                wars.removeIf(w -> !w.isActive());

                Set<Integer> nationIds = new LinkedHashSet<>();
                for (DBWar war : wars) {
                    if (attIds != null) nationIds.add(war.attacker_id);
                    if (defIds != null) nationIds.add(war.defender_id);
                }

                nations.removeIf(f -> !nationIds.contains(f.getNation_id()));
            }

            for (String filterArg : filters) {
                Map.Entry<String, Function<Double, Boolean>> filter = parseFilter(filterArg);
                if (filter == null) {
                    Map.Entry<String, Function<String, Boolean>> strFilter = parseStringFilter(filterArg);
                    if (strFilter == null) {
                        throw new IllegalArgumentException("Invalid filter (1): `" + filterArg + "`");
                    }
                    switch (strFilter.getKey().toLowerCase()) {
                        case "#enemies":
                        case "#sort":
                        case "#inactive":
                        case "#attacking":
                        case "#fighting":
                        case "#defending":
                        case "#treaty":
                            continue;
                        case "#continent":
                            nations.removeIf(n -> !strFilter.getValue().apply(n.getContinent().name().toLowerCase()));
                            continue;
                        case "#color":
                            nations.removeIf(n -> !strFilter.getValue().apply(n.getColor().name().toLowerCase()));
                            continue;
                        case "#warpolicy":
                        case "#policy":
                            nations.removeIf(n -> !strFilter.getValue().apply(n.getWarPolicy().name().toLowerCase()));
                            continue;
                        case "#dompolicy":
                        case "#domesticpolicy":
                            nations.removeIf(n -> !strFilter.getValue().apply(n.getDomesticPolicy().name().toLowerCase()));
                            continue;
                        default:
                            throw new IllegalArgumentException("Invalid filter (2): `" + filterArg + "`");
                    }
                }
                Project project = Projects.get(filter.getKey().substring(1));
                if (project != null) {
                    nations.removeIf(n -> !filter.getValue().apply(n.hasProject(project) ? 1d : 0d));
                    continue;
                }

                Map<DBNation, Map<Integer, JavaCity>> cityCache = new HashMap<>();
                switch (filter.getKey().toLowerCase()) {
                    case "#ispowered": {
                        nations.removeIf(n -> !filter.getValue().apply(n.isPowered() ? 1d : 0d));
                        continue;
                    }
                    case "#spyrange":
                    case "#warrange": {
                        continue;
                    }
                    case "#turntimer": {
                        nations.removeIf(n -> !filter.getValue().apply((double) (n.getCityTurns() - TimeUtil.getTurn())));
                        continue;
                    }
                    case "#barracks":
                    case "#factories":
                    case "#hangars":
                    case "#drydocks":
                        nations.removeIf(new Predicate<DBNation>() {
                            @Override
                            public boolean test(DBNation n) {
                                Map<Integer, JavaCity> cities = cityCache.computeIfAbsent(n, f -> f.getCityMap(false, false));
                                double total = 0;
                                for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
                                    JavaCity city = entry.getValue();
                                    Building building = null;
                                    switch (filter.getKey().toLowerCase()) {
                                        case "#barracks":
                                            building = Buildings.BARRACKS;
                                            break;
                                        case "#factories":
                                            building = Buildings.FACTORY;
                                            break;
                                        case "#hangars":
                                            building = Buildings.HANGAR;
                                            break;
                                        case "#drydocks":
                                            building = Buildings.DRYDOCK;
                                            break;
                                    }
                                    total += city.get(building);
                                }
                                total /= cities.size();

                                return !filter.getValue().apply(total);
                            }
                        });
                        continue;
                    case "#aarank":
                    case "#topx": {
                        nations.removeIf(f -> f.getAlliance_id() == 0);
                        Map<Integer, List<DBNation>> byScore = Locutus.imp().getNationDB().getNationsByAlliance(true, false, true, true);
                        Map<Integer, Integer> aaRanks = new HashMap<>();
                        int rank = 1;
                        for (Map.Entry<Integer, List<DBNation>> entry : byScore.entrySet()) aaRanks.put(entry.getKey(), rank++);
                        nations.removeIf(n -> !filter.getValue().apply((double) aaRanks.getOrDefault(n.getAlliance_id(), Integer.MAX_VALUE)));

                        continue;
                    }
                    case "#soldier%": {
                        nations.removeIf(n -> !filter.getValue().apply((double) (100 * n.getSoldiers() / (Math.max(1, Buildings.BARRACKS.max() * Buildings.BARRACKS.cap() * n.getCities())))));
                        continue;
                    }
                    case "#tank%": {
                        nations.removeIf(n -> !filter.getValue().apply((double) (100 * n.getTanks() / (Math.max(1, Buildings.FACTORY.max() * Buildings.FACTORY.cap() * n.getCities())))));
                        continue;
                    }
                    case "#plane%":
                    case "#aircraft%": {
                        nations.removeIf(n -> !filter.getValue().apply((double) (100 * n.getAircraft() / (Math.max(1, Buildings.HANGAR.max() * Buildings.HANGAR.cap() * n.getCities())))));
                        continue;
                    }
                    case "#ship%": {
                        nations.removeIf(n -> !filter.getValue().apply((double) (100 * n.getShips() / (Math.max(1, Buildings.DRYDOCK.max() * Buildings.DRYDOCK.cap() * n.getCities())))));
                        continue;
                    }
                    case "#registered":
                    case "#verified": {
                        nations.removeIf(n -> !filter.getValue().apply(n.getUser() == null ? 0d : 1d));
                        continue;
                    }
                    case "#online": {
                        nations.removeIf(n -> !filter.getValue().apply(n.isOnline() ? 1d : 0d));
                        continue;
                    }
                    case "#strength": {
                        nations.removeIf(n -> !filter.getValue().apply(n.getRelativeStrength()));
                        continue;
                    }
                    case "#beige":
                        nations.removeIf(n -> !filter.getValue().apply((double) n.getBeigeTurns()));
                        continue;
                    case "#avgbuildings":
                        nations.removeIf(n -> !filter.getValue().apply((double) n.getAvgBuildings()));
                    case "#numwarsagainstactives":
                        nations.removeIf(n -> !filter.getValue().apply((double) n.getNumWarsAgainstActives()));
                        continue;
                    case "#numwars":
                    case "#wars":
                        nations.removeIf(n -> !filter.getValue().apply((double) n.getNumWars()));
                        continue;
                    case "#unpowered":
                        nations.removeIf(n -> {
                            Map<Integer, JavaCity> cities = n.getCityMap(false, false);
                            int unpowered = 0;
                            for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
                                JavaCity city = entry.getValue();
                                if (city.getPoweredInfra() < city.getInfra()) unpowered += 1;
                            }
                            return !filter.getValue().apply((double) unpowered);
                        });
                        continue;
                    case "#freebuildings":
                        nations.removeIf(n -> {
                            Map<Integer, JavaCity> cities = n.getCityMap(false, false);
                            int slots = 0;
                            for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
                                JavaCity city = entry.getValue();
                                slots += city.getFreeSlots();
                            }
                            return !filter.getValue().apply((double) slots);
                        });
                        continue;

                }
                String key = filter.getKey().substring(1);
                String keyLower = key.toLowerCase();
                try {
                    Field field = DBNation.class.getDeclaredField(key);
                    field.setAccessible(true);
                    nations.removeIf(new Predicate<DBNation>() {
                        @Override
                        public boolean test(DBNation nation) {
                            try {
                                Number number = ((Number) field.get(nation));
                                if (number == null) return true;
                                return !filter.getValue().apply(number.doubleValue());
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                } catch (RuntimeException | NoSuchFieldException e) {
                    boolean hasMethod = false;
                    for (Method method : DBNation.class.getDeclaredMethods()) {
                        String methodName = method.getName().toLowerCase();
                        if ((methodName.equals(keyLower) || methodName.equals("get" + keyLower)) && method.getParameters().length == 0) {
                            if (method.getReturnType() == boolean.class) {
                                hasMethod = true;
                                nations.removeIf(new Predicate<DBNation>() {
                                    @Override
                                    public boolean test(DBNation nation) {
                                        try {
                                            boolean value = (boolean) method.invoke(nation);
                                            return !filter.getValue().apply(value ? 1d : 0d);
                                        } catch (IllegalAccessException | InvocationTargetException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });
                            } else if (method.getReturnType() == int.class || method.getReturnType() == long.class || method.getReturnType() == double.class) {
                                hasMethod = true;
                                nations.removeIf(new Predicate<DBNation>() {
                                    @Override
                                    public boolean test(DBNation nation) {
                                        try {
                                            Number value = (Number) method.invoke(nation);
                                            return !filter.getValue().apply(value.doubleValue());
                                        } catch (IllegalAccessException | InvocationTargetException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });
                            }
                        }
                    }
                    if (!hasMethod) {
                        throw new IllegalArgumentException("Invalid filter (3): `" + filterArg + "`");
                    }
                }
            }

            if (inactiveTurns != 0) {
                nations = filter(nations, inactiveTurns, inactivePct);
            }
            if (orNations == null) orNations = nations;
            else orNations.addAll(nations);
        }
        return orNations;
    }

    public static Set<DBNation> filter(Set<DBNation> nations, long inactiveTurns, double inactivePct) {
        int finalInactiveTurns = (int) inactiveTurns;
        double activeProbability = 1 - (inactivePct / 100);
        nations.removeIf(nation -> {
            if (nation.getVm_turns() != 0 || nation.isBeige()) return true;
            if (nation.getActive_m() < 10000) {
                int twoWeeks = 14 * 12;
                Activity activity = new Activity(nation.getNation_id(), twoWeeks);
                double chance = activity.loginChance(finalInactiveTurns, true);
                return chance > activeProbability;
            }
            return false;
        });
        return nations;
    }

    public static Set<Integer> parseAlliances(Guild guild, String aa) {
        Set<Integer> aaIds = new HashSet<>();
        for (String aaName : aa.split(",")) {
            aaName = aaName.trim();
            Integer aaId = null;

            if (aaName.startsWith("~")) {
                aaName = aaName.substring(1);
            } else {
                aaId = PnwUtil.parseAllianceId(aaName);
            }

            if (aaId == null) {
                if (guild != null) {
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
        }
        List<Category> categories = guild.getCategoriesByName(categoryStr, true);
        if (categories.size() == 1) {
            return categories.get(0);
        }
        return null;
    }

    public static void copyMessage(Message msg, JDA outputAPI, long outputGuildId, long outputChannelId) {
        Guild guild = outputAPI.getGuildById(outputGuildId);
        if (guild != null) {
            GuildMessageChannel outChannel = guild.getTextChannelById(outputChannelId);
            if (outChannel != null) {
                MessageBuilder copy = new MessageBuilder(msg);
                copy.setContent(msg.getGuild().toString() + ": " + DiscordUtil.trimContent(msg.getContentRaw()));
                RateLimitUtil.queue(outChannel.sendMessage(copy.build()));
                for (Message.Attachment attachment : msg.getAttachments()) {
                    RateLimitUtil.queue(outChannel.sendMessage(attachment.getUrl()));
                }
            }
        }
    }

    public static void pending(MessageChannel channel, Message message, String title, String desc, char f) {
        pending(channel, message, title, desc, f, "");
    }

    public static void pending(MessageChannel channel, Message message, String title, String desc, char f, String cmdAppend) {
        String cmd = DiscordUtil.trimContent(message.getContentRaw()) + " -" + f + cmdAppend;
        DiscordUtil.createEmbedCommand(channel, "Confirm: " + title, desc, "\u2705", cmd);
    }

    public static String getChannelUrl(GuildMessageChannel channel) {
        return "https://discord.com/channels/" + channel.getGuild().getIdLong() + "/" + channel.getIdLong();
    }

    public static void copyMessage(Message message, GuildMessageChannel outChannel) {
        RateLimitUtil.queue(outChannel.sendMessage(message));
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
        DBNation nation = DBNation.byId(nationId);
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
}
