package link.locutus.discord.db.entities;

import net.dv8tion.jda.api.entities.User;

public class UserWrapper {
    private final User user;

    public UserWrapper(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}
