package link.locutus.discord.gpt.imps.embedding;

import link.locutus.discord.db.entities.EmbeddingSource;

public class EmbeddingInfo {
    public final String text;
    public final long hash;
    public final EmbeddingSource source;
    public final double distance;

    public EmbeddingInfo(String text, long hash, EmbeddingSource source, double distance) {
        this.text = text;
        this.hash = hash;
        this.source = source;
        this.distance = distance;
    }
}

