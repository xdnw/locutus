package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Bank extends Command {
    public Bank() {
        super("stockpile", "bank", CommandCategory.ECON);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "bank <alliance>";
    }

    @Override
    public String desc() {
        return "View nation or AA bank contents";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server) && Locutus.imp().getGuildDB(server).isValidAlliance();
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) {
            return usage(event);
        }

        User user = event.getAuthor();
        DBNation banker = DiscordUtil.getNation(event);
        if (banker == null) {
            return "Please use " + Settings.commandPrefix(true) + "validate";
        }
        Map<ResourceType, Double> totals;

        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (!db.hasAlliance())
            return "No alliance set for this server. See: " + CM.info.cmd.toSlashMention() + " with key: " + GuildKey.ALLIANCE_ID.name();

        Integer alliance;
        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation != null && !args.get(0).contains("/alliance/")) {
            alliance = nation.getAlliance_id();
            if (!db.isAllianceId(alliance)) {
                return "That nation is not in your alliance:";
            }
            if (nation.getNation_id() != banker.getNation_id()) {
                if (!Roles.INTERNAL_AFFAIRS_STAFF.has(user, guild) && !Roles.ECON_STAFF.has(user, guild) && !Roles.MILCOM.has(user, guild)) {
                    return "You are not authorized to view another nations bank. Missing any roles: " + Roles.INTERNAL_AFFAIRS_STAFF + ", " + Roles.ECON_STAFF + ", " + Roles.MILCOM;
                }
            }
            totals = nation.getStockpile();
            if (totals == null) return "They are not a member of " + alliance;
        } else {
            alliance = PnwUtil.parseAllianceId(args.get(0));
            if (alliance == null) {
                return "Invalid alliance: `" + args.get(0) + "`";
            }
            if (!db.isAllianceId(alliance)) {
                return "That alliance is not in your alliance:";
            }
            if (!Roles.ECON_STAFF.has(author, guild)) {
                return "You do not have permission to view alliance bank contents.";
            }
            totals = DBAlliance.getOrCreate(alliance).getStockpile();
        }
        String out = PnwUtil.resourcesToFancyString(totals);
        DiscordUtil.createEmbedCommand(event.getChannel(), args.get(0) + " stockpile", out);
        return null;
    }
}