package link.locutus.discord.commands.bank;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.AllianceBankContainer;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
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
        return "View nation or AA bank contents.";
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
        Map<ResourceType, Double> totals = new HashMap<>();

        GuildDB db = Locutus.imp().getGuildDB(guild);
        PoliticsAndWarV2 allowedApi = db.getApi();
        PoliticsAndWarV2 api = allowedApi;

        Integer alliance;
        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation != null && !args.get(0).contains("/alliance/")) {
            alliance = nation.getAlliance_id();
            if (alliance != db.getAlliance_id()) {
                api = Locutus.imp().getApi(alliance);
            }
            totals = nation.getStockpile();
            if (totals == null) return "They are not a member of " + alliance;
        } else {
            nation = null;
            alliance = PnwUtil.parseAllianceId(args.get(0));
            if (alliance == null) {
                return "Invalid alliance: `" + args.get(0) + "`";
            }
            if (alliance != db.getAlliance_id()) {
                api = Locutus.imp().getApi(alliance);
            }
            AllianceBankContainer bank = api.getBank(alliance).getAllianceBanks().get(0);
            String json = new Gson().toJson(bank);
            JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
            for (ResourceType type : ResourceType.values) {
                JsonElement amt = obj.get(type.name().toLowerCase());
                if (amt != null) {
                    totals.put(type, amt.getAsDouble());
                }
            }
        }

        if (nation == null || nation.getNation_id() != banker.getNation_id()) {
            if (!Roles.ECON.has(author, guild) && !Roles.MILCOM.has(author, guild) && !Roles.INTERNAL_AFFAIRS.has(author, guild) && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild)) {
                return "You do not have permission to check that account's stockpile.";
            }
        }

        if (allowedApi != api && !Roles.ADMIN.hasOnRoot(user)) {
            return "You do not have permission to view that bank: " + allowedApi + " != " + api;
        }

        String out = PnwUtil.resourcesToFancyString(totals);
        DiscordUtil.createEmbedCommand(event.getChannel(), args.get(0) + " stockpile", out);
        return null;
    }
}
