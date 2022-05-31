package com.boydti.discord.pnw;

import com.boydti.discord.Locutus;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.util.FileUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.offshore.Auth;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.TargetType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class PNWUser {
    private int nationId;
    private Long discordId;
    private String discordName;

    public PNWUser(int nationId, Long discordId, String discordName) {
        this.nationId = nationId;
        this.discordId = discordId;
        this.discordName = discordName;
    }

    public User getUser() {
        if (discordId != null) {
            User user = Locutus.imp().getDiscordApi().getUserById(discordId);
            if (user != null && !discordName.contains("#")) {
                discordName = user.getName() + "#" + user.getDiscriminator();
                Locutus.imp().getDiscordDB().addUser(this);
            }
            return user;
        }
        User user;
        if (discordName.contains("#")) {
            String[] split = discordName.split("#");
            user = Locutus.imp().getDiscordApi().getUserByTag(split[0], split[1]);
        } else {
            List<User> users = Locutus.imp().getDiscordApi().getUsersByName(discordName, true);
            if (users.size() != 1) {
                return null;
            }
            user = users.get(0);
        }
        if (user != null) {
            this.discordId = user.getIdLong();
            this.discordName = user.getName() + "#" + user.getDiscriminator();
            Locutus.imp().getDiscordDB().addUser(this);
        }
        return user;
    }

    public Map<Guild, Collection<Map.Entry<ActionType, String>>> getKicksAndBans() {
        if (getDiscordId() == null) throw new IllegalArgumentException("Not discord id");

        Map<Guild, Collection<Map.Entry<ActionType, String>>> map = new HashMap<>();
        Queue<Future<?>> tasks = new ArrayDeque<>();

        for (GuildDB guildDb : Locutus.imp().getGuildDatabases().values()) {
            Guild guild = guildDb.getGuild();
            if (guild == null) continue;
            try {
                AuditLogPaginationAction logs = guild.retrieveAuditLogs();

                Future<?> task = Locutus.imp().getExecutor().submit(new Runnable() {
                    @Override
                    public void run() {
                        List<Map.Entry<ActionType, String>> guildResult = new ArrayList<>();

                        AuditLogPaginationAction kicks = logs.type(ActionType.KICK).user(getDiscordId()).limit(1);
                        for (AuditLogEntry kick : kicks) {
                            if (kick.getTargetType() != TargetType.MEMBER) continue;

                            if (kick.getTargetIdLong() == getDiscordId()) {
                                guildResult.add(new AbstractMap.SimpleEntry<>(ActionType.KICK, kick.getReason()));
                            }
                        }

                        AuditLogPaginationAction bans = logs.type(ActionType.BAN).user(getDiscordId()).limit(1);
                        for (AuditLogEntry ban : bans) {
                            if (ban.getTargetType() != TargetType.MEMBER) continue;

                            if (ban.getTargetIdLong() == getDiscordId()) {
                                guildResult.add(new AbstractMap.SimpleEntry<>(ActionType.BAN, ban.getReason()));
                            }
                        }
                    }
                });
                tasks.add(task);
            } catch (InsufficientPermissionException ignore) {}
        }
        while (!tasks.isEmpty()) {
            try {
                tasks.poll().get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        return map;
    }

    public int getNationId() {
        return nationId;
    }

    public void setNationId(int nationId) {
        this.nationId = nationId;
    }

    public Long getDiscordId() {
        return discordId;
    }

    public void setDiscordId(Long discordId) {
        this.discordId = discordId;
    }

    public String getDiscordName() {
        if (discordId != null) {
            User user = Locutus.imp().getDiscordApi().getUserById(discordId);
            if (user != null) {
                discordName = user.getName() + "#" + user.getDiscriminator();
            }
        }
        return discordName;
    }

    public void setDiscordName(String discordName) {
        this.discordName = discordName;
    }

    public CharSequence getAsMention() {
        return "<@" + discordId + ">";
    }
}
