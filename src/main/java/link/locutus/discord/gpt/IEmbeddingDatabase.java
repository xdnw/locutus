package link.locutus.discord.gpt;

import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;

public interface IEmbeddingDatabase {
    public double[] getEmbedding(long hash);
    public double[] getEmbedding(String content);
    public long getHash(long type, String id);
    public double[] getEmbedding(int type, String id);

    public void setEmbedding(int type, @Nullable String id2, String content, double[] value, boolean saveContent);
    public void addEmbedding(long hash, long type, String id, byte[] data);
    public double[] fetchEmbedding(String text);
}
