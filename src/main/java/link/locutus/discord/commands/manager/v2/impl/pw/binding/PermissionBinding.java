package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.ClassPermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.CoalitionPermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasApi;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasKey;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsGuild;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.NotGuild;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RankPermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PermissionBinding extends BindingHelper {

    @Binding(value = "Must be used in a guild with a classpath permission set by a bot developer")
    @ClassPermission
    public boolean checkClass(@Me GuildDB db, ClassPermission perm) {
        for (Class clazz : perm.value()) {
            if (db.getPermission(clazz) < 1) throw new IllegalCallerException("Guild does not have " + clazz.getSimpleName());
        }
        return true;
    }

    @Binding(value = "Must be used in a guild registered to a valid in-game alliance")
    @IsAlliance
    public boolean checkAlliance(@Me GuildDB db, IsAlliance perm) {
        if (!db.isValidAlliance()) throw new IllegalArgumentException(db.getGuild() + " is not a valid alliance. See: " + GuildKey.ALLIANCE_ID.getCommandMention() + "");
        return true;
    }

//    @Binding
//    @IsAuthenticated
//    public boolean isAuthenticated(@Me GuildDB db, IsAuthenticated perm) {
//        Auth auth = perm.value().length > 0 ? db.getAuth(perm.value()) : db.getAuth();
//        if (auth == null || !auth.isValid()) throw new IllegalArgumentException(db.getGuild() + " is not authenticaed. See: " + CM.credentials.login.cmd.toSlashMention() + "");
//        return true;
//    }

    @Binding(value = "Must be used in a guild with a valid API_KEY configured")
    @HasApi
    public boolean hasApi(@Me GuildDB db, HasApi perm) {
        if (db.getOrNull(GuildKey.API_KEY) == null) throw new IllegalArgumentException("No api key set: " + GuildKey.API_KEY.getCommandMention() + "");
        return true;
    }


    @Binding(value = "Must be used in a Guild with a valid Locutus managed offshore setup")
    @HasOffshore
    public static boolean hasOffshore(@Me GuildDB db, HasOffshore perm) {
        OffshoreInstance offshore = db.getOffshore();
        if (offshore == null) {
            StringBuilder response = new StringBuilder("No offshore is set.");
            response.append("\nSee: ").append(CM.offshore.add.cmd.toSlashMention());
            if (db.isValidAlliance()) {
                response.append("\nNote: Use this alliance id to use the alliance bank for withdrawals (or to create an offshoring point for other alliances you control)");
            } else if (!db.hasAlliance()) {
                response.append("\nNote: Set the alliance for this guild using: " + GuildKey.ALLIANCE_ID.getCommandMention() + "");
            }
            Set<String> publicOffshores = new HashSet<>();
            for (GuildDB otherDB : Locutus.imp().getGuildDatabases().values()) {
                if (otherDB.isValidAlliance() && otherDB.isOffshore() && otherDB.getOrNull(GuildKey.PUBLIC_OFFSHORING) == Boolean.TRUE) {
                    Map.Entry<GuildDB, Integer> offshoreInfo = otherDB.getOffshoreDB();
                    String markdown = PnwUtil.getMarkdownUrl(offshoreInfo.getValue(), true);
                    if (offshoreInfo.getValue() == Settings.INSTANCE.ALLIANCE_ID()) {
                        markdown += " (Bot Owner)";
                    }
                    publicOffshores.add(markdown);
                }
            }
            if (!publicOffshores.isEmpty()) {
                response.append("\nPublic offshores:\n- ").append(String.join("\n- ", publicOffshores));
                response.append("\n`note: do not offshore to these guilds if you do not trust them`");
            }

            throw new IllegalArgumentException(response.toString());
        }
        if (perm != null && perm.value() != null && perm.value().length > 0) {
            long offshoreDBId = offshore.getGuildDB().getIdLong();
            for (long id : perm.value()) {
                if (id == offshoreDBId) return true;
            }
            return false;
        }
        return true;
    }


    @Binding(value = "Must be run in a guild matching the provided ids")
    @IsGuild
    public boolean checkGuild(@Me Guild guild, IsGuild perm) {
        if (Arrays.stream(perm.value()).noneMatch(f -> f == guild.getIdLong())) {
            throw new IllegalCallerException("Guild does not have permission");
        }
        return true;
    }

    @Binding(value = "Cannot be run in guilds matching the provided ids")
    @NotGuild
    public boolean checkNotGuild(@Me Guild guild, NotGuild perm) {
        if (Arrays.stream(perm.value()).noneMatch(f -> f == guild.getIdLong())) {
            throw new IllegalCallerException("Guild has permission denied");
        }
        return true;
    }

    @Binding(value = "Must be run in a guild that has configured the provided settings")
    @HasKey
    public boolean checkKey(@Me GuildDB db, @Me User author, HasKey perm) {
        if (perm.value() == null || perm.value().length == 0) {
            throw new IllegalArgumentException("No key provided");
        }
        for (String keyName : perm.value()) {
            GuildSetting key = GuildKey.valueOf(keyName.toUpperCase());
            Object value = key.getOrNull(db);
            if (value == null) {
                throw new IllegalArgumentException("Key " + key.name() + " is not set in " + db.getGuild());
            }
            if (perm.checkPermission() && !key.hasPermission(db, author, value)) {
                throw new IllegalCallerException("Key " + key.name() + " does not have permission in " + db.getGuild());
            }
        }
        return true;
    }

    @Binding("Must be run in a guild whitelisted by the bot developer")
    @WhitelistPermission
    public boolean checkWhitelistPermission(@Me GuildDB db, @Me User user, WhitelistPermission perm) {
        if (!db.isWhitelisted()) {
            throw new IllegalCallerException("Guild is not whitelisted");
        }
        if (!Roles.MEMBER.has(user, db.getGuild())) {
            throw new IllegalCallerException("You do not have " + Roles.MEMBER + " " + user.getAsMention() + " see: " + CM.role.setAlias.cmd.toSlashMention());
        }
        return true;
    }

    @Binding("Must be run in a guild added to a coalition by the bot developer")
    @CoalitionPermission(Coalition.ALLIES)
    public boolean checkWhitelistPermission(@Me GuildDB db, CoalitionPermission perm) {
        if (db.getIdLong() == Settings.INSTANCE.ROOT_SERVER) return true;
        Coalition requiredCoalition = perm.value();
        Guild root = Locutus.imp().getServer();
        GuildDB rootDb = Locutus.imp().getGuildDB(root);
        Set<Long> coalitionMembers = rootDb.getCoalitionRaw(requiredCoalition);
        if (coalitionMembers.contains(db.getIdLong())) return true;
        Set<Integer> aaIds = db.getAllianceIds();
        for (int aaId : aaIds) {
            if (coalitionMembers.contains((long) aaId)) return true;
        }
        return false;
    }

    @Binding("Must be registered to a nation with an in-game rank equal to or above the provided rank\n" +
            "Default: MEMBER")
    @RankPermission
    public boolean checkRank(@Me Guild guild, RankPermission perm, @Me DBNation me) {
        if (me.getPosition() < perm.value().id) {
            throw new IllegalCallerException("Your ingame alliance positions is below " + perm.value());
        }
        return true;
    }

    @Binding("Must have the provided Locutus roles on discord\n" +
            "If `any` requires ony of the roles instead of all\n" +
            "If `root` requires the role on the Locutus server\n" +
            "If `guild` requires the role in the guild matching that id\n" +
            "If `alliance` requires the role in the guild for that alliance")
    @RolePermission
    public static boolean checkRole(@Me Guild guild, RolePermission perm, @Me User user) {
        return checkRole(guild, perm, user, null);
    }

    public static boolean checkRole(@Me Guild guild, RolePermission perm, @Me User user, Integer allianceId) {
        if (perm.root()) {
            guild = Locutus.imp().getServer();
        } else if (perm.guild() > 0) {
            guild = Locutus.imp().getDiscordApi().getGuildById(perm.guild());
            if (guild == null) throw new IllegalCallerException("Guild " + perm.guild() + " does not exist" + " " + user.getAsMention() + " (are you sure Locutus is invited?)");
        }
        boolean hasAny = false;
        for (Roles requiredRole : perm.value()) {
            if (allianceId != null && !requiredRole.has(user, guild, allianceId) ||
                    (!requiredRole.has(user, guild) && (!perm.alliance() || requiredRole.getAllowedAccounts(user, guild).isEmpty()))) {
                if (perm.any()) continue;
                throw new IllegalCallerException("You do not have " + requiredRole.name() + " on " + guild + " " + user.getAsMention() + " see: " + CM.role.setAlias.cmd.toSlashMention());
            } else {
                hasAny = true;
            }
        }
        if (!hasAny) {
            throw new IllegalCallerException("You do not have any of " + Arrays.toString(perm.value()) + " on " + guild + " " + user.getAsMention() + " see: " + CM.role.setAlias.cmd.toSlashMention());
        }
        return hasAny;
    }
}