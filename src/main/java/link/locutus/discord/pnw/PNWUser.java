package link.locutus.discord.pnw;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.TargetType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction;

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class PNWUser {
    private int nationId;
    private long discordId;
    private String discordName;

    public PNWUser(int nationId, long discordId, String discordName) {
        this.nationId = nationId;
        this.discordId = discordId;
        this.discordName = discordName;
    }

    public User getUser() {
        User user = Locutus.imp().getDiscordApi().getUserById(discordId);
        if (user != null && !discordName.contains("#")) {
            discordName = DiscordUtil.getFullUsername(user);
            Locutus.imp().getDiscordDB().addUser(this);
        }
        return user;
    }

    public Map<Guild, Collection<Map.Entry<ActionType, String>>> getKicksAndBans() {
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

                        AuditLogPaginationAction kicks = logs.type(ActionType.KICK).user(getUser()).limit(1);
                        for (AuditLogEntry kick : kicks) {
                            if (kick.getTargetType() != TargetType.MEMBER) continue;

                            if (kick.getTargetIdLong() == getDiscordId()) {
                                guildResult.add(new AbstractMap.SimpleEntry<>(ActionType.KICK, kick.getReason()));
                            }
                        }

                        AuditLogPaginationAction bans = logs.type(ActionType.BAN).user(getUser()).limit(1);
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

    public long getDiscordId() {
        return discordId;
    }

    public void setDiscordId(Long discordId) {
        this.discordId = discordId;
    }

    public String getDiscordName() {
        User user = Locutus.imp().getDiscordApi().getUserById(discordId);
        if (user != null) {
            discordName = DiscordUtil.getFullUsername(user);
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
