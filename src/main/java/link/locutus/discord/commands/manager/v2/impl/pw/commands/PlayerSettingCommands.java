package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.CoalitionPermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.announce.AnnounceType;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static link.locutus.discord.util.math.ArrayUtil.MathOperator.*;

public class PlayerSettingCommands {

    @Command(desc = "View an announcement you have access to")
    @RolePermission(Roles.MEMBER)
    @Ephemeral
    public String viewAnnouncement(@Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @Me User user, int ann_id, @Switch("d") boolean document, @Switch("n") DBNation nation) throws IOException {
        if (nation == null) nation = me;
        if (nation.getId() != me.getId() && !Roles.INTERNAL_AFFAIRS.has(user, db.getGuild())) {
            throw new IllegalArgumentException("Missing role: " + Roles.INTERNAL_AFFAIRS.toDiscordRoleNameElseInstructions(db.getGuild()));
        }
        Announcement parent = db.getAnnouncement(ann_id);
        boolean isInvite = StringUtils.countMatches(parent.replacements, ",") == 2 && !parent.replacements.contains("|") && !parent.replacements.contains("\n");
        AnnounceType type = isInvite ? AnnounceType.INVITE : document ? AnnounceType.DOCUMENT : AnnounceType.MESSAGE;
        if (type == AnnounceType.INVITE) {
            String[] split = parent.replacements.split(",");
            if (!parent.replacements.isEmpty() && split.length > 0 && MathMan.isInteger(split[0])) {
                long serverId = Long.parseLong(split[0]);
                // check user is in guild
                GuildDB otherDb = Locutus.imp().getGuildDB(serverId);
                if (otherDb == null) {
                    throw new IllegalArgumentException("Cannot find server with id: `" + serverId + "`");
                }
                if (otherDb.getGuild().getMember(user) != null) {
                    return "You are already in the server: " + DiscordUtil.getGuildUrl(serverId);
                }
            }
        }

        Announcement.PlayerAnnouncement announcement;
        if (parent.allowCreation) {
            announcement = db.getOrCreatePlayerAnnouncement(ann_id, nation, type);
        } else {
            announcement = db.getPlayerAnnouncement(ann_id, nation.getNation_id());
        }
        String title;
        String message;
        if (announcement == null) {
            if (parent == null) {
                title = "Announcement #" + ann_id + " not found";
                message = "This announcement does not exist";
            } else {
                title = "Announcement #" + ann_id + " was not sent to you";
                message = "This announcement was not sent to you";
            }
        } else {
            title = "[#" + parent.id + "] " + parent.title;
            StringBuilder body = new StringBuilder();
            if (!parent.active) {
                body.append("`Archived`\n");
            }
            String content = announcement.getContent();
            if (document && content.startsWith("https://docs.google.com/document/d/")) {
                content = content.split("\n")[0];
            }
            body.append(">>> " + content);
            body.append("\n\n- Sent by ").append("<@" + parent.sender + ">").append(" ").append(DiscordUtil.timestamp(parent.date, null)).append("\n");
            message = body.toString();

            if (announcement.active) {
                db.setAnnouncementActive(ann_id, nation.getNation_id(), false);
            }
        }

        io.create().append("## " + title + "\n" + message).send();
        return null;
    }


    @Command(desc = "Mark an announcement by the bot as read/unread")
    @RolePermission(Roles.MEMBER)
    public String readAnnouncement(@Me GuildDB db, @Me DBNation nation, int ann_id, @Default Boolean markRead) {
        if (markRead == null) markRead = true;
        db.setAnnouncementActive(ann_id, nation.getNation_id(), !markRead);
        return "Marked announcement #" + ann_id + " as " + (markRead ? "" : "un") + " read";
    }

    @Command(desc = "Opt out of war room relays and ia channel logging")
    public String optOut(@Me User user, DiscordDB db, @Default("true") boolean optOut) {
        byte[] data = new byte[]{(byte) (optOut ? 1 : 0)};
        db.setInfo(DiscordMeta.OPT_OUT, user.getIdLong(), data);
        if (optOut) {
            for (GuildDB guildDB : Locutus.imp().getGuildDatabases().values()) {
                guildDB.deleteInterviewMessages(user.getIdLong());
            }
        }
        return "Set " + DiscordMeta.OPT_OUT + " to " + optOut;
    }

