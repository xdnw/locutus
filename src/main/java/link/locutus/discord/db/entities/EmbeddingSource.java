package link.locutus.discord.db.entities;

import link.locutus.discord.db.AEmbeddingDatabase;

public class EmbeddingSource {
    public final int source_id;
    public final String source_name;
    public final long date_added;
    public final long guild_id;

    public EmbeddingSource(int source_id, String source_name, long date_added, long guild_id) {
        this.source_id = source_id;
        this.source_name = source_name;
        this.date_added = date_added;
        this.guild_id = guild_id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmbeddingSource)) return false;
        EmbeddingSource that = (EmbeddingSource) o;
        return source_id == that.source_id;
    }

    @Override
    public int hashCode() {
        return source_id;
    }
}
