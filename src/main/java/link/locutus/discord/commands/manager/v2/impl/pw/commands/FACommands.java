package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.TreatyType;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import org.jooq.meta.derby.sys.Sys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class FACommands {
    @Command(desc = "Generate a named coalition by the treaty web of an alliance")
    @RolePermission(Roles.FOREIGN_AFFAIRS)
    public String generateSphere(@Me GuildDB db, String coalition, DBAlliance rootAlliance, @Default("80") int topX) {
        SphereGenerator sphereGen = new SphereGenerator(topX);
        Map.Entry<Integer, List<DBAlliance>> sphere = sphereGen.getSphere(rootAlliance);
        if (sphere == null) {
            return "No sphere found for " + rootAlliance.getName();
        }
        if (Coalition.getOrNull(coalition) != null) {
            return coalition + " is a reserved keyword";
        }
        Set<Long> existing = db.getCoalitionRaw(coalition);
        if (existing != null) {
            for (long id : new HashSet<>(existing)) {
                db.removeCoalition(id, coalition);
            }
        }
        for (DBAlliance alliance : sphere.getValue()) {
            db.addCoalition(alliance.getIdLong(), coalition);
        }
        return "Set Coalition: `" + coalition + "` to `" + StringMan.getString( sphere.getValue()) + "`";
    }

    @Command(desc = "Send a treaty to an alliance")
    @IsAlliance
    @RolePermission(value = Roles.FOREIGN_AFFAIRS, alliance = true)
    public String sendTreaty(@Me User user, @Me GuildDB db, @RolePermission(Roles.FOREIGN_AFFAIRS) AllianceList sender, DBAlliance alliance, TreatyType type, int days, @Default String message) {
        if (message != null && !message.isEmpty() && !Roles.ADMIN.has(user, db.getGuild())) {
            return "Admin is required to send a treaty with a message";
        }
        if (message == null) message = "";
        Set<Treaty> result = sender.sendTreaty(alliance.getAlliance_id(), type, message, days);
        return "Sent:\n - " + StringMan.join(result, "\n - ");
    }

    @Command
    @IsAlliance
    @RolePermission(value = Roles.FOREIGN_AFFAIRS, alliance = true)
    public String approveTreaty(@Me User user, @Me GuildDB db, @RolePermission(Roles.FOREIGN_AFFAIRS) AllianceList receiver, Set<DBAlliance> senders) {
        List<Treaty> changed = receiver.approveTreaty(senders);

        if (changed.isEmpty()) {
            return "No treaties to approve";
        }

        return "Approved:\n - " + StringMan.join(changed, "\n - ");
    }

    @Command
    @IsAlliance
    @RolePermission(Roles.FOREIGN_AFFAIRS)
    public String cancelTreaty(@Me User user, @Me DBNation me, @Me GuildDB db, @RolePermission(Roles.FOREIGN_AFFAIRS) AllianceList receiver, Set<DBAlliance> senders) {
        List<Treaty> changed = receiver.cancelTreaty(senders);

        if (changed.isEmpty()) {
            return "No treaties to cancel";
        }

        return "Cancelled:\n - " + StringMan.join(changed, "\n - ");
    }

    @Command(desc = "List the coalitions")
    @RolePermission(Roles.MEMBER)
    public String listCoalition(@Me User user, @Me GuildDB db, @Default String filter, @Switch("i") boolean listIds, @Switch("d") boolean ignoreDeleted) {
        Map<String, Set<Long>> coalitions = db.getCoalitionsRaw();
        List<String> coalitionNames = new ArrayList<>(coalitions.keySet());
        Collections.sort(coalitionNames);

        StringBuilder response = new StringBuilder();
        for (String coalition : coalitionNames) {
            if (coalition.equalsIgnoreCase("offshore") && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild())) {
                continue;
            }
            Set<Long> alliances = coalitions.get(coalition);
            List<String> names = new ArrayList<>();
            for (long allianceOrGuildId : alliances) {
                String name;
                if (allianceOrGuildId > Integer.MAX_VALUE) {
                    GuildDB guildDb = Locutus.imp().getGuildDB(allianceOrGuildId);
                    if (guildDb == null) {
                        if (ignoreDeleted) continue;
                        name = "guild:" + allianceOrGuildId;
                    } else {
                        name = guildDb.getGuild().toString();
                    }
                } else {
                    name = Locutus.imp().getNationDB().getAllianceName((int) allianceOrGuildId);
                    if (name == null) {
                        if (ignoreDeleted) continue;
                        name = "AA:" + allianceOrGuildId;
                    }
                }
                if (listIds) {
                    names.add(allianceOrGuildId + "");
                } else {
                    names.add(name);
                }
            }
            if (filter != null) {
                if (!coalition.contains(filter)) {
                    names.removeIf(f -> !f.toLowerCase().contains(filter));
                    if (names.isEmpty()) continue;
                }
            }

            response.append('\n').append("**" + coalition + "**: " + StringMan.join(names, ","));
        }

        if (response.length() == 0) return "No coalitions found";
        return response.toString().trim();
    }

    @Command(desc = "Create a new coalition with the provided alliances")
    @RolePermission(Roles.FOREIGN_AFFAIRS)
    public String createCoalition(@Me User user, @Me GuildDB db, Set<NationOrAllianceOrGuild> alliances, String coalitionStr) {
        Coalition coalition = Coalition.getOrNull(coalitionStr);
        if ((coalition != null && !coalition.hasPermission(db.getGuild(), user)) ||
                (coalition == null && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild()))
        ) {
            return "You do not have permission to set this coalition";
        }
        StringBuilder response = new StringBuilder();
        for (NationOrAllianceOrGuild aaOrGuild : alliances) {
            if (aaOrGuild.isNation()) {
                DBAlliance alliance = Locutus.imp().getNationDB().getAllianceByName(aaOrGuild.getName());
                if (alliance == null) throw new IllegalArgumentException("Invalid alliance: " + aaOrGuild.getName());
                aaOrGuild = alliance;
            }
            db.addCoalition(aaOrGuild.getIdLong(), coalitionStr);
            response.append("Added " + aaOrGuild.getName() + " to " + coalitionStr).append("\n");
        }
        return response.toString();
    }

    @Command(desc = "Add alliances to an existing coalition\n" +
            "Note: Use `{prefix}createCoalition` to use a nonstandard coalition")
    @RolePermission(Roles.MEMBER)
    public String addCoalition(@Me User user, @Me GuildDB db, Set<NationOrAllianceOrGuild> alliances, @GuildCoalition String coalitionStr) {
        return createCoalition(user, db, alliances, coalitionStr);
    }

    @Command(desc = "Delete an entire coalition")
    @RolePermission(Roles.FOREIGN_AFFAIRS)
    public String deleteCoalition(@Me User user, @Me GuildDB db, @GuildCoalition String coalitionStr) {
        Coalition coalition = Coalition.getOrNull(coalitionStr);
        if ((coalition != null && !coalition.hasPermission(db.getGuild(), user)) ||
                (coalition == null && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild()))
        ) {
            return "You do not have permission to set this coalition";
        }
        db.removeCoalition(coalitionStr);
        return "Deleted coalition " + coalitionStr;
    }

    @Command(desc = "Remove alliances to a coalition\n" +
            "Note: Use `{prefix}deleteCoalition` to delete an entire coalition")
    @RolePermission(Roles.MEMBER)
    public String removeCoalition(@Me User user, @Me GuildDB db, Set<NationOrAllianceOrGuild> alliances, @GuildCoalition String coalitionStr) {
        Coalition coalition = Coalition.getOrNull(coalitionStr);
        if ((coalition != null && !coalition.hasPermission(db.getGuild(), user)) ||
                (coalition == null && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild()))
        ) {
            return "You do not have permission to set this coalition";
        }
        StringBuilder response = new StringBuilder();
        for (NationOrAllianceOrGuild aaOrGuild : alliances) {
            if (aaOrGuild.isNation()) {
                DBAlliance alliance = Locutus.imp().getNationDB().getAllianceByName(aaOrGuild.getName());
                if (alliance == null) throw new IllegalArgumentException("Invalid alliance: " + aaOrGuild.getName());
                aaOrGuild = alliance;
            }
            db.removeCoalition(aaOrGuild.getIdLong(), coalitionStr);
            response.append("Removed " + aaOrGuild.getName() + " from " + coalitionStr).append("\n");
        }
        return response.toString();
    }

    @Command
    public String treaties(@Me IMessageIO channel, @Me User user, Set<DBAlliance> alliances, @Switch("f") boolean listExpired) {
        StringBuilder response = new StringBuilder();
        Set<Treaty> allTreaties = new LinkedHashSet<>();

        if (alliances.size() == 1) {
            DBAlliance alliance = alliances.iterator().next();
            boolean update = false;
            GuildDB aaDb = alliance.getGuildDB();
            if (aaDb != null) {
                update = (Roles.FOREIGN_AFFAIRS.getAllowedAccounts(user, aaDb).contains(alliance.getIdLong()));
            }
            allTreaties.addAll(alliance.getTreaties(update).values());
        } else {
            for (DBAlliance alliance : alliances) {
                Map<Integer, Treaty> treaties = alliance.getTreaties();
                allTreaties.addAll(treaties.values());
            }
        }

        if (allTreaties.isEmpty()) return "No treaties";

        long turn = TimeUtil.getTurn();
        for (Treaty treaty : allTreaties) {
            String from = PnwUtil.getMarkdownUrl(treaty.getFromId(), true);
            String to = PnwUtil.getMarkdownUrl(treaty.getToId(), true);
            TreatyType type = treaty.getType();

            response.append(from + " | " + type + " -> " + to);
            if (treaty.getTurnEnds() > turn) {
                String expires = TimeUtil.secToTime(TimeUnit.MILLISECONDS, TimeUtil.getTimeFromTurn(treaty.getTurnEnds() - turn));
                response.append(" (" + expires + ")");
            }
            response.append("\n");
        }

        String title = allTreaties.size() + " treaties";
        channel.create().embed(title, response.toString()).send();
        return null;
    }

    @Command
    @RolePermission(Roles.REGISTERED)
    public String embassy(@Me GuildDB db, @Me Guild guild, @Me User user, @Me DBNation me, @Default("%user%") DBNation nation) {
        if (nation == null) nation = me;
        if (!me.equals(nation) && !Roles.FOREIGN_AFFAIRS.has(user, guild)) return "You do not have FOREIGN_AFFAIRS";
        Category category = db.getOrThrow(GuildDB.Key.EMBASSY_CATEGORY);
        if (category == null) {
            return "Embassies are disabled. To set it up, use " + CM.settings.cmd.create(GuildDB.Key.EMBASSY_CATEGORY.name(), "").toSlashCommand() + "";
        }
        if (nation.getPosition() < Rank.OFFICER.id && !Roles.FOREIGN_AFFAIRS.has(user, guild)) return "You are not an officer";
        User nationUser = nation.getUser();
        if (nationUser == null) return "Nation " + nation.getNationUrl() + " is not registered";
        Member member = guild.getMember(nationUser);
        if (member == null) return "User " + user.getName() + " is not in this guild";

        Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
        Role role = aaRoles.get(nation.getAlliance_id());
        if (role == null) {
            db.addCoalition(nation.getAlliance_id(), Coalition.MASKEDALLIANCES);
            GuildDB.AutoRoleOption autoRoleValue = db.getOrNull(GuildDB.Key.AUTOROLE);
            if (autoRoleValue == null || autoRoleValue == GuildDB.AutoRoleOption.FALSE) {
                return "AutoRole is disabled. See " + CM.settings.cmd.create(GuildDB.Key.AUTOROLE.name(), null).toSlashCommand() + "";
            }
            db.getAutoRoleTask().syncDB();
            db.getAutoRoleTask().autoRole(member, f -> {});
            aaRoles = DiscordUtil.getAARoles(guild.getRoles());
            role = aaRoles.get(nation.getAlliance_id());
            if (role == null) {
                return "No alliance role found. Please try " + CM.role.autoassign.cmd.create().toSlashCommand() + "";
            }
        }

        for (TextChannel channel : category.getTextChannels()) {
            String[] split = channel.getName().split("-");
            if (MathMan.isInteger(split[split.length - 1]) && Integer.parseInt(split[split.length - 1]) == nation.getAlliance_id()) {
                updateEmbassyPerms(channel, role, user, true);
                return "Embassy: <#" + channel.getId() + ">";
            }
        }
        if (me.getPosition() <= 2) {
            return "You must be an officer to create an embassy";
        }

        String embassyName = nation.getAllianceName() + "-" + nation.getAlliance_id();

        TextChannel channel = RateLimitUtil.complete(category.createTextChannel(embassyName).setParent(category));
        updateEmbassyPerms(channel, role, nationUser, true);

        return "Embassy: <#" + channel.getId() + ">";
    }

    public static void updateEmbassyPerms(TextChannel channel, Role role, User user, boolean mention) {
        RateLimitUtil.complete(channel.putPermissionOverride(channel.getGuild().getRolesByName("@everyone", false).get(0))
                .deny(Permission.VIEW_CHANNEL));
        RateLimitUtil.complete(channel.putPermissionOverride(role).grant(Permission.VIEW_CHANNEL));

        Role ambassador = Roles.FOREIGN_AFFAIRS.toRole(channel.getGuild());
        if (ambassador != null) {
            RateLimitUtil.complete(channel.putPermissionOverride(ambassador).grant(Permission.VIEW_CHANNEL));
        }

        if (mention) {
            RateLimitUtil.queue(channel.sendMessage(user.getAsMention()));
        }
    }
}
