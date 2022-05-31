package com.boydti.discord.user;

import com.boydti.discord.Locutus;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.util.StringMan;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Locale;

public enum Roles {
    REGISTERED("auto role for anyone who is verified with the bot"),
    MEMBER("Members can run commands"),
    ADMIN("Admin has access to alliance / guild management commands"),

    MILCOM("Access to milcom related commands", GuildDB.Key.ALLIANCE_ID) {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return MILCOM_ADVISOR.has(member);
        }
    },
    MILCOM_ADVISOR("Access to milcom related commands - doesn't receive pings", GuildDB.Key.ALLIANCE_ID),
    ENEMY_BEIGE_ALERT_AUDITOR("Role to receive pings when an enemy gets beiged", GuildDB.Key.ENEMY_BEIGED_ALERT_VIOLATIONS),

    ECON("Has access to econ gov commands", GuildDB.Key.ALLIANCE_ID),
    ECON_LOW_GOV("Has access to basic econ commands", GuildDB.Key.ALLIANCE_ID) {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return ECON.has(member);
        }

        @Override
        public Role toRole(Guild guild) {
            Role value = super.toRole(guild);
            if (value != null) return value;
            return ECON.toRole(guild);
        }
    },

    ECON_GRANT_ALERTS("Gets pinged for member grant requests", GuildDB.Key.ALLIANCE_ID),
    ECON_DEPOSIT_ALERTS("Gets pinged when there is a deposit", GuildDB.Key.ALLIANCE_ID),
    ECON_WITHDRAW_ALERTS("Gets pinged when there is a withdrawal", GuildDB.Key.ALLIANCE_ID),
    ECON_WITHDRAW_SELF("Can withdraw own funds", GuildDB.Key.MEMBER_CAN_WITHDRAW),
    ECON_GRANT_SELF("Role to allow member to grant themselves", GuildDB.Key.MEMBER_CAN_WITHDRAW),

    FOREIGN_AFFAIRS("Role required to see other alliance's embassy channel", GuildDB.Key.ALLIANCE_ID),
    FOREIGN_AFFAIRS_STAFF("Role for some basic FA commands", GuildDB.Key.ALLIANCE_ID) {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return FOREIGN_AFFAIRS.has(member);
        }

        @Override
        public Role toRole(Guild guild) {
            Role value = super.toRole(guild);
            if (value != null) return value;
            return FOREIGN_AFFAIRS.toRole(guild);
        }
    },

    INTERNAL_AFFAIRS("Access to IA related commands", GuildDB.Key.ALLIANCE_ID),
    INTERNAL_AFFAIRS_STAFF("Role for some basic IA commands", GuildDB.Key.ALLIANCE_ID) {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return INTERNAL_AFFAIRS.has(member);
        }

        @Override
        public Role toRole(Guild guild) {
            Role value = super.toRole(guild);
            if (value != null) return value;
            return INTERNAL_AFFAIRS.toRole(guild);
        }
    },

    APPLICANT("Applying to join the alliance (this role doesn't grant any elevated permissions)", GuildDB.Key.INTERVIEW_PENDING_ALERTS),
    INTERVIEWER("Role to get pinged when a user requests an interview", GuildDB.Key.INTERVIEW_PENDING_ALERTS),
    MENTOR("Role to get pinged when a user requests mentoring (can be same as interviewer)", GuildDB.Key.INTERVIEW_PENDING_ALERTS),
    GRADUATED("Members with this role will have their interview channels archived", GuildDB.Key.INTERVIEW_PENDING_ALERTS) {
        @Override
        public boolean has(Member member) {
            return super.has(member)
                    || Roles.MILCOM.has(member)
                    || Roles.MILCOM_ADVISOR.has(member)
                    || Roles.ECON.has(member)
                    || Roles.ECON_LOW_GOV.has(member)
                    || Roles.FOREIGN_AFFAIRS.has(member)
                    || Roles.FOREIGN_AFFAIRS_STAFF.has(member)
                    || Roles.INTERNAL_AFFAIRS.has(member)
                    || Roles.INTERNAL_AFFAIRS_STAFF.has(member)
                    || Roles.INTERVIEWER.has(member)
                    || Roles.MENTOR.has(member)
                    || Roles.RECRUITER.has(member)
                    ;
        }
    },
    RECRUITER("Role to get pinged for recruitment messages (if enabled)", GuildDB.Key.RECRUIT_MESSAGE_OUTPUT),

    TRADE_ALERT("Gets pinged for trade alerts", GuildDB.Key.TRADE_ALERT_CHANNEL),

    BEIGE_ALERT("Gets pinged when a nation leaves beige (in their score range), and they have a slot free", GuildDB.Key.BEIGE_ALERT_CHANNEL),
    BEIGE_ALERT_OPT_OUT("Overrides the beige alert role", GuildDB.Key.BEIGE_ALERT_CHANNEL),

    BOUNTY_ALERT("Gets pings when bounties are placed in their score range"),
