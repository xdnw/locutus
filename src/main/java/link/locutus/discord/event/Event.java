package link.locutus.discord.event;

import link.locutus.discord.Locutus;
import link.locutus.discord.util.AlertUtil;

public class Event {
    private long time;

    public Event(long time) {
        this.time = time;
    }

    public Event() {
        this(System.currentTimeMillis());
    }

    public void setTime(long time) {
        this.time = time;
    }

    public void post() {
        long start = System.currentTimeMillis();
        Locutus.post(this);
        long diff = System.currentTimeMillis() - start;
        if (diff > 100) {
            AlertUtil.error(this.getClass().getSimpleName() + " took too long (" + diff + "ms)", new Exception());
        }
    }

    public long getTimeCreated() {
        return time;
    }
}
