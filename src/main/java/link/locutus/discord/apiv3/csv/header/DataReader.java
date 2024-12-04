package link.locutus.discord.apiv3.csv.header;

public abstract class DataReader<T extends DataHeader> {
    public final T header;
    private final long date;

    public DataReader(T header, long date) {
        this.header = header;
        this.date = date;
    }

    public final long getDate() {
        return date;
    }

    public final T getHeader() {
        return header;
    }

    public abstract void clear();
}
