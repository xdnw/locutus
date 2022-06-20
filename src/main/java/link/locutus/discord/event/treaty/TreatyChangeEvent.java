package link.locutus.discord.event.treaty;

import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.event.Event;

public class TreatyChangeEvent extends Event {
    private final Treaty previous;
    private final Treaty current;

    public TreatyChangeEvent(Treaty previous, Treaty current) {
        this.previous = previous;
        this.current = current;
    }

    public Treaty getPrevious() {
        return previous;
    }

    public Treaty getCurrent() {
        return current;
    }
}
