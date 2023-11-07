package link.locutus.discord.db.entities;

import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class UserWrapper {
    private final long userId;
    private final Guild guild;

    public UserWrapper(Guild guild, User user) {
        this.guild = guild;
        this.userId = user.getIdLong();
    }

    public UserWrapper(Member member) {
        this.userId = member.getIdLong();
        this.guild = member.getGuild();
    }

    public User getUser() {
        return guild.getJDA().getUserById(userId);
    }

    public Member getMember() {
        return guild.getMemberById(userId);
    }

    public long getUserId() {
        return userId;
    }

    public Guild getGuild() {
        return guild;
    }

    public DBNation getNation() {
        return DiscordUtil.getNation(userId);
    }
}
