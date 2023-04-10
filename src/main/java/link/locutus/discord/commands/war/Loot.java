package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.LootEntry;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
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
        return Settings.commandPrefix(true) + "loot <nation|alliance>";
    }

    @Override
    public String desc() {
        return "Get nation or bank loot history";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null) return false;
        if (nation.getAlliance_id() == 0 || nation.getPositionEnum().id <= 1 || nation.getAlliance_id() == 6143) return false;
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) {
            return usage(event);
        }
        if (me == null) {
            return "Please use " + Settings.commandPrefix(true) + "validate";
        }

        String arg0 = args.get(0);
        Integer id = DiscordUtil.parseNationId(arg0);

        double percent;

        double[] loot;
        StringBuilder extraInfo = new StringBuilder();
        if (id == null || arg0.contains("/allaince/")) {
            DBAlliance alliance = DBAlliance.parse(arg0, true);
            if (alliance == null) {
                return "Invalid nation or alliance`" + arg0 + "`";
            }
            LootEntry allianceLoot = alliance.asAlliance().getLoot();
            if (allianceLoot == null) return "No loot history";
            loot = allianceLoot.getTotal_rss();
            Long date = allianceLoot.getDate();
            extraInfo.append("Last looted: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - date));

            double aaScore = alliance.getScore();

            double score = me.getScore();
            double ratio = ((score * 10000) / aaScore) / 2d;
            percent = Math.min(Math.min(ratio, 10000) / 30000, 0.33);

        } else {
            DBNation enemy = Locutus.imp().getNationDB().getNation(id);
            if (enemy == null) return "Unknown nation: " + id;

            percent = me.getWarPolicy() == WarPolicy.PIRATE ? 0.14 : 0.1;
            if (enemy.getWarPolicy() == WarPolicy.MONEYBAGS) percent *= 0.6;

            LootEntry lootInfo = enemy.getBeigeLoot();

            double[] knownResources = new double[ResourceType.values.length];
            double[] buffer = new double[knownResources.length];
            double convertedTotal = enemy.estimateRssLootValue(knownResources, lootInfo, buffer, true);
            if (convertedTotal != 0) {
                loot = knownResources;
            } else {
                loot = null;
            }

            if (lootInfo != null) {
                double originalValue = lootInfo.convertedTotal();
                double originalLootable = originalValue * percent;
                String type = lootInfo.getType().name();
                extraInfo.append("Based on " + type);
                extraInfo.append("(" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - lootInfo.getDate()) + " ago)");
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
        Map<ResourceType, Double> yourLoot = PnwUtil.resourcesToMap(loot);
        yourLoot = PnwUtil.multiply(yourLoot, percent);

        StringBuilder response = new StringBuilder();
        response.append("Total Stored: ```" + PnwUtil.resourcesToString(loot) + "```You could loot: " + "(worth ~$" + MathMan.format(PnwUtil.convertedTotal(yourLoot)) + ")" +"```" + PnwUtil.resourcesToString(yourLoot) + "```");
        if (extraInfo.length() != 0) response.append("\n`Note: " + extraInfo +"`");
        return response.toString();
    }
}
