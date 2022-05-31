package com.boydti.discord.commands.manager.v2.impl.pw.binding;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.v2.binding.BindingHelper;
import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.ClassPermission;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.CoalitionPermission;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.HasApi;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.IsAuthenticated;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.IsGuild;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.NotGuild;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.RankPermission;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.Coalition;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.offshore.Auth;
import com.boydti.discord.util.offshore.OffshoreInstance;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class PermissionBinding extends BindingHelper {

    @Binding
    @ClassPermission
    public boolean checkClass(@Me GuildDB db, ClassPermission perm) {
        for (Class clazz : perm.value()) {
            if (db.getPermission(clazz) < 1) throw new IllegalCallerException("Guild does not have " + clazz.getSimpleName());
        }
        return true;
    }

    @Binding
    @IsAlliance
    public boolean checkAlliance(@Me GuildDB db, IsAlliance perm) {
        if (!db.isValidAlliance()) throw new IllegalArgumentException(db.getGuild() + " is not a valid alliance. See: `!KeyStore ALLIANCE_ID`");
        return true;
    }

    @Binding
    @IsAuthenticated
    public boolean isAuthenticated(@Me GuildDB db, IsAuthenticated perm) {
        Auth auth = perm.value().length > 0 ? db.getAuth(perm.value()[0].id) : db.getAuth();
        if (auth == null || !auth.isValid()) throw new IllegalArgumentException(db.getGuild() + " is not a valid alliance. See: `!KeyStore ALLIANCE_ID`");
        return true;
    }

    @Binding
    @HasApi
    public boolean hasApi(@Me GuildDB db, HasApi perm) {
        if (db.getApi() == null) throw new IllegalArgumentException("No api key set: `!KeyStore API_KEY`");
        return true;
    }


    @Binding
    @HasOffshore
    public boolean hasOffshore(@Me GuildDB db, HasOffshore perm) {
        OffshoreInstance offshore = db.getOffshore();
        if (offshore == null) throw new IllegalArgumentException("No offshore set");
        if (perm.value() != null && perm.value().length > 0) {
            long offshoreDBId = offshore.getGuildDB().getIdLong();
            for (long id : perm.value()) {
                if (id == offshoreDBId) return true;
            }
            return false;
        }
        return true;
    }


    @Binding
    @IsGuild
    public boolean checkGuild(@Me Guild guild, IsGuild perm) {
        if (!Arrays.asList(perm.value()).contains(guild.getIdLong())) {
            throw new IllegalCallerException("Guild does not have permission");
        }
        return true;
    }

    @Binding
    @NotGuild
    public boolean checkNotGuild(@Me Guild guild, NotGuild perm) {
        if (Arrays.asList(perm.value()).contains(guild.getIdLong())) {
            throw new IllegalCallerException("Guild has permission denied");
        }
        return true;
    }

    @Binding
    @WhitelistPermission
    public boolean checkWhitelistPermission(@Me GuildDB db, @Me User user, WhitelistPermission perm) {
        if (!db.isWhitelisted()) {
            throw new IllegalCallerException("Guild is not whitelisted");
        }
        if (!Roles.MEMBER.has(user, db.getGuild())) {
            throw new IllegalCallerException("You do not have " + Roles.MEMBER);
        }
        return true;
    }

    @Binding
    @CoalitionPermission(Coalition.ALLIES)
    public boolean checkWhitelistPermission(@Me GuildDB db, @Me User user, CoalitionPermission perm) {
        Coalition requiredCoalition = perm.value();
        Guild root = Locutus.imp().getServer();
        GuildDB rootDb = Locutus.imp().getGuildDB(root);
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        Set<Long> coalitionMembers = rootDb.getCoalitionRaw(requiredCoalition);
        return coalitionMembers.contains(db.getIdLong()) || (aaId != null && coalitionMembers.contains((long) aaId));
    }

    @Binding
    @RankPermission
    public boolean checkRank(@Me Guild guild, RankPermission perm, @Me DBNation me) {
        if (me.getPosition() < perm.value().id) {
            throw new IllegalCallerException("Your ingame alliance positions is below " + perm.value());
        }
        return true;
    }

    @Binding
    @RolePermission
    public boolean checkRole(@Me Guild guild, RolePermission perm, @Me User user) {
        for (Roles requiredRole : perm.value()) {
            if (perm.root()) {
                if (!requiredRole.has(user, guild)) {
                    if (perm.any()) continue;
                    throw new IllegalCallerException("You do not have " + requiredRole.name() + " on root");
                } else if (perm.any()) {
                    return true;
                }
            } else if (perm.guild() > 0) {
                guild = Locutus.imp().getDiscordApi().getGuildById(perm.guild());
                if (guild == null) throw new IllegalCallerException("Guild " + perm.guild() + " does not exist");
                if (!requiredRole.has(user, guild)) {
                    if (perm.any()) continue;
                    throw new IllegalCallerException("You do not have " + requiredRole.name() + " on " + guild.getName());
                } else if (perm.any()) {
                    return true;
                }
            } else {
                if (!requiredRole.has(user, guild)) {
                    if (perm.any()) continue;
                    throw new IllegalCallerException("You do not have " + requiredRole.name());
                } else if (perm.any()) {
                    return true;
                }
            }
        }
        return !perm.any();
    }
}