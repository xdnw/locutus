package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.task.GetMemberResources;
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
        return (super.checkPermission(server, user) || (Roles.MEMBER.has(user, server) && server != null && Locutus.imp().getGuildDB(server).isAllyOfRoot())) &&
                Roles.ECON.has(user, server);
    }

    @Override
    public String help() {
        return Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "warchest <*|nations|tax_url> <resources> <note>";
    }

    @Override
    public String desc() {
        return "Determine how much to send to each member to meet their warchest requirements (per city)";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 3) {
            GuildDB db = Locutus.imp().getGuildDB(guild);
            return usage(event, "Current warchest (per city): " + PnwUtil.resourcesToString(db.getPerCityWarchest(me)));
        }

        int allianceId = Settings.INSTANCE.getAlliance(event);
        GuildDB guildDb = Locutus.imp().getGuildDB(event);

        String note = "#warchest";
        if (args.size() >= 3) note = args.get(2);
        Collection<String> allowedLabels = Arrays.asList("#warchest", "#grant", "#deposit", "#trade", "#ignore", "#tax", "#account");
        if (!allowedLabels.contains(note.split("=")[0])) return "Please use one of the following labels: " + StringMan.getString(allowedLabels);
        Integer aaId = Locutus.imp().getGuildDB(guild).getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId != null) note += "=" + aaId;
        else {
            note += "=" + guild.getIdLong();
        }

        Collection<DBNation> nations;
        if (args.get(0).equalsIgnoreCase("*")) {
            if (!Roles.ECON.has(author, guild)) {
                return "No permission: " + Roles.ECON.name();
            }
            nations = Locutus.imp().getNationDB().getNations(Collections.singleton(allianceId));
        } else {
            nations = DiscordUtil.parseNations(event.getGuild(), args.get(0));
        }

        nations.removeIf(f -> f.getActive_m() > 7200);
        nations.removeIf(f -> f.getPosition() <= 1);
        nations.removeIf(f -> f.getVm_turns() != 0);

        if (nations.isEmpty()) {
            return "No nations in bracket";
        }

        Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());

        Map<ResourceType, Double> perCity = PnwUtil.parseResources(args.get(1));
        if (perCity.isEmpty()) return "Invalid amount: `" + args.get(1) + "`";

        Map<DBNation, Map<ResourceType, Double>> fundsToSendNations = new LinkedHashMap<>();

        Map<Integer, Map<ResourceType, Double>> memberResources = new GetMemberResources(allianceId).call();
        for (Map.Entry<Integer, Map<ResourceType, Double>> entry : memberResources.entrySet()) {
            int nationId = entry.getKey();
            if (!nationIds.contains(nationId)) continue;
            DBNation nation = DBNation.byId(nationId);
            if (PnwUtil.convertedTotal(entry.getValue()) < 0) continue;

            Map<ResourceType, Double> stockpile = entry.getValue();
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

        String result = Disperse.disperse(guildDb, fundsToSendNations, Collections.emptyMap(), note, event.getGuildChannel(), "Send Warchest");
        if (fundsToSendNations.size() > 1) {
            RateLimitUtil.queue(event.getGuildChannel().sendMessage(author.getAsMention()));
        }
        return result;
    }
}