    public static String handleOptOut(Member member, GuildDB db, Roles lcRole, Boolean forceOptOut) {
        Guild guild = db.getGuild();
        Role role = lcRole.toRole(guild);
        if (role == null) {
            // find role by name
            List<Role> roles = db.getGuild().getRolesByName(lcRole.name(), true);
            if (!roles.isEmpty()) {
                role = roles.get(0);
                db.addRole(lcRole, role, 0);
            } else {
                role = RateLimitUtil.complete(guild.createRole().setName(lcRole.name()));
                db.addRole(lcRole, role, 0);
            }
        }
        if (member.getRoles().contains(role)) {
            if (forceOptOut == Boolean.TRUE) {
                return "You are already opted out of " + lcRole.name() + " alerts";
            }
            RateLimitUtil.complete(guild.removeRoleFromMember(member, role));
            return "Opted back in to " + lcRole.name() + " alerts (@" + role.getName() + " removed from your user). Use the command again to opt out";
        }
        if (forceOptOut == Boolean.FALSE) {
            return "You are already opted in to " + lcRole.name() + " alerts";
        }
        RateLimitUtil.complete(guild.addRoleToMember(member, role));
        return "Opted out of " + lcRole.name() + " alerts (@" + role.getName() + " added to your user). Use the command again to opt back in";
    }

    @Command(desc = "Toggle your opt out of audit alerts")
    @RolePermission(Roles.MEMBER)
    public String auditAlertOptOut(@Me Member member, @Me DBNation me, @Me Guild guild, @Me GuildDB db) {
        return PlayerSettingCommands.handleOptOut(member, db, Roles.AUDIT_ALERT_OPT_OUT, null);
    }

    @Command(desc = "Toggle your opt out of enemy alerts")
    public String enemyAlertOptOut(@Me GuildDB db, @Me User user, @Me Member member, @Me Guild guild) {
        return PlayerSettingCommands.handleOptOut(member, db, Roles.WAR_ALERT_OPT_OUT, null);
    }

    @Command(desc = "Toggle your opt out of bounty alerts")
    public String bountyAlertOptOut(@Me GuildDB db, @Me User user, @Me Member member, @Me Guild guild) {
        return PlayerSettingCommands.handleOptOut(member, db, Roles.BOUNTY_ALERT_OPT_OUT, null);
    }

    @Command(desc = "Set the required transfer market value required for automatic bank alerts\n" +
            "Defaults to $100m, minimum value of 100m")
    @CoalitionPermission(Coalition.RAIDPERMS)
    public String bankAlertRequiredValue(@Me DBNation me,
                                         @Arg("Require the bank transfer to be worth this much\n" +
                                                 "Resources are valued at weekly market average prices")
                                         double requiredValue) {
        if (requiredValue < 100_000_000) {
            throw new IllegalArgumentException("Minimum value is $100m (you entered: `" + MathMan.format(requiredValue) + "`)");
        }
        me.setMeta(NationMeta.BANK_TRANSFER_REQUIRED_AMOUNT, requiredValue);
        return "Set bank alert required value to $" + MathMan.format(requiredValue);
    }

    @Command(desc = "Get an alert when a nation or alliance sends a large bank transfer\n" +
            "Use `*` to subscribe to all nations")
    @WhitelistPermission
    @RolePermission(Roles.MEMBER)
    public String bankAlert(@Me GuildDB db, @Me User author,
                            @Me JSONObject command,
            Set<NationOrAlliance> nation_or_alliances,
                            @ArgChoice({"send", "receive"}) String send_or_receive,
                            long amount,
                            @Timediff long duration) {
        MessageChannel channel = GuildKey.LARGE_TRANSFERS_CHANNEL.get(db);
        boolean isReceive = send_or_receive.equalsIgnoreCase("receive");

        Set<Integer> ids;
        BankDB.BankSubType recType;
        if (command.getString("nation_or_alliances").equals("*")) {
            ids = Collections.singleton(0);
            recType = BankDB.BankSubType.ALL;
        } else {
            ids = nation_or_alliances.stream().map(NationOrAlliance::getId).collect(Collectors.toSet());
            boolean hasNation = nation_or_alliances.stream().anyMatch(NationOrAlliance::isNation);
            boolean hasAlliance = nation_or_alliances.stream().anyMatch(NationOrAlliance::isAlliance);
            if (hasNation && hasAlliance) {
                throw new IllegalArgumentException("Cannot mix nations and alliances");
            }
            recType = hasNation ? BankDB.BankSubType.NATION : BankDB.BankSubType.ALLIANCE;
        }
        if (ids.size() > 15) {
            throw new IllegalArgumentException("Too many nations/alliances (max: 15, provided: " + ids.size() + ")");
        }
        long cutoff = System.currentTimeMillis() + duration;
        for (int id : ids) {
            Locutus.imp().getBankDB().subscribe(author, id, recType, cutoff, isReceive, amount);
        }
        return "Subscribed to `" + command + "` in " + channel.getAsMention() +
                "\nCheck your subscriptions with: " + CM.alerts.bank.list.cmd.toSlashMention();
    }

