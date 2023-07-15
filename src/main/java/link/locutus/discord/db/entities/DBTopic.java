package link.locutus.discord.db.entities;

public class DBTopic {
    public final int topic_id;
    public final int section_id;
    public final String topic_name;
    public final String topic_urlname;
    public final String section_name;
    public final String section_urlname;
    public final long timestamp;
    public final int poster_id;
    public final String poster_name;

    public DBTopic(int topic_id, int section_id, String topic_name, String topic_urlname, String section_name, String section_urlname, long timestamp, int poster_id, String poster_name) {
        this.topic_id = topic_id;
        this.section_id = section_id;
        this.topic_name = topic_name;
        this.topic_urlname = topic_urlname;
        this.section_name = section_name;
        this.section_urlname = section_urlname;
        this.timestamp = timestamp;
        this.poster_id = poster_id;
        this.poster_name = poster_name;
    }

}
