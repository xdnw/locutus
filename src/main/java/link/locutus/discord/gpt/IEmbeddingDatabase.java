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
    public float[] getEmbedding(long hash);
    public float[] getEmbedding(String content);
    public long getHash(long type, String id);
    public float[] getEmbedding(int type, String id);

    public void setEmbedding(int type, @Nullable String id2, String content, float[] value, boolean saveContent);
    public void addEmbedding(long hash, long type, String id, byte[] data);
    public float[] fetchEmbedding(String text);
}
