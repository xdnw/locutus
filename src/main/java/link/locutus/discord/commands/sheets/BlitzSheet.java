package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.sheet.SheetUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import com.google.api.client.util.Lists;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.RowData;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class BlitzSheet extends Command {
    public BlitzSheet() {
        super("BlitzSheet", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + getClass().getSimpleName() + " <attackers> <defenders> [max-off=3] [same-aa-priority=0] [same-activity-priority=0] [turn=-1] [att-activity-threshold=0.5] [def-activity-threshold=0.1] [guilds]";
    }

    @Override
    public String desc() {
        return "Generates a Blitz sheet.\n" +
                "`attackers`: are the nations that should be used for the attackers (can be a google sheet)\n" +
                "`defenders`: are the nations that should be used for the defenders\n" +
                "`max-off`: How many offensive slots a nation can have (defaults to 3)\n" +
                "`same-aa-priority`: Value between 0 and 1 to prioritize assigning a target to nations in the same AA\n" +
                "`same-activity-priority`: Value between 0 and 1 to prioritize assigning targets to nations with similar activity patterns\n" +
                "`turn`: The turn in the day (between 0 and 11) when you expect the blitz to happen\n" +
                "`att-activity-threshold`: A value between 0 and 1 to filter out attackers below this level of daily activity (default: 0.5, which is 50%)\n" +
                "`def-activity-threshold`: A value between 0 and 1 to filter out defenders below this level of activity (default: 0.1)\n" +
                "`guilds`: A comma separated list of discord guilds (their id), to use to check nation activity/roles (nations must be registered)\n\n" +
                "Add `-s` to process slotted enemies\n" +
                "Add `-e` to only assign down declares";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MILCOM.has(user, server) && Locutus.imp().getGuildDB(server).isValidAlliance();
    }

    public String getAttackerNote(DBNation nation) {
        StringBuilder note = new StringBuilder();

        double score = nation.getScore();
        double minScore = Math.ceil(nation.getScore() * 0.75);
        double maxScore = Math.floor(nation.getScore() * 1.75);
        note.append("War Range: " + MathMan.format(minScore) + "-" + MathMan.format(maxScore) + " (" + score + ")").append("\n");
        note.append("ID: " + nation.getNation_id()).append("\n");
        note.append("Alliance: " + nation.getAllianceName()).append("\n");
        note.append("Cities: " + nation.getCities()).append("\n");
        note.append("avg_infra: " + nation.getAvg_infra()).append("\n");
        note.append("soldiers: " + nation.getSoldiers()).append("\n");
        note.append("tanks: " + nation.getTanks()).append("\n");
        note.append("aircraft: " + nation.getAircraft()).append("\n");
        note.append("ships: " + nation.getShips()).append("\n");
        return note.toString();
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        String sheetUrl = DiscordUtil.parseArg(args, "sheet");
        if (args.size() < 2) {
            return usage(event);
        }

        Set<DBNation> attNations = DiscordUtil.parseNations(guild, args.get(0));
        Set<DBNation> defNations = DiscordUtil.parseNations(guild, args.get(1));

        if (attNations == null || attNations.isEmpty())  return "Invalid alliance or coalition: `" + args.get(0) + "`";
        if (defNations == null || defNations.isEmpty())  return "Invalid alliance or coalition: `" + args.get(1) + "`";

        GuildDB guildDB = Locutus.imp().getGuildDB(event);
        if (guildDB == null) {
            return "This command must be run from a valid guild";
        }

        Integer maxOff = args.size() >= 3 ? Integer.parseInt(args.get(2)) : 3;
        Double sameAAPriority = args.size() >= 4 ? Double.parseDouble(args.get(3)) : 0d;
        Double sameActivityPriority = args.size() >= 5 ? Double.parseDouble(args.get(4)) : 0d;

        int turn = -1;
        if (args.size() >= 6) turn = Integer.parseInt(args.get(5));

        double attActivity = args.size() >= 7 ? Double.parseDouble(args.get(6)) : 0.5;
        double defActivity = args.size() >= 8 ? Double.parseDouble(args.get(7)) : 0.1;

        Set<Long> guilds = new HashSet<>();

        if (args.size() == 9) {
            for (String guildIdStr : args.get(8).split(",")) {
                guilds.add(Long.parseLong(guildIdStr));
            }
        }

        BlitzGenerator blitz = new BlitzGenerator(turn, maxOff, sameAAPriority, sameActivityPriority, attActivity, defActivity, guilds, flags.contains('s'));
        blitz.addNations(attNations, true);
        blitz.addNations(defNations, false);
        if (flags.contains('s')) blitz.removeSlotted();

        Map<DBNation, List<DBNation>> targets;
        if (flags.contains('e')) {
            targets = blitz.assignEasyTargets();
        } else {
            targets = blitz.assignTargets();
        }

        SpreadSheet sheet;
        if (sheetUrl != null) {
            sheet = SpreadSheet.create(sheetUrl);
        } else {
            sheet = SpreadSheet.create(guildDB, GuildDB.Key.ACTIVITY_SHEET);
        }

        List<RowData> rowData = new ArrayList<RowData>();

        List<Object> header = new ArrayList<>(Arrays.asList(
                "alliance",
                "nation",
                "cities",
                "infra",
                "soldiers",
                "tanks",
                "planes",
                "ships",
                "spies",
                "money",
                "resources",
                "score",
                "beige",
                "inactive",
                "login_chance",
                "weekly_activity",
                "att1",
                "att2",
                "att3"
        ));

        rowData.add(SheetUtil.toRowData(header));

        Map<Integer, Map.Entry<Long, double[]>> nationLoot = Locutus.imp().getWarDb().getNationLoot();

        for (Map.Entry<DBNation, List<DBNation>> entry : targets.entrySet()) {
            DBNation defender = entry.getKey();
            List<DBNation> attackers = entry.getValue();
            ArrayList<Object> row = new ArrayList<>();
            row.add(MarkupUtil.sheetUrl(defender.getAllianceName(), defender.getAllianceUrl()));

//                if (flags.contains('i')) {
//                    row.set(1, defender.getNation_id());
//                } else
            {
                row.add(MarkupUtil.sheetUrl(defender.getNation(), defender.getNationUrl()));
            }

            row.add(defender.getCities());
            row.add(defender.getAvg_infra());
            row.add(defender.getSoldiers() + "");
            row.add(defender.getTanks() + "");
            row.add(defender.getAircraft() + "");
            row.add(defender.getShips() + "");
            row.add(defender.getSpies() + "");

            double[] knownResources = new double[ResourceType.values.length];
            double[] buffer = new double[knownResources.length];
            Map.Entry<Long, double[]> loot = nationLoot == null ? null : nationLoot.get(defender.getNation_id());
            double convertedTotal = defender.estimateRssLootValue(knownResources, loot, buffer, false);
            row.add(PnwUtil.convertedTotal(knownResources) + "");
            row.add(PnwUtil.resourcesToString(knownResources));
            row.add(defender.getScore() + "");
            row.add(defender.getBeigeTurns() + "");
            row.add(TimeUtil.secToTime(TimeUnit.MINUTES, defender.getActive_m()));

            Activity activity = defender.getActivity(12 * 7 * 2);
            double loginChance = activity.loginChance(turn == -1 ? 11 : turn, 48, false);
            row.add(loginChance);
            row.add(activity.getAverageByDay());

            List<DBNation> myCounters = targets.getOrDefault(defender, Collections.emptyList());

            for (int i = 0; i < myCounters.size(); i++) {
                DBNation counter = myCounters.get(i);
                String counterUrl = MarkupUtil.sheetUrl(counter.getNation(), counter.getNationUrl());
                row.add(counterUrl);
            }
            RowData myRow = SheetUtil.toRowData(row);
            List<CellData> myRowData = myRow.getValues();
            int attOffset = myRowData.size() - myCounters.size();
            for (int i = 0; i < myCounters.size(); i++) {
                DBNation counter = myCounters.get(i);
                myRowData.get(attOffset + i).setNote(getAttackerNote(counter));
            }
            myRow.setValues(myRowData);

            rowData.add(myRow);
        }

        sheet.clear("A:Z");
        sheet.write(rowData);

        return "<" + sheet.getURL() + "> " + author.getAsMention();

//        int att1Index = 13;

//
//        Map<Integer, List<DBNation>> counters = new HashMap<>();
//        Map<Integer, List<DBNation>> targets = new HashMap<>();
//        Set<Integer> strongTargets = new HashSet<>();
//
//        List<List<Object>> existing = sheet.get(0, 1, header.size() - 1, defNations.size() + 50);
//        if (existing != null) {
//            for (List<Object> row : existing) {
//                Object obj1 = row.get(1);
//                if (obj1 == null) continue;
//                String nationName = obj1.toString();
//                if (nationName.isEmpty()) continue;
//                DBNation nation = Locutus.imp().getNationDB().getNationByName(nationName);
//                if (nation == null) continue;
//                int rowLen = Math.min(row.size(), att1Index + 3);
//                for (int i = att1Index; i < rowLen; i++) {
//                    Object attObj = row.get(i);
//                    if (attObj == null) continue;
//                    String attName = attObj.toString();
//                    if (attName.isEmpty()) continue;
//
//                    DBNation attNation = Locutus.imp().getNationDB().getNationByName(attName);
//                    if (attNation == null) continue;
//                    counters.computeIfAbsent(nation.getNation_id(), f -> Lists.newArrayList()).add(attNation);
//                    targets.computeIfAbsent(attNation.getNation_id(), f -> Lists.newArrayList()).add(nation);
//
//                    if (nation.getAircraft() > attNation.getAircraft() * 0.66) {
//                        strongTargets.add(attNation.getNation_id());
//                    }
//                }
//            }
//        }
//
//        List<DBNation> attNations = Locutus.imp().getNationDB().getNations(attackers);
//
//        defNations.sort((o1, o2) -> Integer.compare(o2.getAircraft(), o1.getAircraft()));
//        attNations.sort((o1, o2) -> Integer.compare(o2.getAircraft(), o1.getAircraft()));
//
////        assignTargets(attNations, defNations, counters, targets, strongTargets, new BiFunction<DBNation, DBNation, Boolean>() {
////            @Override
////            public Boolean apply(DBNation nation, DBNation nation2) {
////                return null;
////            }
////        });
//
//        if (findTargets) {
//            for (int i = 0; i < defNations.size(); i++) {
//                DBNation defender = defNations.get(i);
//                if (defender.isBeige()) continue;
//                if (defender.getVm_turns() > 0) continue;
//                if (defender.getActive_m() > 10000) continue;
//                List<DBNation> myCounters = counters.computeIfAbsent(defender.getNation_id(), f -> Lists.newArrayList());
//                if (myCounters.size() >= 3) continue;
//                // Ground is 1/3 stronger
//                // air is stronger
//                for (DBNation attacker : attNations) {
//                    if (attacker.getVm_turns() > 0) continue;
//                    if (attacker.getActive_m() > 10000) continue;
//                    if (attacker.getScore() < defender.getScore() * 0.75 || attacker.getScore() * 0.75 > defender.getScore())
//                        continue;
//                    List<DBNation> myTargets = targets.computeIfAbsent(attacker.getNation_id(), f -> Lists.newArrayList());
//                    if (myTargets.size() >= 5) continue;
//                    boolean strong = defender.getAircraft() > attacker.getAircraft() * 0.66;
//                    if (strong) {
//                        if (defender.getAircraft() > attacker.getAircraft()) {
//                            double defGround = defender.getSoldiers() * 1.75 + defender.getTanks() * 40;
//                            double attGround = attacker.getSoldiers() * 1.75 + attacker.getTanks() * 40;
//                            if (defGround > attGround * 0.33) {
//                                continue;
//                            }
//                        }
//                        if (strongTargets.contains(attacker.getNation_id())) continue;
//                        strongTargets.add(attacker.getNation_id());
//                    }
//
//                    counters.computeIfAbsent(defender.getNation_id(), f -> Lists.newArrayList()).add(attacker);
//                    targets.computeIfAbsent(attacker.getNation_id(), f -> Lists.newArrayList()).add(defender);
//
//                    if (myCounters.size() >= 3) break;
//                }
//            }
//        }
//
//        sheet.setHeader(header);
//
//        Map<Integer, Map.Entry<Long, Map<ResourceType, Long>>> nationLoot = Locutus.imp().getWarDB().getNationLoot();
//
//        for (DBNation defender : defNations) {
//            header.set(0, MarkupUtil.sheetUrl(defender.getAlliance(), defender.getAllianceUrl()));
//
//            if (flags.contains('i')) {
//                header.set(1, defender.getNation_id());
//            } else {
//                header.set(1, MarkupUtil.sheetUrl(defender.getNation(), defender.getNationUrl()));
//            }
//
//            header.set(2, defender.getAvg_infra());
//            header.set(3, defender.getSoldiers() + "");
//            header.set(4, defender.getTanks() + "");
//            header.set(5, defender.getAircraft() + "");
//            header.set(6, defender.getShips() + "");
//            header.set(7, defender.getSpies() + "");
//
//            double[] knownResources = new double[ResourceType.values.length];
//            double[] buffer = new double[knownResources.length];
//            Map.Entry<Long, Map<ResourceType, Long>> loot = nationLoot == null ? null : nationLoot.get(defender.getNation_id());
//            double convertedTotal = defender.estimateRssLootValue(knownResources, loot, buffer, false);
//
//            header.set(8, PnwUtil.convertedTotal(knownResources) + "");
//            header.set(9, PnwUtil.resourcesToString(knownResources));
//            header.set(10, defender.getScore() + "");
//            header.set(11, defender.getBeigeTurns() + "");
//            header.set(12, TimeUtil.secToTime(TimeUnit.MINUTES, defender.getActive_m()));
//
//            header.set(13, "");
//            header.set(14, "");
//            header.set(15, "");
//
//            List<DBNation> myCounters = counters.getOrDefault(defender.getNation_id(), Collections.emptyList());
//            for (int i = 0; i < myCounters.size(); i++) {
//                DBNation counter = myCounters.get(i);
//                String counterUrl = MarkupUtil.sheetUrl(counter.getNation(), counter.getNationUrl());
//                header.set(i + att1Index, counterUrl);
//            }
//
//            sheet.setHeader(header);
//        }
//
//        sheet.clearAll();
//        sheet.set(0, 0);
//
//        return "<" + sheet.getURL() + ">";
    }

    public void assignTargets(List<DBNation> attNations, List<DBNation> defNations, Map<Integer, List<DBNation>> counters, Map<Integer, List<DBNation>> targets, Set<Integer> strongTargets, BiFunction<DBNation, DBNation, Boolean> attDefIsAllowed) {
        for (int i = 0; i < defNations.size(); i++) {
            DBNation defender = defNations.get(i);
            if (defender.isBeige()) continue;
            if (defender.getVm_turns() > 0) continue;
            if (defender.getActive_m() > 10000) continue;
            List<DBNation> myCounters = counters.computeIfAbsent(defender.getNation_id(), f -> Lists.newArrayList());
            if (myCounters.size() >= 3) continue;
            for (DBNation attacker : attNations) {
                if (attacker.getVm_turns() > 0) continue;
                if (attacker.getActive_m() > 10000) continue;
                if (attacker.getScore() < defender.getScore() * 0.75 || attacker.getScore() * 0.75 > defender.getScore())
                    continue;
                List<DBNation> myTargets = targets.computeIfAbsent(attacker.getNation_id(), f -> Lists.newArrayList());
                if (myTargets.size() >= 5) continue;
                boolean strong = defender.getAircraft() > attacker.getAircraft() * 0.66;
                if (strong) {
                    if (defender.getAircraft() > attacker.getAircraft()) {
                        double defGround = defender.getSoldiers() * 1.75 + defender.getTanks() * 40;
                        double attGround = attacker.getSoldiers() * 1.75 + attacker.getTanks() * 40;
                        if (defGround > attGround * 0.33) {
                            continue;
                        }
                    }
                    if (strongTargets.contains(attacker.getNation_id())) continue;
                    strongTargets.add(attacker.getNation_id());
                }

                counters.computeIfAbsent(defender.getNation_id(), f -> Lists.newArrayList()).add(attacker);
                targets.computeIfAbsent(attacker.getNation_id(), f -> Lists.newArrayList()).add(defender);

                if (myCounters.size() >= 3) break;
            }
        }
    }
}
