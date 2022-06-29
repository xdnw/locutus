package link.locutus.discord.event;

import link.locutus.discord.Locutus;

public class Event {
    private long time = System.currentTimeMillis();
    public void post() {
        Locutus.post(this);
    }

    public long getTimeCreated() {
        return time;
    }
}
