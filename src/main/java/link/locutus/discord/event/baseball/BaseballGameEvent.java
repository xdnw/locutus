package link.locutus.discord.event.baseball;

import com.politicsandwar.graphql.model.BBGame;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.event.Event;

public class BaseballGameEvent extends Event {
    public final BBGame game;

    public BaseballGameEvent(BBGame game) {
        this.game = game;
    }
}
