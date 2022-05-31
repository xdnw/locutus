package com.boydti.discord.commands.account;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.StringMan;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.internal.utils.PermissionUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RoleAlias extends Command {
    public RoleAlias() {
        super("aliasrole", CommandCategory.GUILD_MANAGEMENT);
    }

    @Override
    public String help() {
        return "!aliasrole <role> <discord-role>";
    }

    @Override
    public String desc() {
        return "Map a Locutus role to a discord role. Valid roles are: " + Roles.getValidRolesStringList();
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        User user = event.getAuthor();

        if (!event.isFromGuild()) {
            return "Can only run in a guild";
        }
        Guild server = event.getGuild();
        GuildDB db = Locutus.imp().getGuildDB(event);

        if (args.size() != 2) {
            int invalidRoles = 0;
            List<Map.Entry<Roles, Long>> roles = db.getRoles();
            StringBuilder response = new StringBuilder("Current aliases:").append('\n');
            for (Map.Entry<Roles, Long> role : roles) {
                Roles locRole = role.getKey();
                GuildDB.Key key = locRole.getKey();

                Role discordRole = server.getRoleById(role.getValue());
                String roleName = discordRole == null ? "null" : discordRole.getName();
                response.append(" - " + role.getKey().name().toLowerCase() + " > " + roleName);

                if (key != null && db.getOrNull(key) == null) {
                    response.append(" (missing: " + key.name() + ")");
                }
                response.append('\n');
            }
            response.append("Available aliases: " + Roles.getValidRolesStringList()).append('\n');
            response.append("Usage: `!aliasrole <" + StringMan.join(Arrays.asList(Roles.values()).stream().map(r -> r.name()).collect(Collectors.toList()), "|") + "> <discord-role>`");
            return response.toString().trim();
        }
        Roles role;
        try {
            role = Roles.valueOf(args.get(0).toUpperCase());
        } catch (IllegalArgumentException ignore) {
            return "Invalid role: ``" + args.get(0) + "`" + "`. Valid options are: " + Roles.getValidRolesStringList();
        }

        if (args.get(1).equalsIgnoreCase("null")) {
            db.deleteRole(role);
            return "Unregistered " + args.get(0);
        }

        Role discordRole = DiscordUtil.getRole(server, args.get(1));

        if (discordRole == null) return "No single role found for: ``" + args.get(1) + "`" + "`";

        Member member = server.getMember(user);

        if (!Roles.ADMIN.hasOnRoot(user) && !PermissionUtil.checkPermission(member, Permission.ADMINISTRATOR) && !PermissionUtil.checkPermission(member, Permission.MANAGE_SERVER) && !PermissionUtil.checkPermission(member, Permission.MANAGE_ROLES)) {
            return "You are not allowed to manage the role: " + discordRole.getName();
        }

        db.addRole(role, discordRole.getIdLong());
        return "Added role alias: " + role.name().toLowerCase() + " to " + discordRole.getName();
    }
}
