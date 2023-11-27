package link.locutus.discord.db.entities.newsletter;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class Newsletter {
    private int id;
    private final String name;
    private long date_created;
    private long lastSent;
    private long sendInterval;
    private final long sendConfirmationChannel;
    private long pingRole;
    private Set<Long> channelIds = new ObjectOpenHashSet<>();

    public Newsletter(int id, String name, long date_created, long lastSent, long sendInterval, long sendConfirmationChannel, long pingRole) {
        checkNotNull(name, "name");
        this.id = id;
        this.name = name;
        this.date_created = date_created;
        this.lastSent = lastSent;
        this.sendInterval = sendInterval;
        this.sendConfirmationChannel = sendConfirmationChannel;
        this.pingRole = pingRole;
    }

    @Override
    public String toString() {
        StringBuilder body = new StringBuilder();
        body.append("ID: `#").append(getId()).append("`\n");
        body.append("Created: ").append(DiscordUtil.timestamp(getDateCreated(), null)).append("\n");
        if (getLastSent() == 0) {
            body.append("Last sent: Never\n");
        } else {
            body.append("Last sent: ").append(DiscordUtil.timestamp(getLastSent(), null)).append("\n");
        }
        if (sendConfirmationChannel == 0) {
            body.append("Send interval: Disabled\n");
        } else {
            body.append("Send interval: ").append(DiscordUtil.timestamp(getSendInterval(), null)).append("\n");
            body.append("Confirmation channel: <#").append(getSendConfirmationChannel()).append(">\n");
            body.append("Ping role: <@&").append(getPingRole()).append(">\n");
        }
        body.append("Channels: ").append(getChannelIds().size()).append("\n");
        for (Long channelId : getChannelIds()) {
            body.append("- <#").append(channelId).append(">\n");
        }
        return body.toString();
    }

    public void setLastSent(long lastSent) {
        this.lastSent = lastSent;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getDateCreated() {
        return date_created;
    }

    public long getLastSent() {
        return lastSent;
    }

    public long getSendInterval() {
        return sendInterval;
    }

    public long getSendConfirmationChannel() {
        return sendConfirmationChannel;
    }

    public long getPingRole() {
        return pingRole;
    }

    public Set<Long> getChannelIds() {
        return channelIds;
    }

    public void setChannelIds(Set<Long> channelIds) {
        this.channelIds = channelIds;
    }

    public void addChannelId(long channelId) {
        this.channelIds.add(channelId);
    }

    public void setId(int id) {
        this.id = id;
    }

    public void removeChannelId(long id) {
        this.channelIds.remove(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Newsletter n) {
            return n.id == this.id;
        }
        return false;
    }

    public void setSendInterval(long interval) {
        this.sendInterval = interval;
    }

    public void setPingRole(long roleId) {
        this.pingRole = roleId;
    }
}
