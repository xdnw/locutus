package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Set;
import java.util.stream.Collectors;

public class TextChannelWrapper {
    private final TextChannel channel;

    public TextChannelWrapper(TextChannel channel) {
        this.channel = channel;

    }

    @Command
    public String getName() {
        return channel.getName();
    }

    @Command
    public GuildDB getGuild() {
        return Locutus.imp().getGuildDB(channel.getGuild());
    }

    @Command
    public String getMention() {
        return channel.getAsMention();
    }

    @Command
    public long getId() {
        return channel.getIdLong();
    }

    public TextChannel getChannel() {
        return channel;
    }

    @Command
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    public String getTopic() {
        return channel.getTopic();
    }

    @Command
    public boolean isNSFW() {
        return channel.isNSFW();
    }

    @Command
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    public boolean canTalk(User user) {
        Member member = channel.getGuild().getMember(user);
        return member != null && channel.canTalk(member);
    }

    @Command
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    public boolean canBotTalk() {
        return channel.canTalk();
    }

    @Command
    public String getJumpUrl() {
        return channel.getJumpUrl();
    }

    @Command
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    public Set<UserWrapper> getMembers() {
        return channel.getMembers().stream().map(UserWrapper::new).collect(Collectors.toSet());
    }

    @Command
    public int getPosition() {
        return channel.getPosition();
    }

    @Command
    public int getCategoryPosition() {
        return channel.getPositionInCategory();
    }

    @Command
    public int getRawPosition() {
        return channel.getPositionRaw();
    }

    @Command
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    public Boolean getPermission(User user, Permission permission) {
        Member member = channel.getGuild().getMember(user);
        if (member == null) return null;
        PermissionOverride override = channel.getPermissionOverride(member);
        if (override != null) {
            if (override.getAllowed().contains(permission)) return true;
            if (override.getDenied().contains(permission)) return false;
        }
        return null;
    }

    @Command
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    public Boolean getPermission(Role role, Permission permission) {
        PermissionOverride override = channel.getPermissionOverride(role);
        if (override != null) {
            if (override.getAllowed().contains(permission)) return true;
            if (override.getDenied().contains(permission)) return false;
        }
        return null;
    }


}
