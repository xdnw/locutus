package link.locutus.discord.db.entities;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.jooq.meta.derby.sys.Sys;

import java.util.HashSet;
import java.util.Set;

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

    @Command(desc = "If this member has a role")
    public boolean hasRole(Role role) {
        Member member = getMember();
        return member != null && member.getRoles().contains(role);
    }

    @Command(desc = "If this member has all roles")
    public boolean hasAllRoles(Set<Role> roles) {
        Member member = getMember();
        return member != null && new ObjectArraySet<>(member.getRoles()).containsAll(roles);
    }

    @Command(desc = "If this member has any roles")
    public boolean hasAnyRoles(Set<Role> roles) {
        Member member = getMember();
        return member != null && member.getRoles().stream().anyMatch(roles::contains);
    }

    @Command(desc = "Discord user id")
    public long getUserId() {
        return userId;
    }

    public Guild getGuild() {
        return guild;
    }

    @Command(desc = "Nation class corresponding to this user\n" +
            "If no nation is found, returns null")
    public DBNation getNation() {
        return DiscordUtil.getNation(userId);
    }

    @Command(desc = "Matches a nation filter\n" +
            "If no nation is found, returns false")
    public boolean matches(NationFilter filter) {
        DBNation nation = getNation();
        if (nation == null) return false;
        return filter.test(nation);
    }

    @Command(desc = "Discord user name")
    public String getUserName() {
        return DiscordUtil.getUserName(userId);
    }

    @Command(desc = "Get user date ceated in milliseconds")
    public long getCreatedMs() {
        User user = getUser();
        return user == null ? 0 : user.getTimeCreated().toEpochSecond() * 1000L;
    }

    @Command(desc = "Get user age in milliseconds")
    public long getAgeMs() {
        User user = getUser();
        return System.currentTimeMillis() - (user == null ? 0 : user.getTimeCreated().toEpochSecond() * 1000L);
    }

    @Command(desc = "Get user avatar url")
    public String getAvatarUrl() {
        User user = getUser();
        return user == null ? null : user.getAvatarUrl();
    }

    @Command(desc = "Get user nickname")
    public String getNickname() {
        Member member = getMember();
        return member == null ? null : member.getNickname();
    }
    @Command(desc = "Get user color")
    public int getColor() {
        Member member = getMember();
        return member == null ? 0 : member.getColorRaw();
    }
    @Command(desc = "Get user effective name")
    public String getEffectiveName() {
        Member member = getMember();
        return member == null ? null : member.getEffectiveName();
    }
    @Command(desc = "Get user effective avatar url")
    public String getEffectiveAvatarUrl() {
        Member member = getMember();
        return member == null ? null : member.getEffectiveAvatarUrl();
    }
    @Command(desc = "Get user time joined in milliseconds")
    public long getTimeJoinedMs() {
        Member member = getMember();
        if (member != null && member.hasTimeJoined()) {
            return member.getTimeJoined().toEpochSecond() * 1000L;
        }
        return 0;
    }

    @Command(desc = "Get time since user join in milliseconds")
    public long getServerAgeMs() {
        return System.currentTimeMillis() - getTimeJoinedMs();
    }
    @Command(desc = "Get user online status")
    public OnlineStatus getOnlineStatus() {
        Member member = getMember();
        return member == null ? null : member.getOnlineStatus();
    }
    @Command(desc = "Get user color raw")
    public int getColorRaw() {
        Member member = getMember();
        return member == null ? 0 : member.getColorRaw();
    }
    @Command(desc = "If this member has access to a channel")
    public boolean hasAccess(TextChannel channel) {
        Member member = getMember();
        return member != null && member.hasAccess(channel);
    }
    @Command(desc = "If this member has a permission")
    public boolean hasPermission(Permission permission) {
        Member member = getMember();
        return member != null && member.hasPermission(permission);
    }
    @Command(desc = "If this member has a permission in a channel")
    public boolean hasPermissionChannel(TextChannel channel, Permission permission) {
        Member member = getMember();
        return member != null && member.hasPermission(channel, permission);
    }
    @Command(desc = "Get user mention")
    public String getMention() {
        User user = getUser();
        return user == null ? null : user.getAsMention();
    }
    @Command(desc = "Get user url")
    public String getUrl() {
        return DiscordUtil.userUrl(userId, false);
    }
}
