package link.locutus.discord.event;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

public class NationRegisterEvent extends Event {
    private final int nationId;
    private final Guild guild;
    private final User user;
    private final boolean isNew;

    public NationRegisterEvent(int nationId, Guild guild, User user, boolean isNew) {
        this.nationId = nationId;
        this.guild = guild;
        this.user = user;
        this.isNew = isNew;
    }

    public boolean isNew() {
        return isNew;
    }

    public int getNation() {
        return nationId;
    }

    public Guild getGuild() {
        return guild;
    }

    public User getUser() {
        return user;
    }
}
