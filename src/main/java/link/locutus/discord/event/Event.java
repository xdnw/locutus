package link.locutus.discord.event;

import link.locutus.discord.Locutus;
import link.locutus.discord.util.AlertUtil;

public class Event {
    private final long time = System.currentTimeMillis();
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
