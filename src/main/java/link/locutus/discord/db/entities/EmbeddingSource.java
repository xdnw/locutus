package link.locutus.discord.db.entities;

import link.locutus.discord.db.AEmbeddingDatabase;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;

public class EmbeddingSource {
    public final int source_id;
    public final String source_name;
    public final long date_added;
    public final long source_hash;
    public final long guild_id;

    public EmbeddingSource(int source_id, String source_name, long date_added, long source_hash, long guild_id) {
        this.source_id = source_id;
        this.source_name = source_name;
        this.date_added = date_added;
        this.guild_id = guild_id;
        this.source_hash = source_hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmbeddingSource)) return false;
        EmbeddingSource that = (EmbeddingSource) o;
        return source_id == that.source_id;
    }

    public String getQualifiedName() {
        return guild_id + "/" + source_name;
    }

    @Override
    public int hashCode() {
        return source_id;
    }
}
