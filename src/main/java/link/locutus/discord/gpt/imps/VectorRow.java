package link.locutus.discord.gpt.imps;

import java.util.Arrays;

public class VectorRow {
    public final long id;
    public final int sourceId;
    public final String text;
    public final float[] vector;
    public double score; // cosine distance (smaller is better)

    public VectorRow(long id, String text, float[] vector, int sourceId, double score) {
        this.id = id;
        this.sourceId = sourceId;
        this.text = text;
        this.vector = vector;
        this.score = score;
    }

    public VectorRow(VectorRow row, double newScore) {
        this.id = row.id;
        this.sourceId = row.sourceId;
        this.text = row.text;
        this.vector = row.vector;
        this.score = newScore;
    }

    @Override
    public String toString() {
        return "VectorRow{id=" + id + ", label='" + text + "', score=" + score + ", sourceId=" + sourceId +
                ", embedding=" + Arrays.toString(vector) + "}";
    }
}