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
            System.out.println("Posted " + this.getClass().getSimpleName() + "(took " + diff + ")");
        } else {
//            System.out.println("Posted " + this.getClass().getSimpleName());
        }
    }

    public long getTimeCreated() {
        return time;
    }
}
