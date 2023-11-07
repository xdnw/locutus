package link.locutus.discord.db.entities;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class UserWrapper {
    private final User user;
    private final Guild guild;

    public UserWrapper(Guild guild, User user) {
        this.guild = guild;
        this.user = user;
    }

    public UserWrapper(Member member) {
        this.user = member.getUser();
        this.guild = member.getGuild();
    }

    public User getUser() {
        return user;
    }
}
