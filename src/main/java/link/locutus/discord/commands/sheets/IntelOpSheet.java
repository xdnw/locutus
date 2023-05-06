package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.pnw.Spyop;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class IntelOpSheet extends Command {
    public IntelOpSheet() {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.MILCOM.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " <time> <attackers> <ignore-topX>";
    }

    @Override
    public String desc() {
        return "Generate a list of raidable targets to gather intel on\n" +
                "`<time>` - filters out nations we have loot intel on in that period\n" +
                "`<attackers>` - The nations to assign to do the ops (i.e. your alliance link)\n" +
                "`<ignore-topX>` - filter out top X alliances (e.g. due to DNR), in addition to the set `dnr` coalition\n\n" +
                "Add `-l` to remove targets with loot history\n" +
                "Add `-d` to list targets currently on the dnr\n\n" +
                "e.g. `" + Settings.commandPrefix(true) + "IntelOpSheet 10d 'Error 404' 25`";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        String sheetUrl = DiscordUtil.parseArg(args, "sheet");
        if (args.size() < 2) return usage(event);

        GuildDB db = Locutus.imp().getGuildDB(guild);

        int maxOps = 2;

        long millis = TimeUtil.timeToSec(args.get(0)) * 1000L;
        long cutOff = System.currentTimeMillis() - millis;
        Set<DBNation> attackers = DiscordUtil.parseNations(guild, args.get(1));
        attackers.removeIf(f -> f.getPosition() <= 1 || f.getActive_m() > 1440 || f.getVm_turns() > 0);
        Integer topX = db.getOrNull(GuildDB.Key.DO_NOT_RAID_TOP_X);
        if (args.size() > 2) topX = Integer.parseInt(args.get(2));
        if (topX == null) return usage(event);

        if (attackers.isEmpty()) return usage(event);

        List<DBNation> enemies = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());


        Set<Integer> allies = db.getAllies();
        if (!flags.contains('d')) {
            Function<DBNation, Boolean> canRaid = db.getCanRaid(topX, true);
            enemies.removeIf(f -> !canRaid.apply(f));
        }
        enemies.removeIf(f -> allies.contains(f.getAlliance_id()));
        enemies.removeIf(f -> f.getActive_m() < 4320);
        enemies.removeIf(f -> f.getVm_turns() > 0);
        enemies.removeIf(f -> !f.isGray());
//        enemies.removeIf(f -> f.getCities() < 4);
        enemies.removeIf(f -> f.getAvg_infra() < 300);
        enemies.removeIf(f -> f.getDef() >= 3);

        long currentDate = System.currentTimeMillis();
        Map<DBNation, Double> opValueMap = new HashMap<>();

        Iterator<DBNation> iter = enemies.iterator();
        while (iter.hasNext()) {
            DBNation nation = iter.next();
            Map.Entry<Double, Boolean> opValue = nation.getIntelOpValue();
            if (opValue == null) {
                iter.remove();

//                if (nation.getActive_m() < 4320) continue;
//                if (nation.getVm_turns() != 0) continue;
//                if (!nation.isGray()) continue;
//                if (nation.getDef() == 3) continue;
//
//                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14);
//                Map.Entry<Long, double[]> loot = Locutus.imp().getNationDB().getLoot(nation.getNation_id());
//                if (loot != null && loot.getKey() > cutoff) System.out.println("Looted in past 14d " + nation.getNationUrl());;
//
//                Map.Entry<Long, double[]> lootHistory = Locutus.imp().getWarDb().getNationLoot(nation.getNation_id()).get(nation.getNation_id());
//                if (lootHistory != null && lootHistory.getKey() > cutoff) System.out.println("Spied in past 14d " + nation.getNationUrl());
//
//                long lastActiveDate = currentDate - nation.getActive_m() * 60 * 1000;
//                if (lastActiveDate - 2880 > cutoff) System.out.println("Active in past 16 days " + nation.getNationUrl());;

                continue;
            }
            opValueMap.put(nation, opValue.getKey());
        }

        Collections.sort(enemies, new Comparator<DBNation>() {
            @Override
            public int compare(DBNation o1, DBNation o2) {
                double revenueTime1 = opValueMap.get(o1);
                double revenueTime2 = opValueMap.get(o2);
                return Double.compare(revenueTime2, revenueTime1);
            }
        });

        enemies.addAll(new ArrayList<>(enemies));

        // nations with big trades

        Map<DBNation, List<Spyop>> targets = new HashMap<>();

        ArrayList<DBNation> attackersList = new ArrayList<>(attackers);
        Collections.shuffle(attackersList);

        for (DBNation attacker : attackersList) {
            int numOps = attacker.hasProject(Projects.INTELLIGENCE_AGENCY) ? 2 : 1;
            numOps = Math.min(numOps, maxOps);

            outer:
            for (int i = 0; i < numOps; i++) {
                iter = enemies.iterator();
                while (iter.hasNext()) {
                    DBNation enemy = iter.next();
                    if (!attacker.isInSpyRange(enemy)) continue;
                    List<Spyop> currentOps = targets.computeIfAbsent(enemy, f -> new ArrayList<>());
                    if (currentOps.size() > 1) continue;
                    if (currentOps.size() == 1 && currentOps.get(0).attacker == attacker) continue;
                    Spyop op = new Spyop(attacker, enemy, 1, SpyCount.Operation.INTEL, 0, 3);

                    currentOps.add(op);
                    iter.remove();
                    continue outer;
                }
                break;
            }
        }

        SpreadSheet sheet;
        if (sheetUrl != null) {
            sheet = SpreadSheet.create(sheetUrl);
        } else {
            sheet = SpreadSheet.create(db, SheetKeys.SPYOP_SHEET);
        }

        sheet.clearAll();
        SpySheet.generateSpySheet(sheet, targets);

        sheet.set(0, 0);

        sheet.attach(new DiscordChannelIO(event).create()).send();
        return null;
    }
}