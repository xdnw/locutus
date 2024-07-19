package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Bank extends Command {
    public Bank() {
        super("stockpile", "bank", CommandCategory.ECON);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.nation.stockpile.cmd);
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) {
            return usage(args.size(), 1, channel);
        }

        User user = author;
        DBNation banker = me;
        if (banker == null) {
            return "Please use " + CM.register.cmd.toSlashMention();
        }
        Map<ResourceType, Double> totals;

        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (!db.hasAlliance())
            return "No alliance set for this server. See: " + CM.settings.info.cmd.toSlashMention() + " with key: " + GuildKey.ALLIANCE_ID.name();

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
            alliance = PW.parseAllianceId(args.get(0));
            if (alliance == null) {
                return "Invalid alliance: `" + args.get(0) + "`";
            }
            if (!db.isAllianceId(alliance)) {
                return "That alliance is not registered to this guild, see " + CM.settings_default.registerAlliance.cmd.toSlashMention();
            }
            if (!Roles.ECON_STAFF.has(author, guild)) {
                return "You do not have permission to view alliance bank contents.";
            }
            totals = DBAlliance.getOrCreate(alliance).getStockpile();
        }
        String out = ResourceType.resourcesToFancyString(totals);
        channel.create().embed(args.get(0) + " stockpile", out).send();
        return null;
    }
}