package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.Alliance;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.util.offshore.OffshoreInstance;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class LiveDBAlliance extends DBAlliance {
    private String forumLink;
    private String discordLink;
    private String wikiLink;
    private volatile long lastUpdated;
    private OffshoreInstance bank;
    private LootEntry lootEntry;
    private boolean cachedLootEntry;
    private Int2ObjectOpenHashMap<byte[]> metaCache;
    private int treasureCount = -1;

    public LiveDBAlliance(Alliance alliance) {
        super(alliance);
        this.forumLink = "";
        this.discordLink = "";
        this.wikiLink = "";
    }

    public LiveDBAlliance(DBAlliance other) {
        super(other);
        this.forumLink = other.getForum_link();
        this.discordLink = other.getDiscord_link();
        this.wikiLink = other.getWiki_link();
        this.lastUpdated = other.getLastUpdated();
        if (other instanceof LiveDBAlliance live) {
            this.bank = live.bank;
            this.lootEntry = live.lootEntry;
            this.cachedLootEntry = live.cachedLootEntry;
            this.metaCache = live.metaCache;
            this.treasureCount = live.treasureCount;
        }
    }

    public LiveDBAlliance(int allianceId, String name, String acronym, String flag, String forumLink,
            String discordLink, String wikiLink, long dateCreated, NationColor color,
            Int2ObjectOpenHashMap<byte[]> metaCache) {
        super(allianceId, name, acronym, flag, dateCreated, color);
        this.forumLink = forumLink;
        this.discordLink = discordLink;
        this.wikiLink = wikiLink;
        this.metaCache = metaCache;
    }

    @Override
    protected DBAlliance copyForChangeTracking() {
        return new LiveDBAlliance(this);
    }

    @Override
    protected void markAllianceUpdated(long timestamp) {
        this.lastUpdated = timestamp;
    }

    @Override
    protected void applyForumLink(String forumLink) {
        this.forumLink = forumLink;
    }

    @Override
    protected void applyDiscordLink(String discordLink) {
        this.discordLink = discordLink;
    }

    @Override
    protected void applyWikiLink(String wikiLink) {
        this.wikiLink = wikiLink;
    }

    @Override
    public int getNumTreasures() {
        if (getAlliance_id() == 0) {
            return 0;
        }
        if (treasureCount >= 0) {
            return treasureCount;
        }
        return treasureCount = Locutus.imp().getNationDB().countTreasures(getAlliance_id());
    }

    @Override
    public void markTreasuresDirty() {
        treasureCount = -1;
    }

    @Override
    public void setLoot(LootEntry lootEntry) {
        this.lootEntry = lootEntry;
        this.cachedLootEntry = true;
    }

    @Override
    public LootEntry getLoot() {
        if (cachedLootEntry) {
            return lootEntry;
        }
        if (lootEntry == null) {
            lootEntry = Locutus.imp().getNationDB().getAllianceLoot(getAlliance_id());
            cachedLootEntry = true;
        }
        return lootEntry;
    }

    @Override
    public String getForum_link() {
        return forumLink == null ? "" : forumLink;
    }

    @Override
    public String getDiscord_link() {
        return discordLink == null ? "" : discordLink;
    }

    @Override
    public String getWiki_link() {
        return wikiLink == null ? "" : wikiLink;
    }

    @Override
    public long getLastUpdated() {
        return lastUpdated;
    }

    @Override
    public void deleteMeta(AllianceMeta key) {
        if (metaCache != null && metaCache.remove(key.ordinal()) != null) {
            Locutus.imp().getNationDB().deleteMeta(-getAlliance_id(), key.ordinal());
        }
    }

    @Override
    public boolean setMetaRaw(int id, byte[] value) {
        if (metaCache == null) {
            synchronized (this) {
                if (metaCache == null) {
                    metaCache = new Int2ObjectOpenHashMap<>();
                }
            }
        }
        byte[] existing = metaCache.isEmpty() ? null : metaCache.get(id);
        if (existing == null || !Arrays.equals(existing, value)) {
            metaCache.put(id, value);
            return true;
        }
        return false;
    }

    @Override
    public ByteBuffer getMeta(AllianceMeta key) {
        if (metaCache == null) {
            return null;
        }
        byte[] result = metaCache.get(key.ordinal());
        return result == null ? null : ByteBuffer.wrap(result);
    }

    @Override
    public OffshoreInstance getBank() {
        if (bank == null) {
            synchronized (this) {
                if (bank == null) {
                    bank = new OffshoreInstance(getAlliance_id());
                }
            }
        }
        return bank;
    }
}