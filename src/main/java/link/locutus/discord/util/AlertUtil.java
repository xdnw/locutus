package link.locutus.discord.util;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.discord.GuildShardManager;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.exceptions.MissingAccessException;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class AlertUtil {
    public static void forEachChannel(Class permission, GuildDB.Key key, BiConsumer<MessageChannel, GuildDB> channelConsumer) {
        forEachChannel(f -> f.getPermission(permission) > 0, key, channelConsumer);
    }

    public static void forEachChannel(Function<GuildDB, Boolean> hasPerm, GuildDB.Key key, BiConsumer<MessageChannel, GuildDB> channelConsumer) {
        for (GuildDB guildDB : Locutus.imp().getGuildDatabases().values()) {
            try {
                if (!hasPerm.apply(guildDB)) continue;
                String channelId = guildDB.getInfo(key, false);
                if (channelId == null) {
                    continue;
                }

                GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(Long.parseLong(channelId));
                if (channel == null) {
                    continue;
                }
                channelConsumer.accept(channel, guildDB);
            } catch (InsufficientPermissionException e) {
                guildDB.deleteInfo(key);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public static void auditAlert(DBNation nation, AuditType type, String msg) {
        auditAlert(nation, type, f -> msg);
    }

    public static void auditAlert(DBNation nation, AuditType type, Function<GuildDB, String> messageSuplier) {
        if (nation.getPosition() <= 1) return;
        GuildDB guildDb = Locutus.imp().getGuildDBByAA(nation.getAlliance_id());
        if (guildDb == null || !guildDb.isWhitelisted()) return;
        User user = nation.getUser();
        if (user == null) return;
        Guild guild = guildDb.getGuild();
        Member member = guild.getMemberById(user.getIdLong());
        if (member == null) return;

        Role pingOptOut = Roles.AUDIT_ALERT_OPT_OUT.toRole(guild);
        MessageChannel channel = guildDb.getOrNull(GuildDB.Key.MEMBER_AUDIT_ALERTS);
        if (channel == null) return;
        String message = messageSuplier.apply(guildDb);
        if (message == null) return;

        // TODO put result in database

        if (pingOptOut != null && member.getRoles().contains(pingOptOut)) {
            message = member.getEffectiveName() + " " + message;
        } else {
            message = member.getAsMention() + "(see pins to opt out):\n" + message;
        }
        RateLimitUtil.queue(channel.sendMessage(message));
    }

    public static void alertNation(Class permission, GuildDB.Key channelKey, DBNation nation, BiConsumer<Map.Entry<Guild, MessageChannel>, Member> channelConsumer) {
        alertNation(f -> f.getPermission(permission) > 0, channelKey, nation, channelConsumer);
    }

    public static void alertNation(Function<GuildDB, Boolean> hasPerm, GuildDB.Key channelKey, DBNation nation, BiConsumer<Map.Entry<Guild, MessageChannel>, Member> channelConsumer) {
        if (nation.getAlliance_id() == 0 || nation.getPosition() <= 1) return;
        PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(nation.getNation_id());
        if (user != null && user.getDiscordId() != null) {
            GuildDB guildDb = Locutus.imp().getGuildDBByAA(nation.getAlliance_id());
            if (guildDb != null && hasPerm.apply(guildDb)) {
                Guild guild = guildDb.getGuild();
                if (guild != null) {
                    Member member = guild.getMemberById(user.getDiscordId());
                    if (member != null) {
                        MessageChannel channel = guildDb.getOrNull(channelKey, false);
                        if (channel != null) {
                            AbstractMap.SimpleEntry<Guild, MessageChannel> entry = new AbstractMap.SimpleEntry<>(guild, channel);
                            channelConsumer.accept(entry, member);
                        }
                    }
                }
            }
        }
    }

    public static void forEachChannel(Class permission, GuildDB.Key key, Set<Long> mentions, BiConsumer<Map.Entry<Guild, MessageChannel>, Set<Member>> channelConsumer) {
        forEachChannel(permission, key, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDb) {
                Guild guild = guildDb.getGuild();
                if (guild == null) return;
                Set<Member> thisChannelMentions = null;
                for (long user : mentions) {
                    Member member = guild.getMemberById(user);
                    if (member == null) continue;
                    if (thisChannelMentions == null) thisChannelMentions = new LinkedHashSet<>();
                    thisChannelMentions.add(member);
                }
                if (thisChannelMentions != null) {
                    AbstractMap.SimpleEntry<Guild, MessageChannel> entry = new AbstractMap.SimpleEntry<>(guild, channel);
                    channelConsumer.accept(entry, thisChannelMentions);
                }
            }
        });
    }

    public static void openDesktop(String url) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    public static void displayChannel(String title, String body, long... channelIds) {
        GuildShardManager api = Locutus.imp().getDiscordApi();

        for (long channelId : channelIds) {
            GuildMessageChannel channel = api.getGuildChannelById(channelId);
            if(channel !=null) {
                MessageEmbed msg = new EmbedBuilder().setTitle(title).setDescription(body).build();
                try {
                    RateLimitUtil.queue(channel.sendMessageEmbeds(msg));
                } catch (InsufficientPermissionException ignore) {
                    System.out.println("!! " + channel.getName() + " | " + channel.getGuild().getName() + " | " + ignore.getMessage());
                }
            }
        }
    }

    private static TrayIcon trayIcon;

    static
    {
        Image image = Toolkit.getDefaultToolkit().createImage("icon.png");
        SystemTray tray = SystemTray.getSystemTray();

        trayIcon = new TrayIcon(image);
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public static void openUrl(String url) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    public static void displayTray(String title, String body) {
        trayIcon.displayMessage(title, body, TrayIcon.MessageType.INFO);
    }

    private static final Cache<String, Boolean> PING_BUFFER = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .build();

    public static void bufferPing(MessageChannel channel, String... pings) {
        synchronized (AlertUtil.class) {
            for (int i = 0; i < pings.length; i++) {
                String ping = pings[i];
                if (PING_BUFFER.getIfPresent(ping) != null) {
                    pings[i] = "`" + ping + "`";
                }
                PING_BUFFER.put(ping, true);
            }
        }
        RateLimitUtil.queue(channel.sendMessage(StringMan.join(pings, " ")));
    }

    public static void error(String title, String body) {
        if (title == null) title = "error";
        DiscordUtil.createEmbedCommand(Settings.INSTANCE.DISCORD.CHANNEL.ERRORS, title, body);
    }

    public static void error(String title, Throwable e) {
        Map.Entry<String, String> keyVal = StringMan.stacktraceToString(e);
        String body = "**" + keyVal.getKey() + "**\n" + keyVal.getValue();
        error(title, body);
    }
}
