package link.locutus.discord.event;

import link.locutus.discord.db.entities.Treaty;

public class TreatyUpdateEvent {
    private final Treaty previous;
    private final Treaty current;

    public TreatyUpdateEvent(Treaty previous, Treaty current) {
        this.previous = previous;
        this.current = current;
    }

    public Treaty getCurrent() {
        return current;
    }

    public Treaty getPrevious() {
        return previous;
    }
}
