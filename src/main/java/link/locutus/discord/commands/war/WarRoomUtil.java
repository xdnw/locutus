package link.locutus.discord.commands.war;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
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
import link.locutus.discord.util.task.war.WarCard;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static link.locutus.discord.util.discord.DiscordUtil.setSymbol;

public class WarRoomUtil {
    public static final Permission[] CATEGORY_PERMISSIONS = new Permission[]{
            Permission.VIEW_CHANNEL,
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_PERMISSIONS
    };

    public static StandardGuildMessageChannel createChannel2(WarCategory warCategory2, WarRoom room, DBNation target, boolean create, boolean planning) {
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
                        useCat = RateLimitUtil.complete(guild.createCategory(name));
                        PermissionOverrideAction upsert = useCat.upsertPermissionOverride(guild.getMemberById(Settings.INSTANCE.APPLICATION_ID));
                        for (Permission perm : CATEGORY_PERMISSIONS) {
                            upsert = upsert.setAllowed(perm);
                        }
                        RateLimitUtil.queue(upsert);
                        RateLimitUtil.queue(useCat.upsertPermissionOverride(guild.getRolesByName("@everyone", false).get(0)).deny(Permission.VIEW_CHANNEL));

                        List<CompletableFuture<PermissionOverride>> futures = new ArrayList<>();

                        for (Role role : Roles.MILCOM.toRoles(db)) {
                            futures.add(RateLimitUtil.queue(useCat.upsertPermissionOverride(role)
                                    .setAllowed(Permission.VIEW_CHANNEL)));
                        }
                        for (Role role : Roles.MILCOM_NO_PINGS.toRoles(db)) {
                            futures.add(RateLimitUtil.queue(useCat.upsertPermissionOverride(role)
                                    .setAllowed(Permission.VIEW_CHANNEL)));
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
            foundChannel = RateLimitUtil.complete(useCat.createTextChannel(name));

            warCategory2.processChannelCreation(room, foundChannel, planning);
        }
        return foundChannel;
    }

    public static Message getMessage(StandardGuildMessageChannel channel, String topic) {
        if (topic == null || topic.isEmpty()) return null;
        try {
            topic = topic.split(" ")[0];
            if (topic.contains("/")) {
                String[] split = topic.split("/");
                topic = split[split.length - 1];
            }
            return RateLimitUtil.complete(channel.retrieveMessageById(topic));
        } catch (Exception e) {
            return null;
        }
    }

    public static IMessageBuilder updatePin(StandardGuildMessageChannel channel, DBNation target, Set<DBNation> participants, boolean update) {
        if (channel == null || channel.getGuild().getTextChannelById(channel.getIdLong()) == null) {
            return null;
        }

        String topic = channel.getTopic();

        boolean updatePin = false;
        DiscordChannelIO io = new DiscordChannelIO(channel, () -> getMessage(channel, topic));
        IMessageBuilder msg = io.getMessage();
        if (msg == null) {
            msg = io.create();
            updatePin = true;
            update = true;
        }

        if (update) {
            StringBuilder body = new StringBuilder();

            body.append("**Enemy:** ").append(target.getNationUrlMarkup())
                    .append(" | ").append(target.getAllianceUrlMarkup());
            body.append(target.toMarkdown(true, true, false, false, true, false));
            body.append("\n");

            Set<DBWar> wars = target.getActiveWars();
            for (DBWar war : wars) {
                boolean defensive = war.getAttacker_id() == target.getNation_id();
                DBNation participant = Locutus.imp().getNationDB().getNationById(war.getAttacker_id() == target.getNation_id() ? war.getDefender_id() : war.getAttacker_id());

                if (participant != null && (participants.contains(participant) || participant.active_m() < 2880)) {
                    String typeStr = defensive ? "\uD83D\uDEE1 " : "\uD83D\uDD2A ";
                    body.append(typeStr).append("`" + participant.getNation() + "`")
                            .append(" | ").append(participant.getAllianceName());

                    WarCard card = new WarCard(war, false);
                    if (card.blockaded == participant.getNation_id()) body.append("\u26F5");
                    if (card.airSuperiority != 0 && card.airSuperiority == participant.getNation_id())
                        body.append("\u2708");
                    if (card.groundControl != 0 && card.groundControl == participant.getNation_id())
                        body.append("\uD83D\uDC82");

                    body.append(participant.toMarkdown(true, false, false, true, false));
                }
            }
            body.append("\n");
            body.append("Note: These figures are only updated every 5m");

            EmbedBuilder builder = new EmbedBuilder();

            builder.setDescription(body.toString().replaceAll(" \\| ", "|"));


            msg.clearEmbeds();
            msg.clearButtons();
            msg.embed(builder.build());
            msg.commandButton(CommandBehavior.UNPRESS, CM.war.room.pin.cmd, "Update");
            try {
                CompletableFuture<IMessageBuilder> sent = msg.send();
                if (sent != null) msg = sent.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        if (updatePin) {
            String newTopic = DiscordUtil.getChannelUrl(channel) + "/" + msg.getId() + " " + CM.war.room.pin.cmd.toSlashMention();
            RateLimitUtil.queue(channel.getManager().setTopic(newTopic));
            RateLimitUtil.queue(channel.pinMessageById(msg.getId()));
        }

        return msg;
    }

    public static void handleRoomCreation(WarRoom room, User author, GuildDB db, Consumer<String> errorOutput, boolean ping, boolean addMember, boolean addMessage, DBNation target, Collection<DBNation> attackers) {
        long channelId = room.channelId;
        StandardGuildMessageChannel channel = room.channel;
        if (channel == null) return;

        String declareUrl = target.getDeclareUrl();
        String channelUrl = "https://discord.com/channels/" + db.getGuild().getIdLong() + "/" + channelId;
        String info = "> A counter is when an alliance declares a war on a nation for attacking one of its members/applicants. We usually only order counters for unprovoked attacks on members.\n" +
                "About Counters: https://docs.google.com/document/d/1eJfgNRk6L72G6N3MT01xjfn0CzQtYibwnTg9ARFknRg";

        if (addMessage && channel != null) {
            RateLimitUtil.queue(channel.sendMessage(info));
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
                    RateLimitUtil.complete(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL));
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

                            try {
                                attacker.sendMail(ApiKeyPool.create(Locutus.imp().getRootAuth().getApiKey()), title, mailBody, false);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        RateLimitUtil.queue(channel.sendMessage(msg + "\n- <" + declareUrl + (">")));
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
        String[] split = category.getName().split("-", 2);
        if (split.length == 2) {
            String filterStr = split[1];
            if (filterStr.charAt(0) == 'c') {
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
        return setSymbol(channel, "\uD83D\uDCC5", value);
    }
}