//    MAP_FULL_ALERT("Gets pinged when you are on 12 MAPs in an offensive war", GuildDB.Key.MEMBER_AUDIT_ALERTS),

//    WAR_ALERT("Opt out of received war target alerts", GuildDB.Key.ENEMY_ALERT_CHANNEL),
    WAR_ALERT_OPT_OUT("Opt out of received war target alerts", GuildDB.Key.ENEMY_ALERT_CHANNEL),
    AUDIT_ALERT_OPT_OUT("Opt out of received audit alerts", GuildDB.Key.MEMBER_AUDIT_ALERTS),

    BLITZ_PARTICIPANT("Opt in to blitz participation (clear this regularly)", GuildDB.Key.ALLIANCE_ID),
    BLITZ_PARTICIPANT_OPT_OUT("Opt in to blitz participation (clear this regularly)", GuildDB.Key.ALLIANCE_ID),

    TEMP("Role to signify temporary member", GuildDB.Key.ALLIANCE_ID),
//    ACTIVE("Role to signify active member", GuildDB.Key.ALLIANCE_ID)

    MAIL("Can use mail commands", GuildDB.Key.API_KEY),

    BLOCKADED_ALERTS("Gets alerts when you are blockaded", GuildDB.Key.BLOCKADED_ALERTS),
    UNBLOCKADED_ALERTS("Gets alerts when you are unblockaded", GuildDB.Key.UNBLOCKADED_ALERTS)
    ;

    public static Roles[] values = values();
    private final String desc;
    private final GuildDB.Key key;

    public static Roles getHighestRole(Member member) {
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i].has(member)) {
                return values[i];
            }
        }
        return null;
    }

    public static String getValidRolesStringList() {
        return "\n - " + StringMan.join(Roles.values(), "\n - ");
    }
    Roles(String desc) {
        this(desc, null);
    }


    Roles(String desc, GuildDB.Key key) {
        this.desc = desc;
        this.key = key;
    }

    public GuildDB.Key getKey() {
        return key;
    }

    @Override
    public String toString() {
        return name() + ": `" + desc + "`";
    }

    public Role toRole(MessageReceivedEvent event) {
        return toRole(event.isFromGuild() ? event.getGuild() : Locutus.imp().getServer());
    }

    public Role toRole(Guild guild) {
        if (guild == null) return null;
        Long alias = Locutus.imp().getGuildDB(guild).getRoleAlias(this);
        if (alias == null) {
            List<Role> roles = guild.getRolesByName(this.name(), true);
            if (!roles.isEmpty()) {
                return roles.get(0);
            }
        } else {
            return guild.getRoleById(alias);
        }
        return null;
    }

    public boolean hasOnRoot(User user) {
        if (user.getIdLong() == Settings.INSTANCE.ADMIN_USER_ID) return true;
        if (Locutus.imp().getServer() == null) {
            return false;
        }
        return has(user, Locutus.imp().getServer());
    }

    public boolean has(Member member) {
        if (member == null) return false;
        if (member.getIdLong() == Settings.INSTANCE.APPLICATION_ID) return true;

        if (member.isOwner()) return true;
        Role role = toRole(member.getGuild());
        for (Role discordRole : member.getRoles()) {
            if (discordRole.hasPermission(Permission.ADMINISTRATOR)) {
                return true;
            }
        }
        return role != null && member.getRoles().contains(role);
    }

    public static boolean hasAny(User user, Guild guild, Roles... roles) {
        for (Roles role : roles) {
            if (role.has(user, guild)) return true;
        }
        return false;
    }

    public static Roles parse(String role) {
        try {
            return Roles.valueOf(role.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException  e) {
            return null;
        }
    }

    public boolean has(User user, Guild server) {
        if (user == null) return false;
        if (user.getIdLong() == Settings.INSTANCE.APPLICATION_ID) return true;
        if (user.getIdLong() == Settings.INSTANCE.ADMIN_USER_ID) return true;
        if (server == null) return false;
        if (!server.isMember(user)) {
            return false;
        }
        return has(server.getMember(user));

    }
}
