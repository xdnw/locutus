package link.locutus.discord.gpt.imps;

import link.locutus.discord.db.entities.EmbeddingSource;

public class EmbeddingInfo {
    public final long hash;
    public final float[] vector;
    public final EmbeddingSource source;
    public double distance;

    public EmbeddingInfo(long hash, float[] vector, EmbeddingSource source, double distance) {
        this.hash = hash;
        this.vector = vector;
        this.source = source;
        this.distance = distance;
    }
}

