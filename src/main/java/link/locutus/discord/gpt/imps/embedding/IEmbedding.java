package link.locutus.discord.gpt.imps.embedding;

import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.ITokenizer;

import java.io.Closeable;

public interface IEmbedding extends Closeable, ITokenizer {
    String getTableName();

    default float[] fetchAndNormalize(String text) {
        return GPTUtil.handleVectorChunking(text, getSizeCap(), this::getSize, f -> {
            float[] vector = fetch(f);
            GPTUtil.normalize(vector);
            return vector;
        });
    }

    float[] fetch(String text);

    void init();

    int getDimensions();
}
