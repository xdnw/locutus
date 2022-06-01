package link.locutus.discord.event;

import link.locutus.discord.Locutus;

public class Event {
    public void post() {
        Locutus.post(this);
    }
}
