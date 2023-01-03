package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.CounterType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.TreatyType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CounterSheet extends Command {
    public CounterSheet() {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MILCOM.has(user, server) || Roles.FOREIGN_AFFAIRS.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " [enemy-filter] [ally-filter]";
    }

    @Override
    public String desc() {
        return "Generate a sheet with a list of enemies / nations attacking. (Defaults to those attacking allies)\n" +
                "Please still check the war history in case it is not valid to counter (and add a note to the note column indicating such)\n" +
                "Add `-a` to filter out applicants\n" +
                "Add `-i` to filter out inactive members\n" +
                "Add `-e` to include enemies not attacking";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        String sheetUrl = DiscordUtil.parseArg(args, "sheet");
        if (args.size() > 2) return usage(event);

        // nation info, then attackers att1 att2 att3

        long activeCutoff = flags.contains('a') ? Long.MAX_VALUE : 2880;
        boolean includeProtectorates = true;
        boolean includeCoalition = true;
        boolean includeMDP = true;
        boolean includeODP = true;

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Set<Integer> allies = db.getAllies();
        Set<Integer> protectorates = new HashSet<>();

        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId != null) {
            protectorates = Locutus.imp().getNationDB().getTreaties(aaId, TreatyType.PROTECTORATE).keySet();
            if (includeProtectorates) {
                allies.addAll(protectorates);
            }
            if (includeMDP) {
                allies.addAll(Locutus.imp().getNationDB().getTreaties(aaId, TreatyType.MDP, TreatyType.MDOAP).keySet());
            }
            if (includeODP) {
                allies.addAll(Locutus.imp().getNationDB().getTreaties(aaId, TreatyType.ODP, TreatyType.ODOAP).keySet());
            }
        }

        if (args.size() == 2) {
            allies = DiscordUtil.parseAlliances(guild, args.get(1));
            Set<Integer> finalAllies = allies;
            protectorates.removeIf(f -> !finalAllies.contains(f));
        }

        Set<Integer> enemyAAs = db.getCoalition("enemies");

        Map<DBNation, List<DBWar>> enemies = new HashMap<>();

        List<DBWar> defWars = Locutus.imp().getWarDb().getActiveWarsByAlliance(null, allies);
        for (DBWar war : defWars) {
            if (!war.isActive()) continue;
            DBNation enemy = Locutus.imp().getNationDB().getNation(war.attacker_id);
            if (enemy == null) continue;

            if (!enemyAAs.contains(enemy.getAlliance_id())) {
                CounterStat stat = war.getCounterStat();
                if (stat.type == CounterType.IS_COUNTER || stat.type == CounterType.ESCALATION) continue;
            }

            DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
            if (defender == null) continue;
            if (flags.contains('a') && defender.getPosition() <= 1) continue;
            if (flags.contains('i') && defender.getActive_m() > 4880) continue;
            if (!allies.contains(defender.getAlliance_id())) continue;

            enemies.computeIfAbsent(enemy, f -> new ArrayList<>()).add(war);
        }

        if (flags.contains('e')) {
            for (DBNation enemy : Locutus.imp().getNationDB().getNations(enemyAAs)) {
                enemies.putIfAbsent(enemy, new ArrayList<>());
            }
        }

        if (args.size() >= 1 && !args.get(0).equalsIgnoreCase("*")) {
            Set<DBNation> enemyFilter = DiscordUtil.parseNations(guild, args.get(0));
            enemies.entrySet().removeIf(n -> !enemyFilter.contains(n.getKey()));
        }

        SpreadSheet sheet;
        if (sheetUrl != null) {
            sheet = SpreadSheet.create(sheetUrl);
        } else {
            sheet = SpreadSheet.create(db, GuildDB.Key.COUNTER_SHEET);
        }

        WarCategory warCat = db.getWarChannel();

        List<Object> header = new ArrayList<>(Arrays.asList(
                "note",
                "warroom",
                "nation",
                "alliance",
                "status",
                "def_position",
                "att_dd:hh:mm",
                "def_dd:hh:mm",
                "\uD83D\uDEE1",
                "\uD83C\uDFD9", // cities
                "\uD83C\uDFD7", // avg_infra
                "score",
                "\uD83D\uDC82", // soldiers
                "\u2699", // tanks
                "\u2708", // air
                "\u26F5", // navy
                "def1",
                "def2",
                "def3",
                "def4",
                "def5"
        ));

        Map<Integer, String> notes = new HashMap<>();
        List<List<Object>> rows = sheet.getAll();

        if (rows != null && !rows.isEmpty()) {
            for (int i = 1; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                if (row.size() < 3) {
                    continue;
                }

                Object note = row.get(0);
                if (note == null || note.toString().isEmpty()) {
                    continue;
                }
                Object cell = row.get(1);
                if (cell == null) {
                    continue;
                }
                String nationName = cell + "";
                if (nationName.isEmpty()) continue;

                DBNation nation = DiscordUtil.parseNation(nationName);
                if (nation != null) {
                    notes.put(nation.getNation_id(), note.toString());
                }
            }
        }

        sheet.setHeader(header);

        // sort
        for (Map.Entry<DBNation, List<DBWar>> entry : enemies.entrySet()) {
            DBNation enemy = entry.getKey();
            if (enemy.isBeige() || enemy.getDef() >= 3) continue;

            List<DBWar> wars = entry.getValue();

            int action = 3;
            String[] actions = {"ATTACKING US", "ATTACKING PROTECTORATE", "ATTACKING ALLY", ""};

            int active_m = Integer.MAX_VALUE;
            Rank rank = null;

            for (DBWar war : wars) {
                DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
                if (defender == null) {
                    continue;
                }
                if (rank == null || defender.getPosition() > rank.id) {
                    int position = defender.getPosition();
                    rank = Rank.byId(position);
                }

                active_m = Math.min(active_m, defender.getActive_m());

                if (Integer.valueOf(war.defender_aa).equals(aaId)) {
                    action = Math.min(action, 0);
                } else if (protectorates.contains(war.defender_aa)) {
                    action = Math.min(action, 1);
                } else if (allies.contains(war.defender_aa)) {
                    action = Math.min(action, 2);
                } else {
                    continue;
                }
            }

            String actionStr = actions[action];
            if (enemyAAs.contains(enemy.getAlliance_id())) {
                actionStr = ("ENEMY " + actionStr).trim();
            } else if (wars.isEmpty()) {
                continue;
            }

            if (active_m == Integer.MAX_VALUE) active_m = 0;

            ArrayList<Object> row = new ArrayList<>();
            row.add(notes.getOrDefault(enemy.getNation_id(), ""));

            WarCategory.WarRoom warroom = warCat != null ? warCat.get(enemy, true, false, false) : null;
//            warCat.sync();
            GuildMessageChannel channel = warroom != null ? warroom.getChannel(false) : null;
            if (channel != null) {
                String url = DiscordUtil.getChannelUrl(channel);
                String name = "#" + enemy.getName();
                row.add(MarkupUtil.sheetUrl(name, url));
            } else {
                row.add("");
            }

            row.add(MarkupUtil.sheetUrl(enemy.getNation(), PnwUtil.getUrl(enemy.getNation_id(), false)));
            row.add(MarkupUtil.sheetUrl(enemy.getAllianceName(), PnwUtil.getUrl(enemy.getAlliance_id(), true)));
            row.add(actionStr);
            row.add( rank == null ? "" : rank.name());


            row.add( DurationFormatUtils.formatDuration(enemy.getActive_m() * 60L * 1000, "dd:HH:mm"));
            row.add(DurationFormatUtils.formatDuration(active_m * 60L * 1000, "dd:HH:mm"));
            row.add(enemy.getDef());

            row.add(enemy.getCities());
            row.add(enemy.getAvg_infra());
            row.add(enemy.getScore());

            row.add(enemy.getSoldiers());
            row.add(enemy.getTanks());
            row.add(enemy.getAircraft());
            row.add(enemy.getShips());

            for (int i = 0; i < wars.size(); i++) {
                DBWar war = wars.get(i);
                String url = war.toUrl();
                DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
                String warStr = defender.getNation() + "|" + defender.getAllianceName();
                row.add(MarkupUtil.sheetUrl(warStr, url));
            }

            sheet.addRow(row);
        }

        sheet.clearAll();

        sheet.set(0, 0);

        sheet.attach(new DiscordChannelIO(event).create()).send();
        return null;
    }
}
