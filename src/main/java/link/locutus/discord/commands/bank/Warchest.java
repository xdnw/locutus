package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Warchest extends Command {
    public Warchest() {
        super(CommandCategory.ECON, CommandCategory.MILCOM);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server) && server != null;
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "warchest <*|nations|tax_url> <resources> <note>";
    }

    @Override
    public String desc() {
        return "Determine how much to send to each member to meet their warchest requirements (per city)\n" +
                "Add `-s` to skip checking stockpile";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB guildDb = Locutus.imp().getGuildDB(event);
        if (args.size() < 3) {
            return usage(event, "Current warchest (per city): " + PnwUtil.resourcesToString(guildDb.getPerCityWarchest(me)));
        }

        String note = "#warchest";
        if (args.size() >= 3) note = args.get(2);
        Collection<String> allowedLabels = Arrays.asList("#warchest", "#grant", "#deposit", "#trade", "#ignore", "#tax", "#account");
        if (!allowedLabels.contains(note.split("=")[0])) return "Please use one of the following labels: " + StringMan.getString(allowedLabels);
        Integer aaId = Locutus.imp().getGuildDB(guild).getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId != null) note += "=" + aaId;
        else {
            note += "=" + guild.getIdLong();
        }

        boolean hasEcon = Roles.ECON.has(author, guild);
        Collection<DBNation> nations;
        if (args.get(0).equalsIgnoreCase("*")) {
            if (!hasEcon) {
                return "No permission: " + Roles.ECON.name();
            }
            nations = Locutus.imp().getNationDB().getNations(Collections.singleton(aaId));
        } else {
            nations = DiscordUtil.parseNations(event.getGuild(), args.get(0));
        }
        if (nations.isEmpty()) return "No nations specified";
        if (!hasEcon && (nations.size() != 1 || !nations.iterator().next().equals(me))) return "You only have permission to send to your own nation";


        nations.removeIf(f -> f.getActive_m() > 7200);
        nations.removeIf(f -> f.getPosition() <= 1);
        nations.removeIf(f -> f.getVm_turns() != 0);

        if (nations.isEmpty()) {
            return "No nations in bracket";
        }

        Map<ResourceType, Double> perCity = PnwUtil.parseResources(args.get(1));
        if (perCity.isEmpty()) return "Invalid amount: `" + args.get(1) + "`";

        Map<DBNation, Map<ResourceType, Double>> fundsToSendNations = new LinkedHashMap<>();

        Map<DBNation, Map<ResourceType, Double>> memberResources2 = new HashMap<>();
        boolean skipStockpile = flags.contains('s');
        if (!flags.contains('s')) {
            if (aaId == null) return "No alliance found for this guild. Add `-s` to skip checking stockpile";
            memberResources2 = DBAlliance.getOrCreate(aaId).getMemberStockpile();
        }
        for (DBNation nation : nations) {
            Map<ResourceType, Double> stockpile = memberResources2.getOrDefault(nation, skipStockpile ? Collections.emptyMap() : null);

            if (PnwUtil.convertedTotal(stockpile) < 0) continue;

            Map<ResourceType, Double> toSendCurrent = new HashMap<>();
            for (ResourceType type : perCity.keySet()) {
                double required = perCity.getOrDefault(type, 0d) * nation.getCities();
                double current = stockpile.getOrDefault(type, 0d);
                if (required > current) {
                    toSendCurrent.put(type, required - current);
                }
            }
            if (!toSendCurrent.isEmpty()) {
                fundsToSendNations.put(nation, toSendCurrent);
            }
        }

        String result = Disperse.disperse(guildDb, fundsToSendNations, Collections.emptyMap(), note, new DiscordChannelIO(event), "Send Warchest");
        if (fundsToSendNations.size() > 1) {
            RateLimitUtil.queue(event.getGuildChannel().sendMessage(author.getAsMention()));
        }
        return result;
    }
}
