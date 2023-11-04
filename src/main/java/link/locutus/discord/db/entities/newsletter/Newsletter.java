package link.locutus.discord.db.entities.newsletter;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Set;

public class Newsletter {
    private int id;
    private final String name;
    private long date_created;
    private final long lastSent;
    private long sendInterval;
    private final long sendConfirmationChannel;
    private long pingRole;
    private Set<Long> channelIds = new ObjectOpenHashSet<>();

    public Newsletter(int id, String name, long date_created, long lastSent, long sendInterval, long sendConfirmationChannel, long pingRole) {
        this.id = id;
        this.name = name;
        this.date_created = date_created;
        this.lastSent = lastSent;
        this.sendInterval = sendInterval;
        this.sendConfirmationChannel = sendConfirmationChannel;
        this.pingRole = pingRole;
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
