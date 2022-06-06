package link.locutus.discord.event;

import com.politicsandwar.graphql.model.BBGame;
import link.locutus.discord.db.entities.DBBounty;

public class BaseballGameEvent extends Event {
    public final BBGame game;

    public BaseballGameEvent(BBGame game) {
        this.game = game;
    }
}
