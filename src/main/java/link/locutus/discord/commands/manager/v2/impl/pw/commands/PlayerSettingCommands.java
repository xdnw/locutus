package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordHookIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasApi;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Announcement;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

import java.util.HashMap;
import java.util.Map;

public class PlayerSettingCommands {

    @Command(desc = "View an announcement you have access to")
    @RolePermission(Roles.MEMBER)
    public String viewAnnouncement(@Me IMessageIO io, @Me GuildDB db, @Me DBNation nation, @Me User user, int ann_id) {
        Announcement parent = db.getAnnouncement(ann_id);
        Announcement.PlayerAnnouncement announcement = db.getPlayerAnnouncement(ann_id, nation.getNation_id());
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

    @Command(desc = "Opt out of audit alerts")
    @RolePermission(Roles.MEMBER)
    public String auditAlertOptOut(@Me Member member, @Me DBNation me, @Me Guild guild, @Me GuildDB db) {
        Role role = Roles.AUDIT_ALERT_OPT_OUT.toRole(guild);
        if (role == null) {
            role = RateLimitUtil.complete(guild.createRole().setName(Roles.AUDIT_ALERT_OPT_OUT.name()));
            db.addRole(Roles.AUDIT_ALERT_OPT_OUT, role, 0);
        }
        RateLimitUtil.queue(guild.addRoleToMember(member, role));
        return "Opted out of audit alerts";
    }

    @Command
    public String enemyAlertOptOut(@Me GuildDB db, @Me User user, @Me Member member, @Me Guild guild) {
        Role role = Roles.WAR_ALERT_OPT_OUT.toRole(guild);
        if (role == null) {
            role = RateLimitUtil.complete(guild.createRole().setName(Roles.WAR_ALERT_OPT_OUT.name()));
            db.addRole(Roles.WAR_ALERT_OPT_OUT, role, 0);
        }
        if (member.getRoles().contains(role)) {
            return "You are already opted out with the role for " + Roles.WAR_ALERT_OPT_OUT.name();
        }
        RateLimitUtil.complete(guild.addRoleToMember(member, role));
        return "You have been opted out of " + Roles.WAR_ALERT_OPT_OUT.name() + " alerts";
    }

}
