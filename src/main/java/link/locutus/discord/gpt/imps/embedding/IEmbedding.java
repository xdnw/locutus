package link.locutus.discord.gpt.imps.embedding;

import link.locutus.discord.gpt.ITokenizer;

import java.io.Closeable;

public interface IEmbedding extends Closeable, ITokenizer {
    String getTableName();
    float[] fetchEmbedding(String text);
    void init();
}
