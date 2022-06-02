package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Loot extends Command {
    public Loot() {
        super("loot", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }
    @Override
    public String help() {
        return Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "loot <nation|alliance>";
    }

    @Override
    public String desc() {
        return "Get nation or bank loot history";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        GuildDB db = Locutus.imp().getGuildDB(server);
        if (db != null && db.isWhitelisted() && db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) {
            return super.checkPermission(server, user);
        }
        return false;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) {
            return usage(event);
        }
        if (me == null) {
            return "Please use " + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "validate";
        }

        String arg0 = args.get(0);
        Integer id = DiscordUtil.parseNationId(arg0);

        double percent;

        Map<ResourceType, Double> loot;
        StringBuilder extraInfo = new StringBuilder();
        if (id == null || arg0.contains("/allaince/")) {
            Integer alliance = PnwUtil.parseAllianceId(arg0);
            if (alliance == null) {
                return "Invalid nation or alliance`" + arg0 + "`";
            }
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
            Map.Entry<Long, Map<ResourceType, Double>> allianceLoot = Locutus.imp().getWarDb().getDateAndAllianceBankEstimate(alliance);
            if (allianceLoot == null) return "No loot history";
            loot = allianceLoot.getValue();
            Long date = allianceLoot.getKey();
            extraInfo.append("Last looted: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - date));

            double aaScore = MathMan.parseDouble(Locutus.imp().getPnwApi().getAlliance(alliance).getScore());

            double ratio = (me.getScore() / aaScore) / (5);

            percent = Math.min(ratio, 0.33);
        } else {
            DBNation enemy = Locutus.imp().getNationDB().getNation(id);
            if (enemy == null) return "Unknown nation: " + id;

            percent = me.getWarPolicy() == WarPolicy.PIRATE ? 0.14 : 0.1;
            if (enemy.getWarPolicy() == WarPolicy.MONEYBAGS) percent *= 0.6;

            Map<Integer, Map.Entry<Long,double[]>> nationLootMap = Locutus.imp().getWarDb().getNationLoot(id, true);
            Map.Entry<Long, double[]> nationLoot = nationLootMap.get(id);
            Map.Entry<Long, double[]> spyLoot = Locutus.imp().getNationDB().getLoot(id);

            boolean isSpyLoot = spyLoot != null && (nationLoot == null || spyLoot.getKey() >= nationLoot.getKey());

            double[] knownResources = new double[ResourceType.values.length];
            double[] buffer = new double[knownResources.length];
            double convertedTotal = enemy.estimateRssLootValue(knownResources, nationLoot, buffer, true);
            if (convertedTotal != 0) {
                loot = new LinkedHashMap<>();
                for (int i = 0; i < knownResources.length; i++) {
                    loot.put(ResourceType.values[i], knownResources[i]);
                }
            } else {
                loot = null;
            }

            if (nationLoot != null) {
                double originalValue = PnwUtil.convertedTotal(nationLoot.getValue());
                double originalLootable = originalValue * percent;
                String type = isSpyLoot ? "spy op" : "war loss";
                extraInfo.append("Based on " + type);
                extraInfo.append("(" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - nationLoot.getKey()) + " ago)");
                extraInfo.append(", worth: $" + MathMan.format(originalValue) + "($" + MathMan.format(originalLootable) + " lootable)");
                if (enemy.getActive_m() > 1440) extraInfo.append(" - inactive for " + TimeUtil.secToTime(TimeUnit.MINUTES, enemy.getActive_m()));
            } else {
                extraInfo.append("No spy or beige loot found");;
            }
        }
        me.setMeta(NationMeta.INTERVIEW_LOOT, (byte) 1);

        if (loot == null) {
            return "No loot history";
        }
        Map<ResourceType, Double> yourLoot = loot.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));
        yourLoot = PnwUtil.multiply(yourLoot, percent);

        StringBuilder response = new StringBuilder();
        response.append("Total Stored: ```" + PnwUtil.resourcesToString(loot) + "```You could loot: " + "(worth ~$" + MathMan.format(PnwUtil.convertedTotal(yourLoot)) + ")" +"```" + PnwUtil.resourcesToString(yourLoot) + "```");
        if (extraInfo.length() != 0) response.append("\n`Note: " + extraInfo +"`");
        return response.toString();
    }
}
