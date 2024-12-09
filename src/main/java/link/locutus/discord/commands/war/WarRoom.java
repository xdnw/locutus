package link.locutus.discord.commands.war;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.user.Roles;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.google.gson.internal.$Gson$Preconditions.checkNotNull;
import static link.locutus.discord.commands.war.WarRoomUtil.getRangeFromCategory;
import static link.locutus.discord.util.discord.DiscordUtil.setSymbol;

public class WarRoom {
    protected final WarCategory warCategory;
    public final DBNation target;
    protected final Set<DBNation> participants;
    public boolean planning;
    public boolean enemyGc;
    public boolean enemyAc;
    public boolean enemyBlockade;

    public long channelId;
    public StandardGuildMessageChannel channel;

    public WarRoom(WarCategory warCategory, DBNation target, WarCatReason reason) {
        this.warCategory = warCategory;
        checkNotNull(target);
        this.target = target;
        this.participants = new HashSet<>();
        loadParticipants(false);
        MessageChannel logChan = reason.isExisting() ? null : GuildKey.WAR_ROOM_LOG.getOrNull(warCategory.getGuildDb());
        if (logChan != null) {
            String msg = "Creating war room for " + target.getMarkdownUrl() + " due to " + reason.name() + ": " + reason.getReason();
            RateLimitUtil.queueMessage(logChan, msg, true, 60);
        }
    }

    public synchronized WarRoom addChannel(long channelId, StandardGuildMessageChannel channel, WarCatReason reason) {
        if (this.channelId != channelId) {
            MessageChannel logChan = reason.isExisting() ? null : GuildKey.WAR_ROOM_LOG.getOrNull(warCategory.getGuildDb());
            if (logChan != null) {
                String msg = "Adding channel " + channel.getAsMention() + " to " + target.getMarkdownUrl() + " due to " + reason.name() + ": " + reason.getReason();
                RateLimitUtil.queueMessage(logChan, msg, true, 60);
            }

            this.channelId = channelId;
            StandardGuildMessageChannel oldChannel = this.channel;
            this.channel = channel;
            if (oldChannel != null) {
                if (logChan != null) {
                    String msg = "Deleting old channel " + oldChannel.getAsMention() + " for " + target.getMarkdownUrl();
                    RateLimitUtil.queueMessage(logChan, msg, true, 60);
                }
                DiscordUtil.deleteChannelSafe(oldChannel);
                warCategory.getGuildDb().deleteWarRoomChannelCache(Set.of(oldChannel.getIdLong()));
            }
            warCategory.getGuildDb().addWarRoomCache(target.getId(), channel.getIdLong());
        } else if (this.channel != channel) {
            this.channel = channel;
        }
        return this;
    }

