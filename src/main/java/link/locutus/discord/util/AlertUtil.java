package link.locutus.discord.util;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.discord.GuildShardManager;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

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
    public static void forEachChannel(Class permission, GuildSetting<MessageChannel> key, BiConsumer<MessageChannel, GuildDB> channelConsumer) {
        forEachChannel(f -> f.getPermission(permission) > 0, key, channelConsumer);
    }

    public static void forEachChannel(Function<GuildDB, Boolean> hasPerm, GuildSetting<MessageChannel> key, BiConsumer<MessageChannel, GuildDB> channelConsumer) {
        for (GuildDB guildDB : Locutus.imp().getGuildDatabases().values()) {
            try {
                if (!hasPerm.apply(guildDB)) continue;
                MessageChannel channel = guildDB.getOrNull(key, false);
                if (channel == null) {
                    continue;
                }
                if (channel instanceof GuildMessageChannel guildChan) {
                    Guild guild = guildChan.getGuild();
                    if (guild.getIdLong() != guildDB.getIdLong()) {
                        guildDB = Locutus.imp().getGuildDB(guild);
                    }
                }
                channelConsumer.accept(channel, guildDB);
            } catch (InsufficientPermissionException e) {
                guildDB.deleteInfo(key);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public static void auditAlert(DBNation nation, AttackTypeSubCategory type, String msg) {
        auditAlert(nation, null, f -> msg);
    }

    public static void auditAlert(DBNation nation, AutoAuditType type, String msg) {
        auditAlert(nation, type, f -> msg);
    }

    public static void auditAlert(DBNation nation, AutoAuditType type, Function<GuildDB, String> messageSuplier) {
        if (nation.getPosition() <= 1) return;
        GuildDB guildDb = Locutus.imp().getGuildDBByAA(nation.getAlliance_id());
        if (guildDb == null || !guildDb.isWhitelisted()) return;
        User user = nation.getUser();
        if (user == null) return;
        Guild guild = guildDb.getGuild();
        Member member = guild.getMemberById(user.getIdLong());
        if (member == null) return;

        GuildMessageChannel channel = (GuildMessageChannel) guildDb.getOrNull(GuildKey.MEMBER_AUDIT_ALERTS);
        if (channel == null) return;
        String message = messageSuplier.apply(guildDb);
        if (message == null) return;

        if (type != null) {
            Set<AutoAuditType> optOut = guildDb.getOrNull(GuildKey.DISABLED_MEMBER_AUDITS);
            if (optOut != null && optOut.contains(type)) return;
        }

        // TODO put result in database
        Role pingOptOut = Roles.AUDIT_ALERT_OPT_OUT.toRole(channel.getGuild());
        Role pingOptOut2 = Roles.AUDIT_ALERT_OPT_OUT.toRole(guild);
        boolean hasOptOut = (pingOptOut != null && member.getRoles().contains(pingOptOut)) || (pingOptOut2 != null && member.getRoles().contains(pingOptOut2));
        if (hasOptOut) {
            message = member.getEffectiveName() + " " + message;
        } else {
            message = member.getAsMention() + "(opt out: " + CM.alerts.audit.optout.cmd.toSlashMention() + "):\n" + message;
        }
        RateLimitUtil.queueWhenFree(channel.sendMessage(message));
    }

    public static void alertNation(Class permission, GuildSetting channelKey, DBNation nation, BiConsumer<Map.Entry<Guild, MessageChannel>, Member> channelConsumer) {
        alertNation(f -> f.getPermission(permission) > 0, channelKey, nation, channelConsumer);
    }

    public static void alertNation(Function<GuildDB, Boolean> hasPerm, GuildSetting<MessageChannel> channelKey, DBNation nation, BiConsumer<Map.Entry<Guild, MessageChannel>, Member> channelConsumer) {
        if (nation.getAlliance_id() == 0 || nation.getPosition() <= 1) return;
        PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(nation.getNation_id());
        if (user != null) {
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

    public static void forEachChannel(Class permission,GuildSetting key, Set<Long> mentions, BiConsumer<Map.Entry<Guild, MessageChannel>, Set<Member>> channelConsumer) {
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
                    RateLimitUtil.queueWhenFree(channel.sendMessageEmbeds(msg));
                } catch (InsufficientPermissionException ignore) {
                    System.out.println("!! " + channel.getName() + " | " + channel.getGuild().getName() + " | " + ignore.getMessage());
                }
            }
        }
    }

    private static TrayIcon trayIcon;

    static
    {
        try {
            if (SystemTray.isSupported()) {
                SystemTray tray = SystemTray.getSystemTray();

                Image image = Toolkit.getDefaultToolkit().createImage("icon.png");

                trayIcon = new TrayIcon(image);
                tray.add(trayIcon);
            }
        } catch (Throwable e) {
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
        if (trayIcon != null) {
            trayIcon.displayMessage(title, body, TrayIcon.MessageType.INFO);
        }
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
        RateLimitUtil.queueWhenFree(channel.sendMessage(StringMan.join(pings, " ")));
    }

    public static void error(String title, String body) {
        if (Settings.INSTANCE.DISCORD.CHANNEL.ERRORS == 0) return;
        if (title == null) title = "error";
        else if (title.toLowerCase().contains("captcha")) return;
        try {
            DiscordUtil.createEmbedCommand(Settings.INSTANCE.DISCORD.CHANNEL.ERRORS, title, body);
        } catch (IllegalArgumentException ignore) {
            ignore.printStackTrace();
        }
    }

    public static void error(String title, Throwable e) {
        Map.Entry<String, String> keyVal = StringMan.stacktraceToString(e);
        String body = "**" + keyVal.getKey() + "**\n" + keyVal.getValue();
        error(title, body);
    }
}
