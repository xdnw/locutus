package link.locutus.discord.commands.war;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.shrink.EmbedShrink;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.mail.MailApiResponse;
import link.locutus.discord.util.task.mail.MailApiSuccess;
import link.locutus.discord.util.task.war.WarCard;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class WarRoomUtil {
    public static final Permission[] CATEGORY_PERMISSIONS = new Permission[]{
            Permission.VIEW_CHANNEL,
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_PERMISSIONS
    };

    public static StandardGuildMessageChannel createChannel2(WarCategory warCategory2, WarRoom room, DBNation target, boolean create, boolean planning, WarRoomRateLimit source) {
        String catPrefix = warCategory2.getCatPrefix();
        GuildDB db = warCategory2.getGuildDb();
        Guild guild = warCategory2.getGuild();

        StandardGuildMessageChannel foundChannel = null;
        synchronized (target.getName()) {
            for (Category category : guild.getCategories()) {
                String catName = category.getName().toLowerCase();
                if (catName.startsWith(catPrefix)) {
                    for (TextChannel channel : category.getTextChannels()) {
                        String channelName = channel.getName();
                        String[] split = channelName.split("-");
                        if (MathMan.isInteger(split[split.length - 1])) {
                            int targetId = Integer.parseInt(split[split.length - 1]);
                            if (targetId == target.getNation_id()) {
                                foundChannel = channel;
                            }
                        }
                    }
                }
            }

            if (!create || foundChannel != null) {
                return foundChannel;
            }

            Category useCat = null;
            for (Category category : guild.getCategories()) {
                String catName = category.getName().toLowerCase();
                if (!catName.startsWith(catPrefix)) continue;
                List<GuildChannel> channels = category.getChannels();
                if (channels.size() >= 49) continue;

                CityRanges range = getRangeFromCategory(category);
                if (range != null) {
                    if (range.contains(target.getCities())) {
                        useCat = category;
                        break;
                    } else if (useCat == null) {
                        useCat = category;
                    }
                } else {
                    useCat = category;
                }
            }

            if (useCat == null) {
                for (int i = 0; ; i++) {
                    String name = catPrefix + "-" + i;
                    List<Category> existingCat = guild.getCategoriesByName(name, true);
                    if (existingCat.isEmpty()) {
                        useCat = RateLimitUtil.complete(guild.createCategory(name), source);
                        Member botMember = guild.getMemberById(Settings.INSTANCE.APPLICATION_ID);
                        if (botMember != null) {
                            PermissionOverrideAction upsert = useCat.upsertPermissionOverride(botMember);
                            for (Permission perm : CATEGORY_PERMISSIONS) {
                                upsert = upsert.setAllowed(perm);
                            }
                            RateLimitUtil.queue(upsert, source);
                        }
                        RateLimitUtil.queue(useCat.upsertPermissionOverride(guild.getRolesByName("@everyone", false).get(0)).deny(Permission.VIEW_CHANNEL), source);

                        List<CompletableFuture<PermissionOverride>> futures = new ArrayList<>();

                        for (Role role : Roles.MILCOM.toRoles(db)) {
                            futures.add(RateLimitUtil.queue(useCat.upsertPermissionOverride(role)
                                    .setAllowed(Permission.VIEW_CHANNEL), source));
                        }
                        for (Role role : Roles.MILCOM_NO_PINGS.toRoles(db)) {
                            futures.add(RateLimitUtil.queue(useCat.upsertPermissionOverride(role)
                                    .setAllowed(Permission.VIEW_CHANNEL), source));
                        }
                        if (!futures.isEmpty()) {
                            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                        }
                        break;
                    }
                }
            }
            String name = target.getNation() + "-" + target.getNation_id();
            if (planning) name = "\uD83D\uDCC5" + name;
            foundChannel = RateLimitUtil.complete(useCat.createTextChannel(name), source);

            warCategory2.processChannelCreation(room, foundChannel, planning, source);
        }
        return foundChannel;
    }

    public static Long getPinnedMessageId(String topic) {
        if (topic == null || topic.isEmpty()) return null;
        try {
            topic = topic.split(" ")[0];
            if (topic.contains("/")) {
                String[] split = topic.split("/");
                topic = split[split.length - 1];
            }
            return Long.parseLong(topic);
        } catch (Exception e) {
            return null;
        }
    }

    public static CompletableFuture<Long> updatePin(StandardGuildMessageChannel channel, DBNation target, Set<DBNation> participants, boolean update) {
        if (channel == null || channel.getGuild().getTextChannelById(channel.getIdLong()) == null) {
            return CompletableFuture.completedFuture(null);
        }

        Long existingMessageId = getPinnedMessageId(channel.getTopic());
        if (existingMessageId != null && !update) {
            return CompletableFuture.completedFuture(existingMessageId);
        }

        DiscordMessageBuilder msg = (DiscordMessageBuilder) new DiscordChannelIO(channel).create();
        buildPinMessage(msg, target, participants);

        if (existingMessageId == null) {
            CompletableFuture<Long> future = new CompletableFuture<>();
            msg.send().whenComplete((sent, error) -> {
                if (error != null) {
                    future.completeExceptionally(error);
                    return;
                }
                long messageId = sent.getId();
                queuePinnedMessageMetadata(channel, messageId, WarRoomRateLimit.INITIAL_PIN);
                future.complete(messageId);
            });
            return future;
        }

        long messageId = existingMessageId;
        MessageEditData editData = msg.buildEdit(true);
        String key = messageEditKey(channel, messageId);
        return RateLimitUtil.queueLatest(key, WarRoomRateLimit.PIN_REFRESH,
                () -> RateLimitUtil.queue(channel.editMessageById(messageId, editData), WarRoomRateLimit.PIN_REFRESH))
                .thenApply(Message::getIdLong);
    }

    private static void buildPinMessage(DiscordMessageBuilder msg, DBNation target, Set<DBNation> participants) {
        StringBuilder body = new StringBuilder();

        body.append(target.getNationUrlMarkup())
                .append(" | ").append(target.getAllianceUrlMarkup());
        body.append(target.toMarkdown(true, true, false, false, true, false));
        body.append("\n");

        Set<DBWar> wars = target.getActiveWars();
        for (DBWar war : wars) {
            boolean defensive = war.getAttacker_id() == target.getNation_id();
            DBNation participant = Locutus.imp().getNationDB().getNationById(war.getAttacker_id() == target.getNation_id() ? war.getDefender_id() : war.getAttacker_id());

            if (participant != null && (participants.contains(participant) || participant.active_m() < 2880)) {
                String typeStr = defensive ? "\uD83D\uDEE1 " : "\uD83D\uDD2A ";
                body.append(typeStr).append('`').append(participant.getNation()).append('`')
                        .append(" | ").append(participant.getAllianceName());

                WarCard card = new WarCard(war, false);
                if (card.blockaded == participant.getNation_id()) body.append("\u26F5");
                if (card.airSuperiority != 0 && card.airSuperiority == participant.getNation_id()) {
                    body.append("\u2708");
                }
                if (card.groundControl != 0 && card.groundControl == participant.getNation_id()) {
                    body.append("\uD83D\uDC82");
                }

                body.append(participant.toMarkdown(true, false, false, true, false));
            }
        }
        body.append("\n");
        body.append("Note: These figures are only updated every 5m");

        EmbedShrink builder = new EmbedShrink();
        builder.title("**Enemy:** ");
        builder.setDescription(body.toString().replaceAll(" \\| ", "|"));

        msg.clearEmbeds()
                .clearButtons()
                .embed(builder)
                .commandButton(CommandBehavior.UNPRESS, CM.war.room.pin.cmd, "Update");
    }

    private static void queuePinnedMessageMetadata(StandardGuildMessageChannel channel, long messageId, WarRoomRateLimit source) {
        String newTopic = DiscordUtil.getChannelUrl(channel) + "/" + messageId + " " + CM.war.room.pin.cmd.toSlashMention();
        RateLimitUtil.queueLatest(channelTopicKey(channel), source,
                () -> RateLimitUtil.queue(channel.getManager().setTopic(newTopic), source));
        RateLimitUtil.queueLatest(channelPinKey(channel), source,
                () -> RateLimitUtil.queue(channel.pinMessageById(messageId), source));
    }

    public static boolean queueRoomStateSync(WarRoom room, WarRoomRateLimit source) {
        StandardGuildMessageChannel channel = room.channel;
        if (channel == null) {
            return false;
        }
        String expectedName = buildRoomChannelName(room);
        if (expectedName.equals(channel.getName())) {
            return false;
        }
        RateLimitUtil.queueLatest(channelNameKey(channel), source,
                () -> RateLimitUtil.queue(channel.getManager().setName(expectedName), source));
        return true;
    }

    public static String buildRoomChannelName(WarRoom room) {
        StringBuilder name = new StringBuilder();
        if (room.planning) name.append("\uD83D\uDCC5");
        if (room.enemyGc) name.append("\uD83D\uDC82");
        if (room.enemyAc) name.append("\u2708");
        if (room.enemyBlockade) name.append("\u26F5");
        name.append(room.target.getNation()).append('-').append(room.target.getNation_id());
        return name.toString();
    }

    private static String messageEditKey(StandardGuildMessageChannel channel, long messageId) {
        return "message-edit:" + channel.getIdLong() + ':' + messageId;
    }

    private static String channelTopicKey(StandardGuildMessageChannel channel) {
        return "channel-topic:" + channel.getIdLong();
    }

    private static String channelPinKey(StandardGuildMessageChannel channel) {
        return "channel-pin:" + channel.getIdLong();
    }

    private static String channelNameKey(StandardGuildMessageChannel channel) {
        return "channel-name:" + channel.getIdLong();
    }

    public static void handleRoomCreation(WarRoom room, User author, GuildDB db, Consumer<String> errorOutput, boolean ping, boolean addMember, boolean addMessage, DBNation target, Collection<DBNation> attackers) {
        long channelId = room.channelId;
        StandardGuildMessageChannel channel = room.channel;
        if (channel == null) return;

        String declareUrl = target.getDeclareUrl();
        String channelUrl = "https://discord.com/channels/" + db.getGuild().getIdLong() + "/" + channelId;
        String info = "> A counter is when an alliance declares a war on a nation for attacking one of its members/applicants. We usually only order counters for unprovoked attacks on members.\n" +
                "About Counters: https://docs.google.com/document/d/1eJfgNRk6L72G6N3MT01xjfn0CzQtYibwnTg9ARFknRg";

        if (addMessage) {
            RateLimitUtil.queueMessage(channel, info, WarRoomRateLimit.ROOM_INFO);
        }

        for (DBNation attacker : attackers) {
            User user = attacker.getUser();
            if (user == null) {
                errorOutput.accept("No user for: " + attacker.getNation() + " | " + attacker.getAllianceName() + ". Have they used " + CM.register.cmd.toSlashMention() + " ?");
                continue;
            }

            Member member = db.getGuild().getMemberById(user.getIdLong());
            if (member == null) {
                errorOutput.accept("No member for: " + attacker.getNation() + " | " + attacker.getAllianceName() + ". Are they on this discord?");
                continue;
            }

            if (addMember) {
                List<PermissionOverride> overrideds = channel.getMemberPermissionOverrides();
                boolean contains = false;
                for (PermissionOverride overrided : overrideds) {
                    if (member.equals(overrided.getMember())) {
                        contains = true;
                        break;
                    }
                }

                if (!contains) {
                    RateLimitUtil.queue(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL), WarRoomRateLimit.MANUAL_ROOM_CREATE);
                    if (ping) {
                        String msg = author.getName() + " added " + user.getAsMention();

                        if (addMessage) {
                            String warType = target.getAvg_infra() > 2000 && target.getAvg_infra() > attacker.getAvg_infra() ? "attrition" : "raid";
                            msg += ". Please declare a war of type `" + warType + "` with reason `counter`.";

                            Role econRole = Roles.ECON.toRole(attacker.getAlliance_id(), db);
                            String econRoleName = econRole != null ? "`@" + econRole.getName() + "`" : "ECON";

                            MessageChannel rssChannel = db.getResourceChannel(attacker.getAlliance_id());
                            MessageChannel grantChannel = db.getOrNull(GuildKey.GRANT_REQUEST_CHANNEL);

                            if (rssChannel != null) {
                                if (Boolean.TRUE.equals(db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW))) {
                                    msg += " Withdraw funds from: " + rssChannel.getAsMention() + "  **BEFORE** you declare.";
                                } else {
                                    msg += " Ping " + econRoleName + " in " + rssChannel.getAsMention() + " to withdraw funds **BEFORE** you declare.";
                                }
                            }
                            if (grantChannel != null)
                                msg += " Request funds from: " + grantChannel.getAsMention() + " **BEFORE** you declare.";

                            if (target.getGroundStrength(true, true) > attacker.getGroundStrength(true, false)) {
                                msg += "\nThe enemy has more ground. You must ensure you have funds to switch to e.g. mmr=5550 and buy tanks after declaring.";
                            }

                            String title = "Counter Attack/" + channel.getIdLong();
                            String body = info +
                                    "\n\n" + msg +
                                    "\n- target: " + declareUrl +
                                    "\n\nCheck the war room for further details: " + channelUrl;
                            String mailBody = MarkupUtil.transformURLIntoLinks(MarkupUtil.markdownToHTML(body));

                            MailApiResponse result = attacker.sendMail(ApiKeyPool.create(Locutus.imp().getRootAuth().getApiKey()), title, mailBody, false);
                            if (result.status() != MailApiSuccess.SUCCESS) {
                                errorOutput.accept("Failed to send mail to " + attacker.getNation() + " | " + attacker.getAllianceName() + ": " + result.status() + " " + result.error());
                            }
                        }

                        RateLimitUtil.queueMessage(channel, msg + "\n- <" + declareUrl + '>', WarRoomRateLimit.ROOM_INFO);
                    }
                }
            }
        }
        return;
    }

    public static WarRoom getGlobalWarRoom(MessageChannel channel, WarCatReason reason) {
        if (!(channel instanceof StandardGuildMessageChannel)) return null;
        Guild guild = ((StandardGuildMessageChannel) channel).getGuild();
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db != null) {
            WarCategory warRooms = db.getWarChannel();
            if (warRooms != null) {
                return warRooms.getWarRoom((StandardGuildMessageChannel) channel, reason);
            }
        }
        return null;
    }

    public static Set<WarRoom> getGlobalWarRooms(DBNation target, WarCatReason reason) {
        Set<WarCategory> warCategories = null;
        for (Map.Entry<Long, GuildDB> entry : Locutus.imp().getGuildDatabases().entrySet()) {
            GuildDB db = entry.getValue();
            if (db.isDelegateServer()) continue;
            WarCategory warcat = db.getWarChannel();
            if (warcat != null) {
                if (warCategories == null) warCategories = new HashSet<>();
                warCategories.add(warcat);
            }
        }

        Set<WarRoom> result = null;
        if (warCategories == null) return null;
        for (WarCategory warcat : warCategories) {
            if (warcat != null) {
                WarRoom channel = warcat.createWarRoom(target, false, false, false, reason);
                if (channel != null) {
                    if (result == null) result = new HashSet<>();
                    result.add(channel);
                }
            }
        }

        return result;
    }

    public static NationFilter getFilter(GuildDB db) {
        return GuildKey.WAR_ROOM_FILTER.getOrNull(db);
    }

    public static CityRanges getRangeFromCategory(Category category) {
        String[] split = category.getName().toLowerCase().split("-", 2);
        if (split.length == 2) {
            String filterStr = split[1];
            var char0 = filterStr.charAt(0);
            if (char0 == 'c' || char0 == 'C') {
                if (!filterStr.contains("-") && !filterStr.contains("+")) filterStr += "+";
                try {
                    return CityRanges.parse(filterStr);
                } catch (IllegalArgumentException ignore) {}
            }
        }
        return null;
    }

    public static void updateAllianceIds(GuildDB db, Set<Integer> allianceIds) {
        Set<Integer> aaIds = db.getAllianceIds();
        allianceIds.clear();
        if (!aaIds.isEmpty()) {
            allianceIds.addAll(aaIds);
        } else {
            allianceIds.addAll(db.getAllies(false));
        }
        for (GuildDB otherDB : Locutus.imp().getGuildDatabases().values()) {
            aaIds = otherDB.getAllianceIds();
            if (!aaIds.isEmpty()) {
                Guild warServer = otherDB.getOrNull(GuildKey.WAR_SERVER);
                if (warServer != null && warServer.getIdLong() == db.getIdLong()) {
                    allianceIds.addAll(aaIds);
                }
            }
        }
    }

    public static boolean isWarRoomCategory(Category category, String prefix) {
        if (category == null) return false;
        return category.getName().toLowerCase(Locale.ROOT).startsWith(prefix);
    }

    public static Set<Category> getCategories(GuildDB db, String catPrefix) {
        String catLower = catPrefix.toLowerCase(Locale.ROOT);
        Set<Category> result = new ObjectLinkedOpenHashSet<>();
        for (Category category : db.getGuild().getCategories()) {
            if (isWarRoomCategory(category, catLower)) {
                result.add(category);
            }
        }
        return result;
    }

    public static boolean isPlanning(StandardGuildMessageChannel channel) {
        return channel.getName().contains("\uD83D\uDCC5");
    }

    public static boolean setPlanning(StandardGuildMessageChannel channel, boolean value) {
        return DiscordUtil.setSymbol(channel, "\uD83D\uDCC5", value);
    }
}
