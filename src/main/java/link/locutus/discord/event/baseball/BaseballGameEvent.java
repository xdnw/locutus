package link.locutus.discord.event.baseball;

import com.politicsandwar.graphql.model.BBGame;
import link.locutus.discord.event.Event;

public class BaseballGameEvent extends Event {
    public final BBGame game;

    public BaseballGameEvent(BBGame game) {
        this.game = game;
    }

    public BBGame getGame() {
        return game;
    }

    public int getWinnerId() {
        if (game.getHome_score() > game.getAway_score()) {
            return game.getHome_nation_id();
        } else if (game.getAway_score() > game.getHome_score()) {
            return game.getAway_nation_id();
        }
        return 0;
    }
}