    @Command(desc = "List your subscriptions to large bank transfers")
    @WhitelistPermission
    @RolePermission(Roles.MEMBER)
    public String bankAlertList(@Me User author,
                                @Me GuildDB db, @Me IMessageIO io) {
        GuildKey.LARGE_TRANSFERS_CHANNEL.get(db);
        Set<BankDB.Subscription> subscriptions = Locutus.imp().getBankDB().getSubscriptions(author.getIdLong());
        if (subscriptions.isEmpty()) {
            return "No subscriptions. Subscribe to get alerts using " + CM.alerts.bank.subscribe.cmd.toSlashMention();
        }

        for (BankDB.Subscription sub : subscriptions) {
            String name;
            String url;
            if (sub.allianceOrNation == 0) {
                name = "*";
                if (sub.type == BankDB.BankSubType.ALL) {
                    url = name;
                } else {
                    String type = sub.type == BankDB.BankSubType.NATION ? "nation" : "alliance";
                    url = "" + Settings.INSTANCE.PNW_URL() + "/" + type + "/id=" + sub.allianceOrNation;
                }
            } else if (sub.type == BankDB.BankSubType.NATION) {
                DBNation nation = Locutus.imp().getNationDB().getNation(sub.allianceOrNation);
                url = "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + sub.allianceOrNation;
                name = String.format("[%s](%s)",
                        nation == null ? sub.allianceOrNation : nation.getNation(), url);
            } else {
                String aaName = Locutus.imp().getNationDB().getAllianceName(sub.allianceOrNation);
                if (aaName == null) aaName = sub.allianceOrNation + "";
                url = "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + sub.allianceOrNation;
                name = String.format("[%s](%s)",
                        aaName, url);
            }
            String dateStr = TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(sub.endDate)) + " (UTC)";
            String sendReceive = sub.isReceive ? "received" : "deposited";

            String title = name + " " + sendReceive + " > " + MathMan.format(sub.amount);

            String body = "Expires " + dateStr;

            String emoji = "Unsubscribe";
            CM.alerts.bank.unsubscribe unsubCommand = CM.alerts.bank.unsubscribe.cmd.nation_or_alliances(url);

            io.create().embed(title, body)
                    .commandButton(unsubCommand, emoji).send();
        }

        if (subscriptions.isEmpty()) {
            return "No subscriptions";
        }
        return null;
    }

    @Command(desc = "Remove your subscriptions to large bank transfers")
    @WhitelistPermission
    @RolePermission(Roles.MEMBER)
    public String bankAlertUnsubscribe(@Me GuildDB db, @Me JSONObject command, @Me User author,
                                       Set<NationOrAlliance> nation_or_alliances) {
        GuildKey.LARGE_TRANSFERS_CHANNEL.get(db);
        BankDB bankDb = Locutus.imp().getBankDB();
        if (command.getString("nation_or_alliances").equals("*")) {
            bankDb.unsubscribe(author, 0, BankDB.BankSubType.ALL);
            bankDb.unsubscribeAll(author.getIdLong());
            return "Unsubscribed from ALL bank alerts";
        }
        // get subscriptions
        Set<Integer> aaIds = nation_or_alliances.stream().filter(f -> f.isAlliance()).map(f -> f.getId()).collect(Collectors.toSet());
        Set<Integer> nationIds = nation_or_alliances.stream().filter(f -> f.isNation()).map(f -> f.getId()).collect(Collectors.toSet());
        Set<BankDB.Subscription> subscriptions = bankDb.getSubscriptions(author.getIdLong());
        int numUnsubscribed = 0;
        for (BankDB.Subscription sub : subscriptions) {
            if (sub.allianceOrNation == 0) continue;
            if (sub.type == BankDB.BankSubType.NATION) {
                if (nationIds.contains(sub.allianceOrNation)) {
                    bankDb.unsubscribe(author, sub.allianceOrNation, BankDB.BankSubType.NATION);
                    numUnsubscribed++;
                }
            } else {
                if (aaIds.contains(sub.allianceOrNation)) {
                    bankDb.unsubscribe(author, sub.allianceOrNation, BankDB.BankSubType.ALLIANCE);
                    numUnsubscribed++;
                }
            }
        }
        if (numUnsubscribed == 0) {
            return "No subscriptions found matching the provided nations/alliances. See " + CM.alerts.bank.list.cmd.toSlashMention();
        }
        return "Unsubscribed from `" + numUnsubscribed + "`" + " alerts";
    }
}