    private void loadParticipants(boolean force) {
        if (force) {
            addInitialParticipants(false);
        }
        if (channel != null) {
            for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
                Member member = override.getMember();
                if (member == null) continue;
                DBNation nation = DiscordUtil.getNation(member.getUser());
                if (nation != null) {
                    participants.add(nation);
                }
            }
        }
    }

    public String url() {
        if (channel == null) return null;
        return "https://discord.com/channels/" + warCategory.getGuild().getIdLong() + "/" + channel.getIdLong();
    }

    public IMessageBuilder updatePin(boolean update) {
        return WarRoomUtil.updatePin(channel, target, participants, update);
    }

    public boolean setGC(boolean value) {
        if (value == enemyGc) return false;
        enemyGc = value;
        return setSymbol(channel,"\uD83D\uDC82", value);
    }

    public boolean isGC() {
        if (channel != null) return channel.getName().contains("\uD83D\uDC82");
        return false;
    }

    public boolean setAC(boolean value) {
        if (value == enemyAc) return false;
        enemyAc = value;
        return setSymbol(channel, "\u2708", value);
    }

    public boolean isAC() {
        if (channel != null) return channel.getName().contains("\u2708");
        return false;
    }

    public boolean setBlockade(boolean value) {
        if (enemyBlockade == value) return false;
        enemyBlockade = value;
        return setSymbol(channel, "\u26F5", value);
    }

    public boolean isBlockade() {
        if (channel != null) return channel.getName().contains("\u26F5");
        return false;
    }

    public boolean setPlanning(boolean value) {
        if (value == planning) return false;
        planning = value;
        return WarRoomUtil.setPlanning(channel, value);
    }

    public boolean isPlanning() {
        if (channel != null) return WarRoomUtil.isPlanning(channel);
        return false;
    }

    public StandardGuildMessageChannel getChannel() {
        synchronized (this) {
            if (channel == null) {
                channel = warCategory.getGuild().getTextChannelById(channelId);
            }
            if (channel != null) return channel;
        }
        return channel;
    }

    public boolean isChannelValid() {
        if (channel == null) {
            if (channelId != 0) {
                synchronized (this) {
                    if (channel != null) return true;
                    channel = warCategory.getGuild().getTextChannelById(channelId);
                }
                return channel != null;

            }
            return false;
        }
        TextChannel newChannel = this.warCategory.getGuild().getTextChannelById(channelId);
        this.channel = newChannel;
        return newChannel != null;
    }

    public boolean hasChannel() {
        return channel != null;
    }

    public void setChannel(TextChannel channel) {
        this.channel = channel;
        planning = isPlanning();
        enemyGc = isGC();
        enemyAc = isAC();
        enemyBlockade = isBlockade();
    }

    public void handleDelete(String reason) {
        if (channel != null) {
            MessageChannel logChan = GuildKey.WAR_ROOM_LOG.getOrNull(warCategory.getGuildDb());
            if (logChan != null) {
                String msg = "Deleting channel " + channel.getAsMention() + " for " + target.getMarkdownUrl() + " due to " + reason;
                RateLimitUtil.queueMessage(logChan, msg, true, 60);
            }
            DiscordUtil.deleteChannelSafe(channel);
            channel = null;
        }
    }

    public void updateParticipants(DBWar from, DBWar to) {
        updateParticipants(from, to, false);
    }

    public void updateParticipants(DBWar from, DBWar to, boolean ping) {
        int assignedId = to.getAttacker_id() == target.getNation_id() ? to.getDefender_id() : to.getAttacker_id();
        DBNation nation = Locutus.imp().getNationDB().getNationById(assignedId);
        if (nation == null) return;

        User user = nation.getUser();
        Member member = user == null ? null : warCategory.getGuild().getMember(user);
        if (from == null) {
            participants.add(nation);

            if (channel != null && member != null && channel.getPermissionOverride(member) == null) {
                RateLimitUtil.queue(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL));
                if (ping && channel != null) {
                    String msg = member.getAsMention() + " joined the fray";
                    RateLimitUtil.queue(channel.sendMessage(msg));
                }
            }
        } else if (channel != null && (to.getStatus() == WarStatus.EXPIRED || to.getStatus() == WarStatus.ATTACKER_VICTORY || to.getStatus() == WarStatus.DEFENDER_VICTORY)) {
            participants.remove(nation);

            if (member != null) {
                PermissionOverride override = channel.getPermissionOverride(member);
                if (override != null) {
                    RateLimitUtil.queue(override.delete());
                }
            }
        }
    }

    public void addInitialParticipants(boolean planning) {
        addInitialParticipants(target.getActiveWars(), planning);
    }

    public void addInitialParticipants(Collection<DBWar> wars) {
        addInitialParticipants(wars, false);
    }

    public void addInitialParticipants(Collection<DBWar> wars, boolean planning) {
        boolean planned = planning || isPlanning();
        if (!planned && wars.isEmpty()) {
            if (channel != null) {
                warCategory.deleteRoom(this, "No wars");
            }
            return;
        }

        Member botMember = warCategory.getGuild().getMemberById(Settings.INSTANCE.APPLICATION_ID);
        if (botMember != null && channel != null && channel.getPermissionOverride(botMember) == null) {
            RateLimitUtil.queue(channel.upsertPermissionOverride(botMember)
                    .grant(Permission.VIEW_CHANNEL)
                    .grant(Permission.MANAGE_CHANNEL)
                    .grant(Permission.MANAGE_PERMISSIONS)
            );
        }

        Set<DBNation> added = new HashSet<>();
        Set<Member> addedMembers = new HashSet<>();

        for (DBWar war : wars) {
            DBNation other = war.getNation(!war.isAttacker(target));
            if (other == null) continue;

            added.add(other);
            participants.add(other);

            User user = other.getUser();
            Member member = user == null ? null : warCategory.getGuild().getMember(user);

            if (member != null) {
                addedMembers.add(member);
            }
            if (channel != null && member != null && channel.getPermissionOverride(member) == null) {
                RateLimitUtil.queue(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL));
            }
        }
        if (!planned && channel != null) {
            for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
                Member member = override.getMember();
                if (member == null) continue;
                DBNation nation = DiscordUtil.getNation(member.getUser());
                if (!added.contains(nation) && !addedMembers.contains(member)) {
                    RateLimitUtil.queue(override.delete());
                }
            }
        }
    }

    public boolean isParticipant(DBNation nation, boolean forceUpdate) {
        if (forceUpdate) {
            addInitialParticipants(false);
        }
        return participants.contains(nation);
    }

    protected boolean hasOtherWar(NationFilter filter, int warId) {
        boolean hasWar = false;
        for (DBWar war : target.getWars()) {
            if (war.getWarId() == warId) continue;
            DBNation other = war.getNation(!war.isAttacker(target));
            if (other != null && warCategory.isActive(filter, other) && warCategory.getTrackedAllianceIds().contains(other.getAlliance_id())) {
                hasWar = true;
                break;
            }
        }
        return hasWar;
    }

    public Set<DBNation> getParticipants() {
        return participants;
    }

    public String getChannelMention() {
        return "<#" + channelId + ">";
    }
}
