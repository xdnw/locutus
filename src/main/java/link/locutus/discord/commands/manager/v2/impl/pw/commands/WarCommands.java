package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.CoalitionPermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.sheets.SpySheet;
import link.locutus.discord.config.Messages;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.BeigeReason;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.Spyop;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.battle.SpyBlitzGenerator;
import link.locutus.discord.util.battle.sim.WarNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.sheet.SheetUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.war.WarCard;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.RowData;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.json.JSONObject;
import rocker.guild.ia.message;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WarCommands {

    @Command(desc = "Allow receiving automatic beige alerts a certain amount below your current war range")
    @WhitelistPermission
    @CoalitionPermission(Coalition.RAIDPERMS)
    public String setBeigeAlertScoreLeeway(@Me DBNation me, double scoreLeeway) {
        me.setMeta(NationMeta.BEIGE_ALERT_SCORE_LEEWAY, scoreLeeway);
        return "Set beige alert score leeway to " + MathMan.format(scoreLeeway) + "ns";
    }

    @Command(desc = "Set the required amount of loot for automatic beige alerts\n" +
            "Defaults to 15m", aliases = {"beigeAlertRequiredLoot", "setBeigeAlertRequiredLoot"})
    @WhitelistPermission
    @CoalitionPermission(Coalition.RAIDPERMS)
    public String beigeAlertRequiredLoot(@Me DBNation me, double requiredLoot) {
        me.setMeta(NationMeta.BEIGE_ALERT_REQUIRED_LOOT, requiredLoot);
        return "Set beige alert required loot to $" + MathMan.format(requiredLoot);
    }

    @Command(desc = "Set the types of nations to receive automatic beige alerts for", aliases = {"beigeAlertMode", "setBeigeAlertMode"})
    @WhitelistPermission
    @CoalitionPermission(Coalition.RAIDPERMS)
    public String beigeAlertMode(@Me DBNation me, NationMeta.BeigeAlertMode mode) {
        me.setMeta(NationMeta.BEIGE_ALERT_MODE, (byte) mode.ordinal());
        if (mode == NationMeta.BeigeAlertMode.NO_ALERTS) {
            Set<DBNation> reminders = Locutus.imp().getNationDB().getBeigeRemindersByAttacker(me);
            for (DBNation nation : reminders) {
                Locutus.imp().getNationDB().deleteBeigeReminder(me.getNation_id(), nation.getNation_id());
            }
        }
        return "Set beige alert mode to " + mode;
    }

    @Command(desc = "Only get the automatic beige alerts if you have the specified status on discord\n" +
            "Note: You will still receive alerts for targets you have subscribed to via `{prefix}beigeReminder <nation>`",
            aliases = {"beigeAlertRequiredStatus", "setBeigeAlertRequiredStatus"})
    @WhitelistPermission
    @CoalitionPermission(Coalition.RAIDPERMS)
    public String beigeAlertRequiredStatus(@Me DBNation me, NationMeta.BeigeAlertRequiredStatus status) {
        me.setMeta(NationMeta.BEIGE_ALERT_REQUIRED_STATUS, (byte) status.ordinal());
        return "Set beige alert required status to " + status;
    }

    @Command(desc = "List your current beige reminders", aliases = {"beigeReminders", "listBeigeReminders"})
    @WhitelistPermission
    @CoalitionPermission(Coalition.RAIDPERMS)
    public String beigeReminders(@Me DBNation me) {
        Set<DBNation> reminders = Locutus.imp().getNationDB().getBeigeRemindersByAttacker(me);
        if (reminders.isEmpty()) return "You have no beige reminders set.";

        StringBuilder response = new StringBuilder();
        response.append("**" + me.getNation() + "**").append(me.toMarkdown()).append("\n**Reminders**\n");
        for (DBNation target : reminders) {
            response.append(target.toMarkdown()).append('\n');
        }
        return response.toString();
    }

    @Command(desc = "Remove a beige reminder", aliases = {"removeBeigeReminder", "deleteBeigeReminder"})
    public String removeBeigeReminder(@Me DBNation me, Set<DBNation> nationsToRemove) {
        Set<DBNation> reminders = Locutus.imp().getNationDB().getBeigeRemindersByAttacker(me);
        Set<DBNation> toRemove = new HashSet<>();
        for (DBNation nation : nationsToRemove) {
            if (reminders.contains(nation)) toRemove.add(nation);
        }

        if (toRemove.isEmpty()) return "No nations selected for removal. For a list of your current reminders, use " + CM.alerts.beige.beigeReminders.cmd.toSlashMention() + "";

        StringBuilder response = new StringBuilder();
        for (DBNation nation : toRemove) {
            Locutus.imp().getNationDB().deleteBeigeReminder(me.getNation_id(), nation.getNation_id());
            response.append("Removed reminder for <" + nation.getNationUrl() + ">\n");
        }
        return response.toString();
    }

    @Command(desc = "Set a reminder for when a nation leaves beige or VM", aliases = {"beigeAlert", "setAlert", "beigeReminder", "setBeigeReminder", "addBeigeReminder"})
    @WhitelistPermission
    @CoalitionPermission(Coalition.RAIDPERMS)
    @RolePermission(Roles.BEIGE_ALERT)
    public String beigeReminder(@Me GuildDB db, @Me DBNation me, @Filter("*,#color=beige,#vm_turns=0,#warrange={score}||*,#vm_turns>0,#vm_turns<168,#warrange={score}") Set<DBNation> targets, @Default Double requiredLoot, @Switch("s") boolean allowOutOfScore) {
        Function<DBNation, Boolean> canRaid = db.getCanRaid();

        if (!allowOutOfScore) {
            double score = me.getScore();
            ByteBuffer scoreLeewayBuf = me.getMeta(NationMeta.BEIGE_ALERT_SCORE_LEEWAY);
            double scoreLeeway = scoreLeewayBuf == null ? 0 : scoreLeewayBuf.getDouble();
            targets.removeIf(f -> f.getScore() < score * 0.75 - scoreLeeway || f.getScore() > score * 1.75);
        }

        targets.removeIf(f -> !canRaid.apply(f));
        targets.removeIf(f -> !f.isBeige() && (f.getVm_turns() == 0 || f.getVm_turns() > 14 * 12));
        targets.removeIf(f -> f.getVm_turns() > 14 * 12);

        if (requiredLoot != null && requiredLoot != 0) {
            targets.removeIf(f -> f.lootTotal() < requiredLoot);
        }

        if (targets.isEmpty()) {
            return "No suitable targets found. Are you sure you specified a nation you are allowed to raid that is currently in beige?";
        }

        StringBuilder response = new StringBuilder();

        for (DBNation target : targets) {

            long turns = target.isBeige() ? target.getBeigeTurns() : target.getVm_turns();
            long turnEnd = TimeUtil.getTurn() + turns;
            long diff = TimeUtil.getTimeFromTurn(turnEnd) - System.currentTimeMillis();
            String diffStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff);

            if (diff < TimeUnit.MINUTES.toMillis(6)) {
                response.append(target.getDeclareUrl() + " leaves beige next turn  (in " + diffStr + " OR " + turns + " turns) - NO REMINDER SET\n");
                continue;
            }

            Locutus.imp().getNationDB().addBeigeReminder(target, me);

            response.append("Added beige reminder for " + target.getNationUrl() + " (in " + diffStr + " OR " + turns + " turns)\n");
        }
        response.append("\nSee also:\n" +
                " - " + CM.alerts.beige.beigeReminders.cmd.toSlashMention() + "\n" +
                " - `" + Settings.commandPrefix(false) + "removeBeigeReminder <nation>`\n" +
                " - " + CM.alerts.beige.beigeAlertRequiredStatus.cmd.toSlashMention() + "\n" +
                " - " + CM.alerts.beige.beigeAlertMode.cmd.toSlashMention() + "\n" +
                " - " + CM.alerts.beige.beigeAlertRequiredLoot.cmd.toSlashMention() + "\n" +
                " - " + CM.alerts.beige.setBeigeAlertScoreLeeway.cmd.toSlashMention() + "");

        return response.toString();
    }

    @Command(desc = "Get a raw list of nones in war range. This is not sorted by loot")
    @RolePermission(value = {Roles.MEMBER, Roles.APPLICANT}, any=true)
    public String raidNone(@Me User author, @Me GuildDB db, @Me DBNation me, Set<DBNation> nations, @Default("5") Integer numResults, @Switch("s") Double score) {
        if (score == null) score = me.getScore();
        Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);

        nations.removeIf(f -> (f.getAlliance_id() != 0 && !enemies.contains(f.getAlliance_id())) || f.getDef() >= 3 || f.getVm_turns() > 0);

        Double finalScore = score;
        nations.removeIf(f -> f.getScore() < finalScore * 0.75 || f.getScore() > finalScore * 1.75);

        nations.removeIf(f -> f.getGroundStrength(true, false) > me.getGroundStrength(false, false) * 0.4 + 10000);

        boolean hasNonBeige = false;
        for (DBNation nation : nations) {
            if (!nation.isBeige()) {
                hasNonBeige = true;
                break;
            }
        }
        if (hasNonBeige) {
            nations.removeIf(f -> f.isBeige());
        }


        if (nations.isEmpty()) return "No targets found";

        List<DBNation> list = new ArrayList<>(nations);
        list.sort(new Comparator<DBNation>() {
            @Override
            public int compare(DBNation o1, DBNation o2) {
                double val1 = o1.getActive_m() * MathMan.sqr(o1.getAvg_infra());
                double val2 = o2.getActive_m() * MathMan.sqr(o2.getAvg_infra());
                return Double.compare(val2, val1);
            }
        });

        StringBuilder response = new StringBuilder("**Results for " + me.getNation() + "**:\n");
        int count = 0;
        for (DBNation nation : list) {
            if (count++ == numResults) break;
            response.append(nation.toMarkdown(true, false, false, false));
        }


        StringBuilder warnings = new StringBuilder();
        if (me.getSoldiers() == 0) {
            warnings.append(" - You do not have any soldiers, which should be used for raiding as other units aren't cost effective.\n");
        }
        if (me.getTanks() != 0) {
            warnings.append(" - We don't recommend raiding with tanks because they are unable to loot nations with any cost efficiency.\n");
        }
        if (me.getWarPolicy() != WarPolicy.PIRATE) {
            warnings.append(" - Using the pirate policy will increase loot by 40%\n");
        }
        if (warnings.length() != 0) {
            response.append("\n```").append(warnings.toString().trim()).append("```");
        }
        return response.toString();
    }

    @Command(desc = "List your wars you can possible beige")
    @RolePermission(Roles.MEMBER)
    public String canIBeige(@Me IMessageIO channel, @Me GuildDB db, @Me DBNation me, @Default DBNation nation) {
        if (nation == null) nation = me;
        if (nation.getNumWars() == 0) return nation.getNation() + " is not in any wars";
        if (db.getCoalition(Coalition.ENEMIES).contains(nation.getAlliance_id())) return "This command takes your own nation as the argument, not the enemy";

        Map<CityRanges, Set<BeigeReason>> allowedRanges = db.getOrThrow(GuildDB.Key.ALLOWED_BEIGE_REASONS);

        Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);

        String explanation = db.getHandler().getBeigeCyclingInfo(Collections.singleton(BeigeReason.BEIGE_CYCLE), false);
        channel.send(explanation);

        List<DBWar> wars = nation.getActiveWars();
        for (DBWar war : wars) {
            DBNation enemy = war.getNation(!war.isAttacker(nation));

            String title = (war.isAttacker(nation) ? "Off" : "Def") + ": " + enemy.getNation() + " | " + enemy.getAllianceName();
            StringBuilder body = new StringBuilder();
            body.append(war.toUrl()).append("\n");
            String info = war.getWarInfoEmbed(war.isAttacker(nation), true);
            body.append(info);
            body.append("\nBeige:");

            if (enemy.getActive_m() > 10000) {
                body.append("**YES** (inactive)");
            } else if (!enemies.contains(enemy.getAlliance_id())) {
                body.append("**YES** (not an enemy)");
            } else {
                List<BeigeReason> permitted = new ArrayList<>(BeigeReason.getAllowedBeigeReasons(db, nation, war, null));

                if (permitted.isEmpty()) {
                    body.append("**AVOID DEFEATING** (ping milcom for more info, or assistance)");
                } else {
                    Collections.sort(permitted);
                    BeigeReason firstReason = permitted.get(0);
                    body.append("**YES**");
                    if (firstReason.getApproveMessage() != null) {
                        body.append(" (" + firstReason.getApproveMessage() + ")");
                    }
                    body.append("\n");
                    for (BeigeReason reason : permitted) {
                        body.append(" - " + reason + ": " + reason.getDescription() + "\n");
                    }

                }
            }

            channel.create().embed(title, body.toString()).send();

        }
        return "Notes:\n" +
                " - These results are only valid if you are beiging right now. i.e. Do not consider it valid if another nation beiges the enemy first." +
                " - Remember to talk in your war rooms, and if sitting on a weakened enemy, to keep a blockade up";
    }

    private static Map<Integer, Long> alreadySpied = new ConcurrentHashMap<>();
    @Command(desc = "Find nations to gather intel on (sorted by infra * days since last intel)")
    @RolePermission(Roles.MEMBER)
    public String intel(@Me IMessageIO channel, @Me GuildDB db, @Me DBNation me, @Default Integer dnrTopX, @Switch("d") boolean useDNR) {
        if (dnrTopX == null) {
            dnrTopX = db.getOrNull(GuildDB.Key.DO_NOT_RAID_TOP_X);
            if (dnrTopX == null) dnrTopX = 0;
        }

        List<DBNation> enemies = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());

        Set<Integer> allies = db.getAllies(true);

        Function<DBNation, Boolean> raidList = db.getCanRaid(dnrTopX, true);
        Set<Integer> enemyCoalitions = db.getCoalition("enemies");
        Set<Integer> targetCoalitions = db.getCoalition("targets");

        if (useDNR) {
            enemies.removeIf(f -> !raidList.apply(f));
        }

        enemies.removeIf(f -> allies.contains(f.getAlliance_id()));
        enemies.removeIf(f -> f.getActive_m() < 4320);
        enemies.removeIf(f -> f.getVm_turns() > 0);
        enemies.removeIf(f -> f.isBeige());
        if (me.getCities() > 3) enemies.removeIf(f -> f.getCities() < 4 || f.getScore() < 500);
        enemies.removeIf(f -> f.getDef() == 3);
        enemies.removeIf(nation ->
                nation.getActive_m() < 12000 &&
                        nation.getGroundStrength(true, false) > me.getGroundStrength(true, false) &&
                        nation.getAircraft() > me.getAircraft() &&
                        nation.getShips() > me.getShips() + 2);
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30);
        enemies.removeIf(f -> alreadySpied.getOrDefault(f.getNation_id(), 0L) > cutoff);

        if (false) {
            Set<DBNation> myAlliance = Locutus.imp().getNationDB().getNations(Collections.singleton(me.getAlliance_id()));
            myAlliance.removeIf(f -> f.getActive_m() > 2440 || f.getVm_turns() != 0);
            BiFunction<Double, Double, Integer> range = PnwUtil.getIsNationsInScoreRange(myAlliance);
            enemies.removeIf(f -> range.apply(f.getScore() / 1.75, f.getScore() / 0.75) <= 0);
        } else {
            List<DBNation> tmp = new ArrayList<>(enemies);
            tmp.removeIf(f -> f.getScore() < me.getScore() * 0.75 || f.getScore() > me.getScore() * 1.75);
            if (tmp.isEmpty()) {
                enemies.removeIf(f -> !f.isInSpyRange(me));
            } else {
                enemies = tmp;
            }

        }

        List<Map.Entry<DBNation, Double>> noData = new ArrayList<>();
        List<Map.Entry<DBNation, Double>> outDated = new ArrayList<>();

        for (DBNation enemy : enemies) {
            Map.Entry<Double, Boolean> opValue = enemy.getIntelOpValue();
            if (opValue != null) {
                List<Map.Entry<DBNation, Double>> list = opValue.getValue() ? outDated : noData;
                list.add(new AbstractMap.SimpleEntry<>(enemy, opValue.getKey()));
            }
        }

        Collections.sort(noData, (o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));
        Collections.sort(outDated, (o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));
        noData.addAll(outDated);
        for (Map.Entry<DBNation, Double> entry : noData) {
            DBNation nation = entry.getKey();
            alreadySpied.put(nation.getNation_id(), System.currentTimeMillis());

            String title = "Gather Intelligence for: " + me.getNation();
            String response = nation.toEmbedString(false);
            response += "\n1 spy on extremely covert: ";
            response += "\n*Please post the result of your spy report here*";
            response += "\nMore info: https://docs.google.com/document/d/1gEeSOjjSDNBpKhrU9dhO_DN-YM3nYcklYzSYzSqq8k0";
            channel.create().embed(title, response).send();
            return null;
        }
        return "No results found";
    }

    @Command(desc = "Cancel your unblockade request")
    @RolePermission(Roles.MEMBER)
    public String cancelUnblockadeRequest(@Me DBNation me, @Me GuildDB db, @Me User author) {
        Map.Entry<Long, String> existing = me.getUnblockadeRequest();
        me.deleteMeta(NationMeta.UNBLOCKADE_REASON);
        if (existing == null) return "No unblockade request founds";

        TextChannel unblockadeChannel = db.getOrNull(GuildDB.Key.UNBLOCKADE_REQUESTS);
        if (unblockadeChannel != null) {
            StringBuilder response = new StringBuilder();

            response.append("**ALLY **");
            response.append(author.getAsMention());
            response.append("<" + me.getNationUrl() + "> Cancelled the unblockade request: `" + existing.getValue() + "`");
            RateLimitUtil.queue(unblockadeChannel.sendMessage(response.toString()));
        }

        return "Cancelled unblockade request";
    }

    @Command(desc = "Request your blockade be broken within a specific timeframe\n" +
            "e.g. `{prefix}UnblockadeMe 4day \"i am low on warchest\"`")
    @RolePermission(Roles.MEMBER)
    public String unblockadeMe(@Me GuildDB db, @Me DBNation me, @Me User author, @Timediff long diff, @TextArea String note, @Switch("f") boolean force) throws IOException {
        if (diff > TimeUnit.DAYS.toMillis(5)) {
            return "You cannot make a request longer than 5 days. (Make a new request later to extend your current one)";
        }
        if (note.length() > 256) return "Note is too long. Max 256 characters";
        if (note.indexOf('\n') != -1) return "Note must be a single line";
        long timestamp = System.currentTimeMillis() + diff;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeLong(timestamp);
        dos.writeBytes(note);
        me.setMeta(NationMeta.UNBLOCKADE_REASON, out.toByteArray());

//        TODO info about baiting beige and things you can do under a blockade

        if (me.getOff() < 5 && !force && me.getAircraftPct() < 0.3) {
            return "You do not have 5 offensive wars. If you have already lost military, it can be advantageous to give yourself beige time instead of someone wasting resources trying to break a blockade.\n" +
                    "Please consider declaring more wars on potential enemies, or raids on various alliances.\n" +
                    "Ask milcom for advice on targets if you need\n\n" +
                    Messages.BLOCKADE_HELP +
                    "\nAdd `-f` to ignore this check";
        }

        TextChannel unblockadeChannel = db.getOrNull(GuildDB.Key.UNBLOCKADE_REQUESTS);
        if (unblockadeChannel != null) {
            StringBuilder response = new StringBuilder();

            response.append("**ALLY **");
            response.append(author.getAsMention());

            response.append("<" + me.getNationUrl() + ">");
            response.append(" | " + me.getAllianceName() + " | Time: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff));
            response.append("\nnote: `").append(note).append("`");
            response.append("\n```")
                    .append(String.format("%5s", (int) me.getScore())).append(" ns").append(" | ")
                    .append(String.format("%2s", me.getCities())).append(" \uD83C\uDFD9").append(" | ")
                    .append(String.format("%6s", me.getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                    .append(String.format("%5s", me.getTanks())).append(" \u2699").append(" | ")
                    .append(String.format("%5s", me.getAircraft())).append(" \u2708").append(" | ")
                    .append(String.format("%4s", me.getShips())).append(" \u26F5").append(" | ")
                    .append(String.format("%1s", me.getOff())).append(" \uD83D\uDDE1").append(" | ")
                    .append(String.format("%1s", me.getDef())).append(" \uD83D\uDEE1").append("``` ");
            response.append("------\n");

            Set<Integer> enemies = me.getBlockadedBy();
            for (Integer id : enemies) {
                DBNation enemy = DBNation.byId(id);
                int maxShips = 0;
                for (DBWar war : enemy.getActiveWars()) {
                    DBNation other = war.getNation(!war.isAttacker(enemy));
                    if (other == null) continue;
                    maxShips = Math.max(other.getShips(), maxShips);
                }

                if (enemy != null) {
                    response.append("**Enemy**: <" + enemy.getDeclareUrl() + "> | <" + enemy.getAllianceUrl() + "> " + MathMan.format(enemy.getShipPct() * 100) + "% ships");

                    response.append("\n```")
                            .append(String.format("%5s", (int) enemy.getScore())).append(" ns").append(" | ")
                            .append(String.format("%2s", enemy.getCities())).append(" \uD83C\uDFD9").append(" | ")
                            .append(String.format("%6s", enemy.getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                            .append(String.format("%5s", enemy.getTanks())).append(" \u2699").append(" | ")
                            .append(String.format("%5s", enemy.getAircraft())).append(" \u2708").append(" | ")
                            .append(String.format("%4s", enemy.getShips())).append(" \u26F5").append(" | ")
                            .append(String.format("%1s", enemy.getOff())).append(" \uD83D\uDDE1").append(" | ")
                            .append(String.format("%1s", enemy.getDef())).append(" \uD83D\uDEE1").append("``` ");

                    double otherOdds = PnwUtil.getOdds(maxShips, enemy.getShips(), 3);

                    if (otherOdds > 0.15) {
                        response.append(" - Another attacker has " + MathMan.format(otherOdds * 100) + "% to break blockade\n");
                    }

                    Set<Integer> blockading = enemy.getBlockading();
                    blockading.remove(me.getNation_id());
                    blockading.removeIf(f -> {
                        DBNation nation = DBNation.byId(f);
                        return (nation == null || nation.getActive_m() > 2880);
                    });

                    if (blockading.size() > 0) {
                        response.append(" - enemy also blockading: " + StringMan.getString(blockading) + "\n");
                    }
                }
            }
            Role milcom = Roles.MILCOM.toRole(db);
            if (milcom != null) response.append(milcom.getAsMention());

            RateLimitUtil.queue(unblockadeChannel.sendMessage(response.toString()));

        }


        return "Added blockade request. See also " + CM.war.blockade.cancelRequest.cmd.toSlashMention() + "\n> " + Messages.BLOCKADE_HELP;
    }

    @Command(desc = "Find blockade targets")
    @RolePermission(Roles.MEMBER)
    public String unblockade(@Me DBNation me, @Me GuildDB db, @Me Guild guild, @Me User user, @Me IMessageIO channel,
                             Set<DBNation> allies,
                             @Default("*") Set<DBNation> targets,
                             @Switch("s") Integer myShips,
                             @Switch("r") @Default("5") Integer numResults) throws IOException {
        allies.removeIf(f -> f.getActive_m() > 1440 || f.getVm_turns() > 0 || f.getPosition() <= 1);
        allies.removeIf(f -> !f.isBlockaded());

        if(myShips== null) myShips = me.getShips();

        double min = me.getScore() * 0.75;
        double max = me.getScore() * 1.75;

        Map<DBNation, Map<DBNation, Boolean>> alliesBlockadedBy = new HashMap<>();
        for (DBNation ally : allies) {
            for (Integer nationId : ally.getBlockadedBy()) {
                DBNation blockader = DBNation.byId(nationId);
                if (blockader == null) continue;
                alliesBlockadedBy.computeIfAbsent(ally, f -> new HashMap<>()).put(blockader, false);

                if (blockader.getScore() < min || blockader.getScore() > max) continue;
                if (blockader.getVm_turns() > 0 || blockader.isBeige() || blockader.getDef() >= 3) continue;
                if (!targets.contains(blockader)) continue;

                alliesBlockadedBy.computeIfAbsent(ally, f -> new HashMap<>()).put(blockader, true);
            }
        }

        // prioritize
        // Allies that want to be unblockaded
        // Nations you can break the blockade of
        // enemies you wont lose against

        Map<DBNation, Double> weighting = new HashMap<>();
        Map<DBNation, String> requested = new HashMap<>();
        for (Map.Entry<DBNation, Map<DBNation, Boolean>> entry : alliesBlockadedBy.entrySet()) {

            DBNation ally = entry.getKey();

            Map.Entry<Long, String> request = ally.getUnblockadeRequest();

            double[] escrowed = null;
            GuildDB otherDb = Locutus.imp().getGuildDBByAA(ally.getAlliance_id());
            if (otherDb != null) {
                escrowed = otherDb.getEscrowed(ally);
            }

            double value;
            if (request != null && escrowed != null) {
                value = 16;
            } else if (escrowed != null) {
                value = 15;
                request = new AbstractMap.SimpleEntry<>(0L, "Funds wanted: " + PnwUtil.resourcesToString(escrowed));
            } else if (request != null) {
                value = 10;
            } else {
                value = 5;
            }

            if (request != null) {
                requested.put(ally, request.getValue());
            }

            double relativeAir = 0;
            double relativeGround = 0;

            int canUnblockade = 0;
            double unblockadChance = 0;
            Set<Map.Entry<DBNation, Boolean>> enemyEntries = entry.getValue().entrySet();
            for (Map.Entry<DBNation, Boolean> entry2 : enemyEntries) {
                DBNation enemy = entry2.getKey();
                if (entry2.getValue()) {
                    double odds = PnwUtil.getOdds(myShips, enemy.getShips(), 3);
                    if (odds >= 0.125) {
                        canUnblockade++;
                        unblockadChance += odds;

                        relativeAir += (1d + enemy.getAircraft()) / (me.getAircraft() + 1d);
                        relativeGround += (enemy.getGroundStrength(true, me.getAircraft() > enemy.getAircraft() * 1.3) / me.getGroundStrength(true, me.getAircraft() * 1.3 < enemy.getAircraft()));
                    }

                }
            }
            if (canUnblockade == 0) continue;

            unblockadChance /= enemyEntries.size();

            value *= unblockadChance;

            if (canUnblockade > 1) {
                relativeAir /= (0.9 * canUnblockade);
                relativeGround /= (0.9 * canUnblockade);
            }

            relativeAir = Math.max(Math.min(2, relativeAir), 0.5);
            relativeGround = Math.max(Math.min(2, relativeGround), 0.5);

            value /= ((relativeAir + relativeGround) / 2d);

            weighting.put(ally, value);
        }

        if (weighting.isEmpty()) return "No results found. Try adding `-s 1234` to specify a number of ships";

        List<DBNation> sorted = new ArrayList<>(weighting.keySet());

        Collections.sort(sorted, (o1, o2) -> Double.compare(weighting.get(o2), weighting.get(o1)));

        StringBuilder response = new StringBuilder();

        for (int i = 0; i < Math.min(sorted.size(), numResults); i++) {
            DBNation ally = sorted.get(i);

            response.append("**ALLY **");
            User allyUser = ally.getUser();
            if (allyUser != null) {
                response.append(ally.getUserDiscriminator() + " | ");
                OnlineStatus status = OnlineStatus.OFFLINE;
                if (ally.getActive_m() < 15) {
                    status = OnlineStatus.ONLINE;
                } else {
                    Member member = allyUser.getMutualGuilds().get(0).getMember(allyUser);
                    if (member != null) {
                        status = member.getOnlineStatus();
                    }
                }
                if (status != OnlineStatus.OFFLINE) {
                    response.append(status + " ");
                }
            }
            response.append("<" + ally.getNationUrl() + ">");
            response.append(" | " + ally.getAllianceName());
            response.append("\n```")
            .append(String.format("%5s", (int) ally.getScore())).append(" ns").append(" | ")
            .append(String.format("%2s", ally.getCities())).append(" \uD83C\uDFD9").append(" | ")
            .append(String.format("%6s", ally.getSoldiers())).append(" \uD83D\uDC82").append(" | ")
            .append(String.format("%5s", ally.getTanks())).append(" \u2699").append(" | ")
            .append(String.format("%5s", ally.getAircraft())).append(" \u2708").append(" | ")
            .append(String.format("%4s", ally.getShips())).append(" \u26F5").append(" | ")
            .append(String.format("%1s", ally.getOff())).append(" \uD83D\uDDE1").append(" | ")
            .append(String.format("%1s", ally.getDef())).append(" \uD83D\uDEE1").append("``` ");
            String request = requested.get(ally);
            if (request != null) {
                response.append(" - Requested Blockade Broken: `" + request + "`\n");
            }
            response.append("------\n");

            Map<DBNation, Boolean> enemies = alliesBlockadedBy.get(ally);
            Set<Integer> outOfRange = new HashSet<>();
            for (Map.Entry<DBNation, Boolean> entry : enemies.entrySet()) {
                DBNation enemy = entry.getKey();
                int maxShips = 0;
                for (DBWar war : enemy.getActiveWars()) {
                    DBNation other = war.getNation(!war.isAttacker(enemy));
                    if (other == null) continue;
                    maxShips = Math.max(other.getShips(), maxShips);
                }

                if (entry.getValue()) {
                    response.append("**Enemy**: <" + enemy.getDeclareUrl() + "> | <" + enemy.getAllianceUrl() + ">");

                    response.append("\n```")
                    .append(String.format("%5s", (int) enemy.getScore())).append(" ns").append(" | ")
                    .append(String.format("%2s", enemy.getCities())).append(" \uD83C\uDFD9").append(" | ")
                    .append(String.format("%6s", enemy.getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                    .append(String.format("%5s", enemy.getTanks())).append(" \u2699").append(" | ")
                    .append(String.format("%5s", enemy.getAircraft())).append(" \u2708").append(" | ")
                    .append(String.format("%4s", enemy.getShips())).append(" \u26F5").append(" | ")
                    .append(String.format("%1s", enemy.getOff())).append(" \uD83D\uDDE1").append(" | ")
                    .append(String.format("%1s", enemy.getDef())).append(" \uD83D\uDEE1").append("``` ");

                    double otherOdds = PnwUtil.getOdds(maxShips, enemy.getShips(), 3);
                    double myOdds = PnwUtil.getOdds(myShips, enemy.getShips(), 3);

                    if (otherOdds > 0.15) {
                        response.append(" - Another attacker has " + MathMan.format(otherOdds * 100) + "% to break blockade\n");
                    }
                    response.append(" - You have " + MathMan.format(myOdds * 100) + "% to break blockade\n");

                    Set<Integer> blockading = enemy.getBlockading();
                    blockading.remove(ally.getNation_id());
                    blockading.removeIf(f -> {
                        DBNation nation = DBNation.byId(f);
                        return (nation == null || nation.getActive_m() > 2880);
                    });

                    if (blockading.size() > 0) {
                        response.append(" - enemy also blockading: " + StringMan.getString(blockading) + "\n");
                    }
                } else {
                    outOfRange.add(enemy.getNation_id());
                }
            }
            if (!outOfRange.isEmpty()) {
                response.append(" - " + outOfRange.size() + " blockading not in range " + StringMan.getString(outOfRange) + "\n");
            }

            response.append("\n\n");
        }
        response.append("`note: 2.5x ships for guaranteed IT (rounded up). 2x for 90%. see:`" + CM.simulate.naval.cmd.toSlashMention());

        return response.toString();

    }

    @Command(desc = "Find nations who aren't protected, or are in an alliance unable to provide suitable counters")
    @RolePermission(Roles.MEMBER)
//    @CoalitionPermission(Coalition.RAIDPERMS)
    public String unprotected(@Me IMessageIO channel, @Me GuildDB db, Set<DBNation> targets, @Me DBNation me,
                              @Switch("r") @Default("10") @Range(min=1, max=25) Integer numResults,
                              @Switch("d") boolean ignoreDNR,
                              @Switch("a") boolean includeAllies,
                              @Switch("n") Set<DBNation> nationsToBlitzWith,
                              @Switch("s") @Default("1.2") Double maxRelativeTargetStrength,
                              @Switch("c") @Default("1.2") Double maxRelativeCounterStrength,
                              @Switch("f") boolean force
    ) {

        if (nationsToBlitzWith == null) nationsToBlitzWith = Collections.singleton(me);
        if (nationsToBlitzWith.stream().anyMatch(f -> f.active_m() > 7200 || f.getVm_turns() > 0) && !force) {
            return "You can't blitz with nations that are inactive or VM. Add `force: True` to bypass";
        }
        BiFunction<Double, Double, Integer> attScores = PnwUtil.getIsNationsInScoreRange(nationsToBlitzWith);

//        double minScore = me.getScore() * 0.75;
//        double maxScore = me.getScore() * 1.75;
        List<DBNation> nations = new ArrayList<>(targets);
        nations.removeIf(f -> f.getVm_turns() != 0);
        nations.removeIf(f -> f.getDef() >= 3);
        nations.removeIf(f -> attScores.apply(f.getScore() / 1.75, f.getScore() * 1.25) <= 0);
        nations.removeIf(f -> f.isBeige());

        if (!ignoreDNR) {
            Function<DBNation, Boolean> dnr = db.getCanRaid();
            nations.removeIf(f -> !dnr.apply(f));
        } else {
            channel.send("**WARNING: VIOLATING THE DO NOT RAID LIST IS PROHIBITED**");
        }

        Set<Integer> aaIds = new HashSet<>();
        for (DBNation nation : nations) {
            if (nation.getActive_m() < 10000 && nation.getPosition() >= Rank.MEMBER.id) {
                aaIds.add(nation.getAlliance_id());
            }
        }

        Map<Integer, List<DBNation>> countersByAlliance = new HashMap<>();

        int maxCounterSize = nationsToBlitzWith.size() * 3;
        for (Integer aaId : aaIds) {
            List<DBNation> canCounter = new ArrayList<>();
            DBAlliance alliance = DBAlliance.getOrCreate(aaId);
            Set<DBAlliance> alliances = new HashSet<>(Arrays.asList(alliance));
            if (includeAllies) {
                alliances.addAll(alliance.getTreatiedAllies());
            }
            System.out.println(aaId + " | allies=" + includeAllies + " | " + StringMan.getString(alliances));
            for (DBAlliance ally : alliances) {
                canCounter.addAll(ally.getNations(true, 10000, true));
            }

            canCounter.removeIf(f -> f.getVm_turns() > 0);
            canCounter.removeIf(f -> f.getCities() < 10 && f.getActive_m() > 2880);
            canCounter.removeIf(f -> f.getCities() == 10 && f.getActive_m() > 3000);
            canCounter.removeIf(f -> attScores.apply(f.getScore() * 0.75, f.getScore() * 1.75) <= 0);
            canCounter.removeIf(f -> f.getOff() >= 5);
//            canCounter.removeIf(f -> f.getAircraft() < me.getAircraft() * 0.6);
            canCounter.removeIf(f -> f.getNumWars() > 0 && f.getRelativeStrength() < 1);
            canCounter.removeIf(f -> f.getAircraftPct() < 0.5 && f.getTankPct() < 0.5);

            Collections.sort(canCounter, new Comparator<DBNation>() {
                @Override
                public int compare(DBNation o1, DBNation o2) {
                    return Double.compare(o2.getStrength(), o1.getStrength());
                }
            });
            if (canCounter.size() > maxCounterSize) canCounter = canCounter.subList(0, maxCounterSize);
            countersByAlliance.put(aaId, canCounter);
        }

        Map<DBNation, Double> strength = new HashMap<>();
        List<Map.Entry<DBNation, Double>> counterChance = new ArrayList<>();
        for (DBNation nation : nations) {
            double counterStrength = 0;
            if (nation.getAlliance_id() != 0) {
                List<DBNation> counters = countersByAlliance.get(nation.getAlliance_id());
                if (counters != null) {
                    counters = new ArrayList<>(counters);
                    counters.remove(nation);
                    int i = 0;

                    double totalStr = 0;
                    int numWars = 0;
                    for (DBNation other : counters) {
                        if (other == nation) continue;
                        if (i++ >= maxCounterSize) break;
                        numWars++;
                        totalStr += Math.pow(other.getStrength(), 3);
                    }
                    if (numWars > 0) {
                        counterStrength = Math.pow(totalStr / numWars, 1 / 3d);
                    }
                }
            }
            counterChance.add(new AbstractMap.SimpleEntry<>(nation, counterStrength));

            if (nation.getActive_m() > 2880) {
                if (nation.lostInactiveWar() || nation.getAlliance_id() == 0) {
                    strength.put(nation, 0d);
                    continue;
                }
                if (nation.getPosition() == Rank.APPLICANT.id) {
                    strength.put(nation, nation.getStrength() * Math.max(0, 0.8 - 0.1 * nation.getActive_m() / 1440d));
                    continue;
                }
            }
            if (nation.getAlliance_id() == 0) {
                strength.put(nation, nation.getStrength() * 0.8);
            }
            if (nation.getDef() > 0 && nation.getRelativeStrength(false) < 1) {
                strength.put(nation, nation.getStrength() / 2d);
                continue;
            }
            if (nation.getAircraft() == 0 && nation.getSoldiers() == 0) {
                strength.put(nation, nation.getStrength() / 2d);
                continue;
            }
            strength.put(nation, nation.getStrength());
        }

        double myStrenth = me.getStrength();
        if (maxRelativeCounterStrength != null) {
            counterChance.removeIf(f -> f.getKey().getStrength() > myStrenth * maxRelativeCounterStrength);
            counterChance.removeIf(f -> strength.getOrDefault(f.getKey(), 0d) > myStrenth * maxRelativeTargetStrength);
        }

        if (counterChance.isEmpty()) {
            return "No results found";
        }

        Map<DBNation, Double> valueWeighted = new HashMap<>();
        for (Map.Entry<DBNation, Double> entry : counterChance) {
            DBNation nation = entry.getKey();
            double counterStr = entry.getValue();
            double targetStr = strength.get(nation);

            double total = targetStr + counterStr;
            double min = Math.max(targetStr, counterStr);
            double weighted = min + (total - min) / 5d;
            valueWeighted.put(nation, weighted);
        }


        Collections.sort(counterChance, new Comparator<Map.Entry<DBNation, Double>>() {
            @Override
            public int compare(Map.Entry<DBNation, Double> o1, Map.Entry<DBNation, Double> o2) {
                return Double.compare(valueWeighted.get(o1.getKey()), valueWeighted.get(o2.getKey()));
            }
        });

        boolean whitelisted = db.isWhitelisted();
        long currentTurn = TimeUtil.getTurn();
        Map<DBNation, Integer> beigeTurns = new HashMap<>();

        StringBuilder response = new StringBuilder();
        numResults = Math.min(numResults, 25);
        for (int i = 0; i < Math.min(numResults, counterChance.size()); i++) {
            Map.Entry<DBNation, Double> entry = counterChance.get(i);
            DBNation nation = entry.getKey();
            double counterStrength = entry.getValue();

            response.append('\n')
                    .append("<" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + nation.getNation_id() + ">")
                    .append(" | " + String.format("%16s", nation.getNation()))
                    .append(" | " + String.format("%16s", nation.getAllianceName()));

            if (whitelisted) {
                double total = nation.lootTotal();
                if (total != 0) {
                    response.append(": $" + MathMan.format(total));
                }
            }

            response.append("\n```")
//                            .append(String.format("%5s", (int) nation.getScore())).append(" ns").append(" | ")
                    .append(String.format("%2s", nation.getCities())).append(" \uD83C\uDFD9").append(" | ")
//                                .append(String.format("%5s", nation.getAvg_infra())).append(" \uD83C\uDFD7").append(" | ")
                    .append(String.format("%6s", nation.getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                    .append(String.format("%5s", nation.getTanks())).append(" \u2699").append(" | ")
                    .append(String.format("%5s", nation.getAircraft())).append(" \u2708").append(" | ")
                    .append(String.format("%4s", nation.getShips())).append(" \u26F5").append(" | ")
//                            .append(String.format("%1s", nation.getOff())).append(" \uD83D\uDDE1").append(" | ")
                    .append(String.format("%1s", nation.getDef())).append(" \uD83D\uDEE1");
//                                .append(String.format("%2s", nation.getSpies())).append(" \uD83D\uDD0D");

            if (nation.isBeige()) {
                int turns = nation.getBeigeTurns();
                if (turns > 0) {
                    response.append(" | ").append("beige=" + turns);
                }
            }

            Activity activity = nation.getActivity(14 * 12);
            double loginChance = activity.loginChance((int) Math.max(1, (12 - (currentTurn % 12))), true);
            int loginPct = (int) (loginChance * 100);

            response.append(" | log=" + loginPct + "%");
            response.append(" | str=" + MathMan.format(100 * counterStrength / myStrenth) + "%");
            response.append("```");
        }
        return response.toString();
    }

    @Command(desc="Find a weaker war target that you can hit, who is in a specified alliance/coalition/none/*\n" +
            "Defualts to `enemies` coalition\n" +
            "Add `-i` to include inactives\n" +
            "Add `-a` to include applicants\n" +
            "Add `-p` to only include priority targets\n" +
            "Add `-w` to only list weak enemies\n" +
            "Add `-c` to only list enemies with less cities")
    @RolePermission(Roles.MEMBER)
    public String war(@Me User author, @Me IMessageIO channel, @Me GuildDB db, @Me DBNation me, @Default("~enemies") Set<DBNation> targets, @Default("8") int numResults,
                      @Switch("i") boolean includeInactives,
                      @Switch("a") boolean includeApplicants,
                      @Switch("p") boolean onlyPriority,
                      @Switch("w") boolean onlyWeak,
                      @Switch("c") boolean onlyLessCities,
                      @Switch("d") boolean resultsInDm,
                      @Switch("s") boolean includeStrong) throws IOException, ExecutionException, InterruptedException {
        if (resultsInDm) {
            IMessageIO parent = channel;
            channel = new DiscordChannelIO(RateLimitUtil.complete(author.openPrivateChannel()), null);
        }

        String aa = null;

        if (!includeApplicants) targets.removeIf(f -> f.getActive_m() > 1440 && f.getPosition() <= 1);
        if (!includeInactives) targets.removeIf(n -> n.getActive_m() >= 2440);
        targets.removeIf(n -> n.getVm_turns() != 0);
//                nations.removeIf(n -> n.isBeige());

        double minScore = me.getScore() * 0.75;
        double maxScore = me.getScore() * 1.75;

        List<DBNation> strong = new ArrayList<>();

        ArrayList<DBNation> targetsStorted = new ArrayList<>();
        for (DBNation nation : targets) {
            if (nation.getScore() >= maxScore || nation.getScore() <= minScore) continue;
            if (nation.getActive_m() > 2440 && !includeInactives) continue;
            if (nation.getVm_turns() != 0) continue;
            if (nation.getDef() >= 3) continue;
            if (nation.getCities() >= me.getCities() * 1.5 && !includeStrong && me.getGroundStrength(false, true) > nation.getGroundStrength(true, false) * 2) continue;
            if (nation.getCities() >= me.getCities() * 1.8 && !includeStrong && nation.getActive_m() < 2880) continue;
            targetsStorted.add(nation);
        }

        if (onlyPriority) {
            targetsStorted.removeIf(f -> f.getNumWars() == 0);
            targetsStorted.removeIf(f -> f.getRelativeStrength() <= 1);
        }

        DBNation finalMe = me;
        if (onlyWeak) {
            targetsStorted.removeIf(f -> f.getGroundStrength(true, false) > finalMe.getGroundStrength(true, false));
            targetsStorted.removeIf(f -> f.getAircraft() > finalMe.getAircraft());
        }
        if (onlyLessCities) {
            targetsStorted.removeIf(f -> f.getCities() > finalMe.getCities());
        }

        List<DBWar> wars = me.getActiveWars();
        for (DBWar war : wars) {
            targetsStorted.remove(war.getNation(true));
            targetsStorted.remove(war.getNation(false));
        }

        CompletableFuture<IMessageBuilder> msg = channel.send("Please wait... ");

        int mySoldierRebuy = me.getCities() * Buildings.BARRACKS.max() * 5 * 2;

        long currentTurn = TimeUtil.getTurn();

        List<Map.Entry<DBNation, Double>> nationNetValues = new ArrayList<>();

        for (DBNation nation : targetsStorted) {
            if (nation.isBeige()) continue;
//                        SimulatedWarNode origin = SimulatedWarNode.of(nation, me.getNation_id() + "", nation.getNation_id() + "", "raid");

            double value = BlitzGenerator.getAirStrength(nation, true);
            value *= 2 * (nation.getCities() / (double) me.getCities());
            if (nation.getOff() > 0) value /= 4;
            if (nation.getShips() > 1 && nation.getOff() > 0 && nation.isBlockader()) value /= 2;
            if (nation.getDef() <= 1) value /= (1.05 + (0.1 * nation.getDef()));
            if (nation.getActive_m() > 1440) value *= 1 + Math.sqrt(nation.getActive_m() - 1440) / 250;
            value /= (1 + nation.getOff() * 0.1);
            if (nation.getScore() > me.getScore() * 1.25) value /= 2;
            if (nation.getOff() > 0) value /= nation.getRelativeStrength();

            nationNetValues.add(new AbstractMap.SimpleEntry<>(nation, value));
        }

        Map<DBNation, Integer> beigeTurns = new HashMap<>();

        if (nationNetValues.isEmpty()) {
            for (DBNation nation : targetsStorted) {
                if (nation.isBeige()) {
                    int turns = beigeTurns.computeIfAbsent(nation, f -> f.getBeigeTurns());
                    nationNetValues.add(new AbstractMap.SimpleEntry<>(nation, (double) turns));
                }
            }
            if (nationNetValues.isEmpty()) {
                String message;
                if (onlyPriority) {
                    message = "No targets found. Try `" + Settings.commandPrefix(true) + "war`";
                } else {
                    message = "No targets found:\n" +
                            " - Add `-i` to include inactives\n" +
                            " - Add `-a` to include applicants";
                }
                channel.send(message);
                return null;
            }
        }

        nationNetValues.sort(Comparator.comparingDouble(Map.Entry::getValue));

        StringBuilder response = new StringBuilder("**Results for " + me.getNation() + "**:");

        int count = 0;

        boolean whitelisted = db.isWhitelisted();

        for (Map.Entry<DBNation, Double> nationNetValue : nationNetValues) {
            if (count++ == numResults) break;

            DBNation nation = nationNetValue.getKey();

            response.append('\n')
                    .append("<" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + nation.getNation_id() + ">")
                    .append(" | " + String.format("%16s", nation.getNation()))
                    .append(" | " + String.format("%16s", nation.getAllianceName()));

            if (whitelisted) {
                double total = nation.lootTotal();
                if (total != 0) {
                    response.append(": $" + MathMan.format(total));
                }
            }

            response.append("\n```")
//                            .append(String.format("%5s", (int) nation.getScore())).append(" ns").append(" | ")
                    .append(String.format("%2s", nation.getCities())).append(" \uD83C\uDFD9").append(" | ")
//                                .append(String.format("%5s", nation.getAvg_infra())).append(" \uD83C\uDFD7").append(" | ")
                    .append(String.format("%6s", nation.getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                    .append(String.format("%5s", nation.getTanks())).append(" \u2699").append(" | ")
                    .append(String.format("%5s", nation.getAircraft())).append(" \u2708").append(" | ")
                    .append(String.format("%4s", nation.getShips())).append(" \u26F5").append(" | ")
//                            .append(String.format("%1s", nation.getOff())).append(" \uD83D\uDDE1").append(" | ")
                    .append(String.format("%1s", nation.getDef())).append(" \uD83D\uDEE1");
//                                .append(String.format("%2s", nation.getSpies())).append(" \uD83D\uDD0D");

            if (nation.isBeige()) {
                int turns = beigeTurns.computeIfAbsent(nation, f -> f.getBeigeTurns());
                if (turns > 0) {
                    response.append(" | ").append("beige=" + turns);
                }
            }

            Activity activity = nation.getActivity(14 * 12);
            double loginChance = activity.loginChance((int) Math.max(1, (12 - (currentTurn % 12))), true);
            int loginPct = (int) (loginChance * 100);

            response.append(" | login=" + loginPct + "%");
            response.append("```");
        }

        if (count == 0) {
            return "No results. Please ping a target (advisor";
        }

        return response.toString();
    }


    @Command(desc = "Find a high infra target\n" +
            "optional alliance and sorting (default: active nations, sorted by top city infra).\n\t" +
            "To see a list of coalitions, use `{prefix}coalitions`.\n\t" +
            "Add `-a` To include applicants\n" +
            "Add `-i` to include inactives\n" +
            "Add `-w` to filter out nations with strong ground\n" +
            "Add `-s` to filter out nations with >2 ships\n" +
            "Add `-m` to sort by mean infra instead of city max\n" +
            "Add `-c` to sort by city max instead of damage estimate\n" +
            "Add `-b` to include beige targets" +
            "Add `-s 1234` to filter by war range (score)")
    @RolePermission(Roles.MEMBER)
    public String damage(@Me IMessageIO channel, @Me DBNation me, @Me User author, Set<DBNation> nations, @Switch("a") boolean includeApps,
                         @Switch("i") boolean includeInactives, @Switch("w") boolean filterWeak, @Switch("n") boolean noNavy,
                         @Switch("m") boolean targetMeanInfra, @Switch("c") boolean targetCityMax, @Switch("b") boolean includeBeige,
                         @Switch("d") boolean resultsInDm,
                         @Switch("s") Double warRange,
                         @Switch("n") Double relativeNavalStrength) {
        nations.removeIf(f -> f.getDef() >= 3);
        nations.removeIf(f -> f.getVm_turns() != 0);
        if (!includeApps) nations.removeIf(f -> f.getPosition() <= 1);
        if (!includeInactives) nations.removeIf(f -> f.getActive_m() > (f.getCities() > 11 ? 5 : 2) * 1440);
        if (noNavy) nations.removeIf(f -> f.getShips() > 2);
        DBNation finalMe = me;
        if (relativeNavalStrength != null) nations.removeIf(f -> f.getShips() > finalMe.getShips() * relativeNavalStrength);
        if (!includeBeige) nations.removeIf(f -> f.isBeige());

        if (warRange == null || warRange == 0) warRange = me.getScore();
        double minScore = warRange * 0.75;
        double maxScore = warRange * 1.75;

        nations.removeIf(f -> f.getScore() <= minScore || f.getScore() >= maxScore);

        me = DiscordUtil.getNation(author);
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention() + "";
        double str = me.getGroundStrength(false, true);
        str = Math.max(str, me.getCities() * 15000);
        if (filterWeak) {
            double finalStr = str;
            nations.removeIf(f -> f.getGroundStrength(true, false) > finalStr * 0.4);
        }

        Map<Integer, Double> maxInfraByNation = new HashMap<>();
        Map<Integer, Double> damageEstByNation = new HashMap<>();
        Map<Integer, Double> avgInfraByNation = new HashMap<>();

        Set<Integer> nationIds = nations.stream().map(f -> f.getNation_id()).collect(Collectors.toSet());
        Map<Integer, List<Double>> cityInfraByNation = new HashMap<>();

        {
            for (DBNation nation : nations) {
                Collection<JavaCity> cities = nation.getCityMap(false, false, false).values();
                List<Double> allInfra = cities.stream().map(f -> f.getInfra()).collect(Collectors.toList());
                double max = Collections.max(allInfra);
                double average = allInfra.stream().mapToDouble(f -> f).average().orElse(0);
                avgInfraByNation.put(nation.getNation_id(), average);
                maxInfraByNation.put(nation.getNation_id(), max);
                cityInfraByNation.put(nation.getNation_id(), allInfra);
            }
        }

        {
            for (Map.Entry<Integer, List<Double>> entry : cityInfraByNation.entrySet()) {
                double cost = damageEstimate(me, entry.getKey(), entry.getValue());
                if (cost <= 0) continue;
                damageEstByNation.put(entry.getKey(), cost);
            }

        }

        Map<Integer, Double> valueFunction;
        if (targetMeanInfra) valueFunction = avgInfraByNation;
        else if (targetCityMax) valueFunction = maxInfraByNation;
        else valueFunction = damageEstByNation;

        if (resultsInDm) {
            channel = new DiscordChannelIO(RateLimitUtil.complete(author.openPrivateChannel()), null);
        }

        if (valueFunction.isEmpty()) {
            return ("No results found");
        }

        List<Map.Entry<DBNation, Double>>  maxInfraSorted = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : valueFunction.entrySet()) {
            DBNation nation = DBNation.byId(entry.getKey());
            double amt = entry.getValue();
            maxInfraSorted.add(new AbstractMap.SimpleEntry<>(nation, amt));
        }
        maxInfraSorted.sort((o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));

        StringBuilder response = new StringBuilder("**Results for " + me.getNation() + ":**\n");
        for (int i = 0; i < Math.min(15, maxInfraSorted.size()); i++) {
            Map.Entry<DBNation, Double> entry = maxInfraSorted.get(i);
            DBNation nation = entry.getKey();

            double numCities = 2;
            if (nation.getGroundStrength(true, false) < me.getGroundStrength(true, false) * 0.4) {
                numCities++;
                if (nation.getAircraft() <= me.getAircraft()) numCities += 5;
            }
            if (nation.getActive_m() > 2440) numCities++;
            if (nation.getShips() <= 1 && me.getShips() > 1) numCities += 0.3;
            if (nation.getCities() <= me.getCities() * 0.5) numCities++;
            if (nation.getActive_m() > 10000) numCities++;

            List<Double> cityInfra = new ArrayList<>();

            double cost = damageEstByNation.getOrDefault(nation.getNation_id(), 0d);
            String moneyStr = "$" + MathMan.format(cost);
            response.append(moneyStr + " | " + nation.toMarkdown(true));
        }
        return response.toString();
    }

    public double damageEstimate(DBNation me, int nationId, List<Double> cityInfra) {
        DBNation nation = DBNation.byId(nationId);
        if (nation == null) return 0;


        double numCities = 0;
        if (me.hasProject(Projects.MISSILE_LAUNCH_PAD)) {
            numCities += 0.5;
            if (nation.hasProject(Projects.IRON_DOME)) numCities -= 0.25;
        }
        if (me.hasProject(Projects.NUCLEAR_RESEARCH_FACILITY)) {
            numCities += 1.5;
            if (nation.hasProject(Projects.VITAL_DEFENSE_SYSTEM)) numCities -= 0.3;
        }
        if (nation.getGroundStrength(true, false) < me.getGroundStrength(true, false) * 0.4) {
            numCities++;
            if (nation.getAircraft() <= me.getAircraft()) numCities += 5;
        }
        if (nation.getActive_m() > 2440) numCities+=0.5;
        if (nation.getActive_m() > 4880) numCities+=0.5;
        if (nation.getShips() <= 1 && me.getShips() > 1) numCities += 0.3;
        if (nation.getCities() <= me.getCities() * 0.5) numCities++;
        if (nation.getActive_m() > 10000) numCities += 10;

        if (numCities == 0) return 0;

        double cost = 0;
        Collections.sort(cityInfra);
        int i = cityInfra.size() - 1;
        while (i >= 0 && numCities > 0) {
            Double infra = cityInfra.get(i);
            if (infra <= 600) break;
            double factor = Math.min(numCities, 1);
            cost += factor * PnwUtil.calculateInfra(infra * 0.6-500, infra);

            i--;
            numCities--;
        }
        return cost;
    }

    @Command(desc = "Find a nation to do a spy op against the specified enemy\n" +
                    "Op types: (INTEL,NUKE,MISSILE,SHIPS,AIRCRAFT,TANKS,SPIES,SOLDIER) or `*` (for all op types)\n" +
                    "The alliance argument is optional\n" +
                    "Use `success>80` to specify a cutoff for spyop success")
    @RolePermission(Roles.MEMBER)
    public String Counterspy(@Me IMessageIO channel, @Me GuildDB db, @Me DBNation me, DBNation enemy, Set<SpyCount.Operation> operations, @Default Set<DBNation> counterWith, @Switch("s") @Range(min=0, max=100) int minSuccess) throws ExecutionException, InterruptedException, IOException {
        if (operations.isEmpty()) throw new IllegalArgumentException("Valid operations: " + StringMan.getString(SpyCount.Operation.values()));
        if (counterWith == null) {
            counterWith = new HashSet<>(Locutus.imp().getNationDB().getNations(Collections.singleton(db.getAlliance_id())));
        }
        counterWith.removeIf(n -> n.getSpies() == 0 || !n.isInSpyRange(enemy) || n.getActive_m() > TimeUnit.DAYS.toMinutes(2));

        List<Map.Entry<DBNation, Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>>>> netDamage = new ArrayList<>();


        channel.send("Please wait...");

        Integer enemySpies = enemy.updateSpies();
        if (enemySpies == null) {
            enemySpies = SpyCount.guessSpyCount(enemy);
        }

        SpyCount.Operation[] opTypes = operations.toArray(new SpyCount.Operation[0]);
        for (DBNation nation : counterWith) {
            Integer mySpies = nation.updateSpies();
            if (mySpies == null) {
                mySpies = SpyCount.guessSpyCount(nation);
            }

            if (enemySpies == -1) {
                return "Unknown enemy spies";
            }
            if (opTypes.length == 1 && opTypes[0] == SpyCount.Operation.SPIES && enemySpies == 0) {
                return "Enemy has no spies";
            }

            Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(mySpies, enemy, opTypes);
            if (best != null) {
                netDamage.add(new AbstractMap.SimpleEntry<>(nation, best));
            }
        }

        Collections.sort(netDamage, (o1, o2) -> Double.compare(o2.getValue().getValue().getValue(), o1.getValue().getValue().getValue()));

        if (netDamage.isEmpty()) {
            return "No nations found";
        }

        String title = "Recommended ops";
        StringBuilder body = new StringBuilder();

        int nationCount = 0;
        for (int i = 0; i < netDamage.size(); i++) {
            Map.Entry<DBNation, Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>>> entry = netDamage.get(i);

            Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> opinfo = entry.getValue();
            SpyCount.Operation op = opinfo.getKey();
            Map.Entry<Integer, Double> safetyDamage = opinfo.getValue();

            DBNation nation = entry.getKey();
            Integer safety = safetyDamage.getKey();
            Double damage = safetyDamage.getValue();

            int attacking = entry.getKey().getSpies();
            int spiesUsed = attacking;
            if (op != SpyCount.Operation.SPIES) {
                spiesUsed = SpyCount.getRecommendedSpies(attacking, enemy.getSpies(), safety, op, enemy);
            }

            double odds = SpyCount.getOdds(spiesUsed, enemySpies, safety, op, enemy);
            if (odds <= minSuccess) continue;
            if (++nationCount >= 10) break;

            double kills = SpyCount.getKills(spiesUsed, enemy, op, safety);

            String nationUrl = PnwUtil.getBBUrl(nation.getNation_id(), false);
            String allianceUrl = PnwUtil.getBBUrl(nation.getAlliance_id(), true);
            body.append(nationUrl).append(" | ")
                    .append(allianceUrl).append("\n");

            String safetyStr = safety == 3 ? "covert" : safety == 2 ? "normal" : "quick";

            body.append(op.name())
                    .append(" (" + safetyStr + ") with ")
                    .append(nation.updateSpies() + " spies (")
                    .append(MathMan.format(odds) + "% for $")
                    .append(MathMan.format(damage) + "net damage)")
                    .append("\n")
            ;
        }

        body.append("**Enemy:** ")
                .append(PnwUtil.getBBUrl(enemy.getNation_id(), false))
                .append(" | ")
                .append(PnwUtil.getBBUrl(enemy.getAlliance_id(), true))
                .append("\n**Spies: **").append(enemySpies).append("\n")
                .append(enemy.toMarkdown(true, false, true, false, false))
                .append(enemy.toMarkdown(true, false, false, true, true))
        ;

        channel.create().embed(title, body.toString()).send();
        return null;
    }

    @Command(aliases = {"spyop", "spyops"},
    desc = "Find the optimal spy ops to use:\n" +
            "Use `*` for the alliance to only include active wars against allies\n" +
            "Use `*` for op type to automatically find the best op type\n" +
            "Use `success>80` to specify a cutoff for spyop success\n\n" +
            "e.g. `{prefix}spyop enemies spies` | `{prefix}spyop enemies * -s`")
    @RolePermission(Roles.MEMBER)
    public String Spyops(@Me User author, @Me IMessageIO channel, @Me GuildDB db, @Me DBNation me, Set<DBNation> targets, Set<SpyCount.Operation> operations, @Default("40") @Range(min=0,max=100) int requiredSuccess, @Switch("d") boolean directMesssage, @Switch("k") boolean prioritizeKills) throws ExecutionException, InterruptedException, IOException {
        targets.removeIf(f -> f.getActive_m() > 2880);
        targets.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id);
        String title = "Recommended ops";
        String body = runSpyOps(me, db, targets, operations, requiredSuccess, prioritizeKills);

        if (directMesssage) {
            channel = new DiscordChannelIO(RateLimitUtil.complete(author.openPrivateChannel()), null);
        }

        IMessageBuilder msg = channel.create().embed(title, body);

        String response = ("Use `" + Settings.commandPrefix(true) + "spies <enemy>` first to ensure the results are up to date");
        msg.append(response.toString()).send();
        return null;
    }

    public String runSpyOps(DBNation me, GuildDB db, Set<DBNation> enemies, Set<SpyCount.Operation> operations, int requiredSuccess, boolean prioritizeKills) throws IOException {
        double minSuccess = requiredSuccess > 0 ? requiredSuccess : 50;

        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention() + "";
        }

        boolean findOptimal = true;

        Set<Integer> allies = new HashSet<>();
        Set<Integer> alliesCoalition = db.getCoalition("allies");
        if (alliesCoalition != null) allies.addAll(alliesCoalition);
        if (me.getAlliance_id() != 0) allies.add(me.getAlliance_id());
        String allianceId = db.getInfo(GuildDB.Key.ALLIANCE_ID);
        if (allianceId != null) allies.add(Integer.parseInt(allianceId));

        Set<Integer> myEnemies = Locutus.imp().getWarDb().getWarsByNation(me.getNation_id()).stream()
                .map(dbWar -> dbWar.attacker_id == me.getNation_id() ? dbWar.defender_id : dbWar.attacker_id)
                .collect(Collectors.toSet());

        Function<DBNation, Boolean> isInSpyRange = nation -> me.isInSpyRange(nation) || myEnemies.contains(nation.getNation_id());

        Function<Integer, Boolean> isInvolved = integer -> {
            if (integer == me.getNation_id()) return true;
            DBNation nation = Locutus.imp().getNationDB().getNation(integer);
            return nation != null && allies.contains(nation.getAlliance_id());
        };

        enemies.removeIf(nation -> {
            if (!isInSpyRange.apply(nation)) return true;
            if (nation.getVm_turns() > 0) return true;
            return false;
        });

        if (enemies.isEmpty()) {
            return "No nations found (1)";
        }

        int mySpies = SpyCount.guessSpyCount(me);
        long dcTime = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - (TimeUtil.getTurn() % 12));

        List<Map.Entry<DBNation, Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>>>> netDamage = new ArrayList<>();

        for (DBNation nation : enemies) {
            Integer spies = nation.updateSpies(false, false);
            if (spies == null) {
                continue;
            }
            if (spies == -1) {
                continue;
            }
            ArrayList<SpyCount.Operation> opTypesList = new ArrayList<>(operations);

            if (spies == 0) opTypesList.remove(SpyCount.Operation.SPIES);
            if (nation.getSoldiers() == 0) opTypesList.remove(SpyCount.Operation.SOLDIER);
            if (nation.getTanks() == 0) opTypesList.remove(SpyCount.Operation.TANKS);
            if (nation.getAircraft() == 0) opTypesList.remove(SpyCount.Operation.AIRCRAFT);
            if (nation.getShips() == 0) opTypesList.remove(SpyCount.Operation.SHIPS);

            int maxMissile = nation.hasProject(Projects.SPACE_PROGRAM) ? 2 : 1;
            if (opTypesList.contains(SpyCount.Operation.MISSILE) && nation.getMissiles() == maxMissile) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.MISSILE, dcTime);
                if (!purchases.isEmpty()) opTypesList.remove(SpyCount.Operation.MISSILE);
            }

            if (opTypesList.contains(SpyCount.Operation.NUKE) && nation.getNukes() == 1) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.NUKE, dcTime);
                if (!purchases.isEmpty()) opTypesList.remove(SpyCount.Operation.NUKE);
            }
            SpyCount.Operation[] opTypes = opTypesList.toArray(new SpyCount.Operation[0]);

            Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(!prioritizeKills, mySpies, nation, opTypes);
            if (best != null) {
                double netDamageCost = best.getValue().getValue();
                if (nation.hasProject(Projects.INTELLIGENCE_AGENCY)) {
                    netDamageCost *= 2;
                }
                if (nation.hasProject(Projects.SPY_SATELLITE)) {
                    netDamageCost *= 2;
                }
                best.getValue().setValue(netDamageCost);
                netDamage.add(new AbstractMap.SimpleEntry<>(nation, best));
            }
        }

        Collections.sort(netDamage, (o1, o2) -> Double.compare(o2.getValue().getValue().getValue(), o1.getValue().getValue().getValue()));

        if (netDamage.isEmpty()) {
            return "No nations found (2)";
        }

        StringBuilder body = new StringBuilder("Results for " + me.getNation() + ":\n");
        int nationCount = 0;

        ArrayList<Map.Entry<DBNation, Runnable>> targets = new ArrayList<>();

        for (int i = 0; i < netDamage.size(); i++) {
            Map.Entry<DBNation, Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>>> entry = netDamage.get(i);

            Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> opinfo = entry.getValue();
            SpyCount.Operation op = opinfo.getKey();
            Map.Entry<Integer, Double> safetyDamage = opinfo.getValue();

            DBNation nation = entry.getKey();
            Integer safety = safetyDamage.getKey();
            Double damage = safetyDamage.getValue();

            int spiesUsed = mySpies;
            if (op != SpyCount.Operation.SPIES) {
                Integer enemySpies = nation.updateSpies(false, false);
                spiesUsed = SpyCount.getRecommendedSpies(spiesUsed, enemySpies, safety, op, nation);
            }

            double kills = SpyCount.getKills(spiesUsed, nation, op, safety);

            Integer enemySpies = nation.getSpies();
            double odds = SpyCount.getOdds(spiesUsed, enemySpies, safety, op, nation);
            if (odds <= minSuccess) continue;

            int finalSpiesUsed = spiesUsed;
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    String nationUrl = PnwUtil.getBBUrl(nation.getNation_id(), false);
                    String allianceUrl = PnwUtil.getBBUrl(nation.getAlliance_id(), true);
                    body.append(nationUrl).append(" | ")
                            .append(allianceUrl).append("\n");

                    body.append("Op: " + op.name()).append("\n")
                            .append("Safety: " + SpyCount.Safety.byId(safety)).append("\n")
                            .append("Enemy \uD83D\uDD0E: " + nation.getSpies()).append("\n")
                            .append("Attacker \uD83D\uDD0E: " + finalSpiesUsed).append("\n")
                            .append("Dmg: $" + MathMan.format(damage)).append("\n")
                            .append("Kills: " + MathMan.format(kills)).append("\n")
                            .append("Success: " + MathMan.format(odds)).append("%\n\n")
                    ;
                }
            };
            targets.add(new AbstractMap.SimpleEntry<>(nation, task));
        }

        targets.removeIf(f -> f.getKey().isEspionageFull());

        for (int i = 0; i < Math.min(5, targets.size()); i++) {
            targets.get(i).getValue().run();
        }
        return body.toString();
    }

    @Command(desc = "Generate a list of raidable targets to gather intel on\n" +
            "`<time>` - filters out nations we have loot intel on in that period\n" +
            "`<attackers>` - The nations to assign to do the ops (i.e. your alliance link)\n" +
            "`<ignore-topX>` - filter out top X alliances (e.g. due to DNR), in addition to the set `dnr` coalition\n\n" +
            "Add `-l` to remove targets with loot history\n" +
            "Add `-d` to list targets currently on the dnr\n\n" +
            "e.g. `{prefix}IntelOpSheet 10d 'Error 404' 25`")
    @RolePermission(Roles.MILCOM)
    public String IntelOpSheet(@Me GuildDB db, @Timestamp long time, Set<DBNation> attackers, @Default() Integer dnrTopX,
                               @Switch("l") boolean ignoreWithLootHistory, @Switch("d") boolean ignoreDNR, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.SPYOP_SHEET);
        }
        int maxOps = 2;

        attackers.removeIf(f -> f.getPosition() <= 1 || f.getActive_m() > 1440 || f.getVm_turns() > 0);
        if (dnrTopX == null) dnrTopX = db.getOrNull(GuildDB.Key.DO_NOT_RAID_TOP_X);
        if (dnrTopX == null) dnrTopX = 0;

        List<DBNation> enemies = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());


        Set<Integer> allies = db.getAllies();
        if (!ignoreDNR) {
            Function<DBNation, Boolean> canRaid = db.getCanRaid(dnrTopX, true);
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

        SpySheet.generateSpySheet(sheet, targets);

        sheet.clearAll();
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }

    @Command(desc = "Convert hidude's sheet format to locutus")
    @RolePermission(Roles.MILCOM)
    public String convertHidudeSpySheet(@Me GuildDB db, @Me User author, SpreadSheet input, @Switch("s") SpreadSheet output, @Switch("a") boolean groupByAttacker, @Switch("f") boolean forceUpdate) throws GeneralSecurityException, IOException {
        Map<DBNation, List<Spyop>> spyOpsFiltered = SpyBlitzGenerator.getTargetsHidude(input, groupByAttacker, forceUpdate);

        if (output == null) {
            output = SpreadSheet.create(db, GuildDB.Key.SPYOP_SHEET);
        }

        generateSpySheet(output, spyOpsFiltered, groupByAttacker);

        output.clearAll();
        output.set(0, 0);

        return "<" + output.getURL() + "> " + author.getAsMention();
    }

    private void createOp(DBNation att, DBNation def, String type, String safety) {
        type = type.toLowerCase();
        SpyCount.Operation op;
        if (type.contains("spies")) {
            op = SpyCount.Operation.SPIES;
        } else if (type.contains("tank")) {
            op = SpyCount.Operation.TANKS;
        } else if (type.contains("nuke") || type.contains("nuclear")) {
            op = SpyCount.Operation.NUKE;
        } else if (type.contains("missile")) {
            op = SpyCount.Operation.MISSILE;
        } else if (type.contains("soldier")) {
            op = SpyCount.Operation.SOLDIER;
        } else if (type.contains("ship") || type.contains("navy")) {
            op = SpyCount.Operation.SHIPS;
        } else if (type.contains("aircraft") || type.contains("plane")) {
            op = SpyCount.Operation.AIRCRAFT;
        } else if (type.contains("intel")) {
            op = SpyCount.Operation.INTEL;
        } else {
            throw new IllegalArgumentException("Unknown op type " + type);
        }

//        spyOp = new SpyCount.SpyOp(op)
    }

    @Command(desc = "List only spy targets for your specific alliance")
    @RolePermission(Roles.MILCOM)
    public String listSpyTargets(@Me User author, @Me GuildDB db, SpreadSheet spySheet, Set<DBNation> attackers, @Default("*") Set<DBNation> defenders, @Switch("h") Integer headerRow, @Switch("s") SpreadSheet output, @Switch("a") boolean groupByAttacker) throws GeneralSecurityException, IOException {
        if (headerRow == null) headerRow = 0;
        Map<DBNation, Set<Spyop>> spyOps = SpyBlitzGenerator.getTargets(spySheet, headerRow, false);

        if (output == null) {
            output = SpreadSheet.create(db, GuildDB.Key.SPYOP_SHEET);
        }

        List<Spyop> allOps = new ArrayList<>();
        for (Map.Entry<DBNation, Set<Spyop>> entry : spyOps.entrySet()) {
            for (Spyop spyop : entry.getValue()) {
                if (attackers.contains(spyop.attacker) && defenders.contains(spyop.defender)) {
                    allOps.add(spyop);
                }
            }
        }

        Map<DBNation, List<Spyop>> spyOpsFiltered = new LinkedHashMap<>();
        for (Spyop op : allOps) {
            if (groupByAttacker) {
                spyOpsFiltered.computeIfAbsent(op.attacker, f -> new ArrayList<>()).add(op);
            } else {
                spyOpsFiltered.computeIfAbsent(op.defender, f -> new ArrayList<>()).add(op);
            }
        }

        generateSpySheet(output, spyOpsFiltered, groupByAttacker);

        output.clearAll();
        output.set(0, 0);

        return "<" + output.getURL() + "> " + author.getAsMention();


    }

    @Command(desc = "Generate a spy blitz sheet")
    @RolePermission(Roles.MILCOM)
    public String SpySheet(@Me User author, @Me GuildDB db, Set<DBNation> attackers, Set<DBNation> defenders, @Default("nuke,missile,ships,aircraft,tanks,spies") Set<SpyCount.Operation> allowedTypes,
                           @Switch("f") boolean forceUpdate,
                           @Switch("e") boolean checkEspionageSlots,
//                           @Switch("r") Integer requiredSpies,
                           @Switch("k") boolean prioritizeKills,
                           @Switch("s") SpreadSheet sheet,
                           @Switch("d") @Default("3") Integer maxDef,
                           @Switch("p") Set<DBAlliance> prioritizeAlliances) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.SPYOP_SHEET);
        }

        SpyBlitzGenerator generator = new SpyBlitzGenerator(attackers, defenders, allowedTypes, forceUpdate, maxDef, checkEspionageSlots, 0, prioritizeKills);
        if (prioritizeAlliances != null) {
            for (DBAlliance alliance : prioritizeAlliances) {
                generator.setAllianceWeighting(alliance, 1.2);
            }

        }
        Map<DBNation, List<Spyop>> targets = generator.assignTargets();

        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.SPYOP_SHEET);
        }

        generateSpySheet(sheet, targets);

        sheet.clearAll();
        sheet.set(0, 0);

        return "<" + sheet.getURL() + "> " + author.getAsMention();
    }

    private static void generateSpySheet(SpreadSheet sheet, Map<DBNation, List<Spyop>> opsAgainstNations) {
        generateSpySheet(sheet, opsAgainstNations, false);
    }

    private static void generateSpySheet(SpreadSheet sheet, Map<DBNation, List<Spyop>> opsAgainstNations, boolean groupByAttacker) {
        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "\uD83C\uDFD9", // cities
                "\uD83C\uDFD7", // avg_infra
                "score",
                "\uD83D\uDD0D",
                "\uD83D\uDC82",
                "\u2699",
                "\u2708",
                "\u26F5",
                "\uD83D\uDE80", // rocket
                "\u2622\uFE0F", // rads
                "att1",
                "att2",
                "att3"
        ));

        sheet.setHeader(header);

        boolean multipleAAs = false;
        DBNation prevAttacker = null;
        for (List<Spyop> spyOpList : opsAgainstNations.values()) {
            for (Spyop spyop : spyOpList) {
                DBNation attacker = spyop.attacker;
                if (prevAttacker != null && prevAttacker.getAlliance_id() != attacker.getAlliance_id()) {
                    multipleAAs = true;
                }
                prevAttacker = attacker;
            }
        }

        for (Map.Entry<DBNation, List<Spyop>> entry : opsAgainstNations.entrySet()) {
            DBNation nation = entry.getKey();

            ArrayList<Object> row = new ArrayList<>();
            row.add(MarkupUtil.sheetUrl(nation.getNation(), PnwUtil.getUrl(nation.getNation_id(), false)));
            row.add(MarkupUtil.sheetUrl(nation.getAllianceName(), PnwUtil.getUrl(nation.getAlliance_id(), true)));
            row.add(nation.getCities());
            row.add(nation.getAvg_infra());
            row.add(nation.getScore());
            row.add("" + nation.getSpies());

            row.add(nation.getSoldiers());
            row.add(nation.getTanks());
            row.add(nation.getAircraft());
            row.add(nation.getShips());
            row.add(nation.getMissiles());
            row.add(nation.getNukes());

            for (Spyop spyop : entry.getValue()) {
                DBNation other;
                if (!groupByAttacker) {
                    other = spyop.attacker;
                } else {
                    other = spyop.defender;
                }
                String attStr =other.getNation();
                String safety = spyop.safety == 3 ? "covert" : spyop.safety == 2 ? "normal" : "quick";
//                attStr += "|" + spyop.operation.name() + "|" + safety + "|" + spyop.spies;

                if (multipleAAs) {
                    attStr += "|" + spyop.operation.name() + "|" + safety + "|" + spyop.spies + "|" + other.getAllianceName();
                } else {
                    attStr += "|" + spyop.operation.name() + "|" + safety + "|" + spyop.spies;
                }
                attStr =  MarkupUtil.sheetUrl(attStr, PnwUtil.getUrl(other.getNation_id(), false));

                row.add(attStr);
            }

            sheet.addRow(row);
        }
    }

    @RolePermission(value = {Roles.MILCOM, Roles.INTERNAL_AFFAIRS,Roles.ECON}, any=true)
    @Command(desc = "Generate a sheet of nation activity from a nation id\n" +
            "(use normal activity sheet unless you need the activity of a deleted nation)   ")
    public String ActivitySheetFromId(@Me GuildDB db, int nationId, @Default("2w") @Timestamp long trackTime, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        DBNation nation = new DBNation();
        nation.setNation_id(nationId);
        return ActivitySheet(db, Collections.singleton(nation), trackTime, sheet);
    }

    @RolePermission(value = {Roles.MILCOM, Roles.INTERNAL_AFFAIRS,Roles.ECON}, any=true)
    @Command(desc = "Generate a sheet of nation activity\n" +
            "Days represent the % of that day a nation logs in (UTC)\n" +
            "Numbers represent the % of that turn a nation logs in")
    public String ActivitySheet(@Me GuildDB db, Set<DBNation> nations, @Default("2w") @Timestamp long trackTime, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.ACTIVITY_SHEET);
        }
        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "\uD83C\uDFD9", // cities
                "\uD83C\uDFD7", // avg_infra
                "score",
                "Mo",
                "Tu",
                "We",
                "Th",
                "Fr",
                "Sa",
                "Su"
        ));
        for (int i = 0; i < 12; i++) {
            header.add((i + 1) + "");
        }

        sheet.setHeader(header);

        for (DBNation nation : nations) {

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), PnwUtil.getUrl(nation.getNation_id(), false)));
            header.set(1, MarkupUtil.sheetUrl(nation.getAllianceName(), PnwUtil.getUrl(nation.getAlliance_id(), true)));
            header.set(2, nation.getCities());
            header.set(3, nation.getAvg_infra());
            header.set(4, nation.getScore());

            Activity activity;
            if (trackTime == 0) {
                System.out.println("Track time = 0");
                activity = nation.getActivity();
            } else {
                long diff = System.currentTimeMillis() - trackTime;
                System.out.println("Check turns " + diff + " | " + TimeUnit.MILLISECONDS.toHours(diff) / 2 + " | " + trackTime);
                activity = nation.getActivity(TimeUnit.MILLISECONDS.toHours(diff) / 2);
            }
            double[] byDay = activity.getByDay();
            double[] byDayTurn = activity.getByDayTurn();

            for (int i = 0; i < byDay.length; i++) {
                header.set(5 + i, byDay[i] * 100);
            }

            for (int i = 0; i < byDayTurn.length; i++) {
                header.set(5 + byDay.length + i, byDayTurn[i] * 100);
            }

            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }

    @RolePermission(value = {Roles.MILCOM, Roles.INTERNAL_AFFAIRS,Roles.ECON}, any=true)
    @Command(desc = "Generate a sheet of alliance/nation/city MMR\n" +
            "Add `-f` to force an update\n" +
            "Add `-c` to list it by cities")
    public String MMRSheet(@Me GuildDB db, Set<DBNation> nations, @Switch("s") SpreadSheet sheet,
                           @Switch("f") boolean forceUpdate, @Switch("c") boolean showCities) throws GeneralSecurityException, IOException {
        if (sheet == null) sheet = SpreadSheet.create(db, GuildDB.Key.MMR_SHEET);
        List<Object> header = new ArrayList<>(Arrays.asList(
                "city",
                "nation",
                "alliance",
                "\uD83C\uDFD9", // cities
                "\uD83C\uDFD7", // avg_infra
                "score",
                "\uD83D\uDDE1",
                "\uD83D\uDEE1",
                "\uD83D\uDC82",
                "\u2699",
                "\u2708",
                "\u26F5",
                "spy",
                "spy_2d",
                "spy_cap",
                "barracks",
                "factory",
                "hangar",
                "drydock",
                "$\uD83D\uDC82",
                "$\u2699",
                "$A\u2708",
                "$\u26F5"
        ));

        sheet.setHeader(header);
        nations.removeIf(n -> n.hasUnsetMil());

        Map<Integer, Set<DBNation>> byAlliance = new HashMap<>();

        for (DBNation nation : nations) {
            byAlliance.computeIfAbsent(nation.getAlliance_id(), f -> new HashSet<>()).add(nation);
        }

        Map<Integer, DBNation> averageByAA = new HashMap<>();


        Map<DBNation, List<Object>> nationRows = new HashMap<>();

        double barracksTotal = 0;
        double factoriesTotal = 0;
        double hangarsTotal = 0;
        double drydocksTotal = 0;

        double soldierBuyTotal = 0;
        double tankBuyTotal=  0;
        double airBuyTotal = 0;
        double navyBuyTotal= 0;

        Set<Integer> nationIds = nations.stream().map(f -> f.getNation_id()).collect(Collectors.toSet());
        long dayCutoff = TimeUtil.getDay() - 2;
        Map<Integer, Integer> lastSpyCounts = Locutus.imp().getNationDB().getLastSpiesByNation(nationIds, dayCutoff);

        for (Map.Entry<Integer, Set<DBNation>> entry : byAlliance.entrySet()) {
            int aaId = entry.getKey();

            Set<DBNation> aaNations = entry.getValue();
            for (DBNation nation : aaNations) {

                double barracks = 0;
                double factories = 0;
                double hangars = 0;
                double drydocks = 0;

                double soldierBuy = 0;
                double tankBuy=  0;
                double airBuy = 0;
                double navyBuy= 0;

                List<Object> row = new ArrayList<>(header);

                Map<Integer, JavaCity> cities = nation.getCityMap(forceUpdate, forceUpdate);
                int i = 0;
                for (Map.Entry<Integer, JavaCity> cityEntry : cities.entrySet()) {
                    int cityBarracks = cityEntry.getValue().get(Buildings.BARRACKS);
                    int cityFactories = cityEntry.getValue().get(Buildings.FACTORY);
                    int cityHangars = cityEntry.getValue().get(Buildings.HANGAR);
                    int cityDrydocks = cityEntry.getValue().get(Buildings.DRYDOCK);
                    barracks += cityBarracks;
                    factories += cityFactories;
                    hangars += cityHangars;
                    drydocks += cityDrydocks;
                    if (showCities) {
                        String url = MarkupUtil.sheetUrl("CITY " + (++i), PnwUtil.getCityUrl(cityEntry.getKey()));
                        setRowMMRSheet(url, row, nation, lastSpyCounts.get(nation.getNation_id()), cityBarracks, cityFactories, cityHangars, cityDrydocks, 0, 0, 0, 0);
                        sheet.addRow(row);
                    }
                }

                long turn = TimeUtil.getTurn();
                long dayStart = TimeUtil.getTimeFromTurn(turn - (turn % 12));
                soldierBuy = 100 * Locutus.imp().getNationDB().getMilitaryBuy(nation, MilitaryUnit.SOLDIER, dayStart) / (Buildings.BARRACKS.perDay() * barracks);
                tankBuy = 100 * Locutus.imp().getNationDB().getMilitaryBuy(nation, MilitaryUnit.TANK, dayStart) / (Buildings.FACTORY.perDay() * factories);
                airBuy = 100 * Locutus.imp().getNationDB().getMilitaryBuy(nation, MilitaryUnit.AIRCRAFT, dayStart) / (Buildings.HANGAR.perDay() * hangars);
                navyBuy = 100 * Locutus.imp().getNationDB().getMilitaryBuy(nation, MilitaryUnit.SHIP, dayStart) / (Buildings.DRYDOCK.perDay() * drydocks);

                if (!Double.isFinite(soldierBuy)) soldierBuy = 100;
                if (!Double.isFinite(tankBuy)) tankBuy = 100;
                if (!Double.isFinite(airBuy)) airBuy = 100;
                if (!Double.isFinite(navyBuy)) navyBuy = 100;

                barracks /= nation.getCities();
                factories /= nation.getCities();
                hangars /= nation.getCities();
                drydocks /= nation.getCities();

                barracksTotal += barracks;
                factoriesTotal += factories;
                hangarsTotal += hangars;
                drydocksTotal += drydocks;

                soldierBuyTotal += soldierBuy;
                tankBuyTotal += tankBuy;
                airBuyTotal += airBuy;
                navyBuyTotal += navyBuy;

                setRowMMRSheet("NATION", row, nation, lastSpyCounts.get(nation.getNation_id()), barracks, factories, hangars, drydocks, soldierBuy, tankBuy, airBuy, navyBuy);
                sheet.addRow(row);
            }

            barracksTotal /= aaNations.size();
            factoriesTotal /= aaNations.size();
            hangarsTotal /= aaNations.size();
            drydocksTotal /= aaNations.size();

            soldierBuyTotal /= aaNations.size();
            tankBuyTotal /= aaNations.size();
            airBuyTotal /= aaNations.size();
            navyBuyTotal /= aaNations.size();

            String name = PnwUtil.getName(aaId, true);
            DBNation total = DBNation.createFromList("", entry.getValue(), false);

            total.setNation_id(0);
            total.setAlliance_id(aaId);

            List<Object> row = new ArrayList<>(header);
            setRowMMRSheet("ALLIANCE", row, total, null, barracksTotal, factoriesTotal, hangarsTotal, drydocksTotal, soldierBuyTotal, tankBuyTotal, airBuyTotal, navyBuyTotal);
            sheet.addRow(row);
        }

        sheet.clearAll();
        sheet.set(0, 0);
        String response = "<" + sheet.getURL() + ">";
        if (!forceUpdate) response += "\nNote: Results may be outdated, add `-f` to update.";
        return response;
    }

    private void setRowMMRSheet(String name, List<Object> row, DBNation nation, Integer lastSpies, double barracks, double factories, double hangars, double drydocks, double soldierBuy, double tankBuy, double airBuy, double navyBuy) {
        row.set(0, name);
        row.set(1, MarkupUtil.sheetUrl(nation.getNation(), PnwUtil.getUrl(nation.getNation_id(), false)));
        row.set(2, MarkupUtil.sheetUrl(nation.getAllianceName(), PnwUtil.getUrl(nation.getAlliance_id(), true)));
        row.set(3, nation.getCities());
        row.set(4, nation.getAvg_infra());
        row.set(5, nation.getScore());
        row.set(6, nation.getOff());
        row.set(7, nation.getDef());

        double soldierPct = (double) nation.getSoldiers() / (Buildings.BARRACKS.max() * nation.getCities());
        double tankPct = (double) nation.getTanks() / (Buildings.FACTORY.max() * nation.getCities());
        double airPct = (double) nation.getAircraft() / (Buildings.HANGAR.max() * nation.getCities());
        double navyPct = (double) nation.getShips() / (Buildings.DRYDOCK.max() * nation.getCities());

        row.set(8, soldierPct);
        row.set(9, tankPct);
        row.set(10, airPct);
        row.set(11, navyPct);

        int spyCap = nation.getSpyCap();
        row.set(12, nation.getSpies() + "");
        row.set(13, lastSpies + "");
        row.set(14, spyCap);

        row.set(15, barracks);
        row.set(16, factories);
        row.set(17, hangars);
        row.set(18, drydocks);

        row.set(19, soldierBuy);
        row.set(20, tankBuy);
        row.set(21, airBuy);
        row.set(22, navyBuy);
    }

    @RolePermission(Roles.MILCOM)
    @Command
    public String DeserterSheet(@Me GuildDB db, Set<DBAlliance> alliances, @Timestamp long cuttOff,
                                @Default("*") Set<DBNation> filter,
                                @Switch("a") boolean ignoreInactive,
                                @Switch("v") boolean ignoreVM,
                                @Switch("n") boolean ignoreMembers) throws IOException, GeneralSecurityException {
        Set<Integer> aaIds = alliances.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());
        Map<Integer, Map.Entry<Long, Rank>> removes = new HashMap<>();
        Map<Integer, Integer> nationPreviousAA = new HashMap<>();

        for (Integer aaId : aaIds) {
            Map<Integer, Map.Entry<Long, Rank>> removesId = Locutus.imp().getNationDB().getRemovesByAlliance(aaId);
            for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removesId.entrySet()) {
                Map.Entry<Long, Rank> existing = removes.get(entry.getKey());
                if (existing != null && entry.getValue().getKey() > existing.getKey()) {
                    continue;
                }
                nationPreviousAA.put(entry.getKey(), aaId);
                removes.put(entry.getKey(), entry.getValue());
            }

            removes.putAll(removesId);
        }

        if (removes.isEmpty()) return "No history found";

        List<Map.Entry<DBNation, Map.Entry<Long, Rank>>> nations = new ArrayList<>();

        for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
            if (entry.getValue().getKey() < cuttOff) continue;

            DBNation nation = Locutus.imp().getNationDB().getNation(entry.getKey());
            if (nation != null && (filter == null || filter.contains(nation))) {
                nations.add(new AbstractMap.SimpleEntry<>(nation, entry.getValue()));
            }
        }

        if (ignoreInactive) nations.removeIf(n -> n.getKey().getActive_m() > 10000);
        if (ignoreVM) nations.removeIf(n -> n.getKey().getVm_turns() != 0);
        if (ignoreMembers) nations.removeIf(n -> n.getKey().getPosition() > 1);
        if (nations.isEmpty()) return "No nations find over the specified timeframe";

        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.DESERTER_SHEET);
        List<Object> header = new ArrayList<>(Arrays.asList(
                "AA-before",
                "AA-now",
                "date-left",
                "position-left",
                "nation",
                "cities",
                "infra",
                "soldiers",
                "tanks",
                "planes",
                "ships",
                "spies",
                "score",
                "beige",
                "inactive",
                "login_chance"
        ));

        sheet.setHeader(header);

        for (Map.Entry<DBNation, Map.Entry<Long, Rank>> entry : nations) {
            DBNation defender = entry.getKey();
            Map.Entry<Long, Rank> dateRank = entry.getValue();
            Long date = dateRank.getKey();

            String dateStr = TimeUtil.YYYY_MM_DD_HH_MM_A.format(new Date(date));
            Rank rank = dateRank.getValue();

            ArrayList<Object> row = new ArrayList<>();
            Integer prevAA = nationPreviousAA.get(defender.getNation_id());
            String prevAAName = PnwUtil.getName(prevAA, true);
            row.add(MarkupUtil.sheetUrl(prevAAName, PnwUtil.getUrl(prevAA, true)));
            row.add(MarkupUtil.sheetUrl(defender.getAllianceName(), defender.getAllianceUrl()));

            row.add(dateStr);
            row.add(rank.name());

            row.add(MarkupUtil.sheetUrl(defender.getNation(), defender.getNationUrl()));

            row.add(defender.getCities());
            row.add(defender.getAvg_infra());
            row.add(defender.getSoldiers() + "");
            row.add(defender.getTanks() + "");
            row.add(defender.getAircraft() + "");
            row.add(defender.getShips() + "");
            row.add(defender.getSpies() + "");
            row.add(defender.getScore() + "");
            row.add(defender.getBeigeTurns() + "");
            row.add(TimeUtil.secToTime(TimeUnit.MINUTES, defender.getActive_m()));

            Activity activity = defender.getActivity(12 * 7 * 2);
            row.add(activity.getAverageByDay());

            sheet.addRow(row);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }

    @RolePermission(Roles.MILCOM)
    @Command(desc = "List of nations and their relative military")
    public String combatantSheet(@Me GuildDB db, Set<DBAlliance> alliances) {
        Set<Integer> alliancesIds = alliances.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());
        List<DBWar> wars = Locutus.imp().getWarDb().getActiveWars(alliancesIds, WarStatus.ACTIVE, WarStatus.DEFENDER_OFFERED_PEACE, WarStatus.ATTACKER_OFFERED_PEACE);
        wars.removeIf(w -> {
            DBNation n1 = Locutus.imp().getNationDB().getNation(w.attacker_id);
            DBNation n2 = Locutus.imp().getNationDB().getNation(w.defender_id);
            if (n1 == null || n2 == null) {
                return true;
            }
            DBNation self = alliances.contains(n1.getAlliance_id()) ? n1 : n2;
            return n1.getActive_m() > 4320 || n2.getActive_m() > 4320 || self.getPosition() <= 1;
        });

        if (wars.isEmpty()) return "No wars found";

        Map<DBWar, WarCard> warMap = new HashMap<>();

        int i = 0;
        for (DBWar war : wars) {
            WarCard card = new WarCard(war, true);
            if (!card.isActive()) continue;
            warMap.put(war, card);
        }

        try {

            Map.Entry<Map<DBNation, DBNation>, Map<DBNation, DBNation>> kdMap = simulateWarsKD(warMap.values());

            SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.ACTIVE_COMBATANT_SHEET);

            List<Object> header = new ArrayList<>(Arrays.asList(
                    "nation",
                    "alliance",
                    "cities",
                    "avg_infra",
                    "score",
                    "soldier%",
                    "tank%",
                    "air%",
                    "sea%",
                    "off",
                    "def",
                    "-ground",
                    "-air",
                    "-sea",
                    "'+ground",
                    "'+air",
                    "'+sea",
                    "net_ground",
                    "net_air",
                    "net_sea"
            ));

            sheet.setHeader(header);

            Map<DBNation, DBNation> losses = kdMap.getValue();
            Map<DBNation, DBNation> kills = kdMap.getKey();
            for (Map.Entry<DBNation, DBNation> entry : losses.entrySet()) {
                DBNation nation = entry.getKey();
                DBNation loss = entry.getValue();
                DBNation kill = kills.get(loss);

                header.set(0, MarkupUtil.sheetUrl(nation.getNation(), PnwUtil.getUrl(nation.getNation_id(), false)));
                header.set(1, MarkupUtil.sheetUrl(nation.getAllianceName(), PnwUtil.getUrl(nation.getAlliance_id(), true)));
                header.set(2, nation.getCities());
                header.set(3, nation.getAvg_infra());
                header.set(4, nation.getScore());

                double soldierMMR = (double) nation.getSoldiers() / (Buildings.BARRACKS.max() * nation.getCities());
                double tankMMR = (double) nation.getTanks() / (Buildings.FACTORY.max() * nation.getCities());
                double airMMR = (double) nation.getAircraft() / (Buildings.HANGAR.max() * nation.getCities());
                double navyMMR = (double) nation.getShips() / (Buildings.DRYDOCK.max() * nation.getCities());

                header.set(5, soldierMMR);
                header.set(6, tankMMR);
                header.set(7, airMMR);
                header.set(8, navyMMR);

                header.set(9, nation.getOff());
                header.set(10, nation.getDef());

                int groundTotal = nation.getSoldiers() + nation.getTanks() * 1000;
                {
                    int groundLoss = nation.getSoldiers() - loss.getSoldiers() + (nation.getTanks() - loss.getTanks()) * 1000;
                    double groundPct = 100 * (groundTotal == 0 ? -1 : -groundLoss / (double) groundTotal);
                    double airPct = 100 * (nation.getAircraft() == 0 ? -1 : -(nation.getAircraft() - loss.getAircraft()) / (double) nation.getAircraft());
                    double seaPct = 100 * (nation.getShips() == 0 ? -1 : -(nation.getShips() - loss.getShips()) / (double) nation.getShips());

                    header.set(11, groundPct);
                    header.set(12, airPct);
                    header.set(13, seaPct);
                    int groundKill = kill.getSoldiers() - nation.getSoldiers() + (kill.getTanks() - nation.getTanks()) * 1000;
                    double groundPctKill = 100 * (groundTotal == 0 ? 0 : groundKill / (double) groundTotal);
                    double airPctKill = 100 * (nation.getAircraft() == 0 ? 0 : (kill.getAircraft() - nation.getAircraft()) / (double) nation.getAircraft());
                    double seaPctKill = 100 * (nation.getShips() == 0 ? 0 : (kill.getShips() - nation.getShips()) / (double) nation.getShips());

                    if (groundPctKill == 0 && nation.getSoldiers() != 0) groundPctKill = 100;
                    if (airPctKill == 0 && nation.getAircraft() != 0) airPctKill = 100;
                    if (seaPctKill == 0 && nation.getShips() != 0) seaPctKill = 100;

                    header.set(14, groundPctKill);
                    header.set(15, airPctKill);
                    header.set(16, seaPctKill);

                    header.set(17, groundPctKill + groundPct);
                    header.set(18, airPctKill + airPct);
                    header.set(19, seaPctKill + seaPct);
                }

                sheet.addRow(header);
            }

            sheet.set(0, 0);

            return "<" + sheet.getURL() + ">";
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    private Map.Entry<Map<DBNation, DBNation>,Map<DBNation, DBNation>> simulateWarsKD(Collection<WarCard> warcards) {
        Map<DBNation, DBNation> losses = new HashMap<>();
        Map<DBNation, DBNation> kills = new HashMap<>();
        int i = 0;
        for (WarCard warcard : warcards) {
            DBWar war = warcard.getWar();
            DBNation n1 = Locutus.imp().getNationDB().getNation(war.attacker_id);
            DBNation n2 = Locutus.imp().getNationDB().getNation(war.defender_id);
            WarNation attacker = warcard.toWarNation(true);
            WarNation defender = warcard.toWarNation(false);

            performAttacks(losses, kills, attacker, defender, n1, n2);

            attacker = warcard.toWarNation(true);
            defender = warcard.toWarNation(false);

            performAttacks(losses, kills, defender, attacker, n2, n1);
        }
        return new AbstractMap.SimpleEntry<>(kills, losses);
    }

    private void performAttacks(Map<DBNation, DBNation> losses, Map<DBNation, DBNation> kills, WarNation attacker, WarNation defender, DBNation attackerOrigin, DBNation defenderOrigin) {
        DBNation attackerKills = kills.computeIfAbsent(attackerOrigin, f -> new DBNation(attackerOrigin));
        DBNation defenderLosses = losses.computeIfAbsent(defenderOrigin, f -> new DBNation(defenderOrigin));

        if (attacker.groundAttack(defender, attacker.getSoldiers(), attacker.getTanks(), true, true)) {
            addLosses(defenderOrigin, attackerKills, defenderLosses, defender);
        }
        if (attacker.airstrikeAir(defender, attacker.getAircraft(), true)) {
            addLosses(defenderOrigin, attackerKills, defenderLosses, defender);
        }
        if (attacker.naval(defender, attacker.getShips(), false)) {
            addLosses(defenderOrigin, attackerKills, defenderLosses, defender);
        }
    }

    private void addLosses(DBNation defenderOrigin,  DBNation attackerKills, DBNation defenderLosses, WarNation defender) {
        int soldierLosses = defenderOrigin.getSoldiers() - defender.getSoldiers();
        int tankLosses = defenderOrigin.getTanks() - defender.getTanks();
        int aircraftLosses = defenderOrigin.getAircraft() - defender.getAircraft();
        int shipLosses = defenderOrigin.getShips() - defender.getShips();

        defenderLosses.setSoldiers(Math.max(0, defenderLosses.getSoldiers() + soldierLosses * -1));
        defenderLosses.setTanks(Math.max(0, defenderLosses.getTanks() + tankLosses * -1));
        defenderLosses.setAircraft(Math.max(0, defenderLosses.getAircraft() + aircraftLosses * -1));
        defenderLosses.setShips(Math.max(0, defenderLosses.getShips() + shipLosses * -1));

        attackerKills.setSoldiers(Math.max(0, attackerKills.getSoldiers() + soldierLosses));
        attackerKills.setTanks(Math.max(0, attackerKills.getTanks() + tankLosses));
        attackerKills.setAircraft(Math.max(0, attackerKills.getAircraft() + aircraftLosses));
        attackerKills.setShips(Math.max(0, attackerKills.getShips() + shipLosses));
    }

    @RolePermission(Roles.MILCOM)
    @Command(desc = "Run checks on a spy blitz sheet.\nChecks that all nations are in range of their spy blitz targets and that they have no more than the provided number of offensive operations.\n" +
            "Add `true` for the day-change argument to double the offensive op limit")
    public String validateSpyBlitzSheet(@Me GuildDB db, SpreadSheet sheet, @Default("false") boolean dayChange, @Default("*") Set<DBNation> filter) {
        StringBuilder response = new StringBuilder();

        Function<DBNation, Integer> maxWarsFunc = new Function<DBNation, Integer>() {
            @Override
            public Integer apply(DBNation nation) {
                int offSlots = 1;
                if (nation.hasProject(Projects.INTELLIGENCE_AGENCY)) offSlots++;
                if (dayChange) offSlots *= 2;
                return offSlots;
            }
        };

        Function<DBNation, Boolean> isValidTarget = n -> filter.contains(n);

        BlitzGenerator.getTargets(sheet, 0, maxWarsFunc, 0.4, 1.5, false, isValidTarget, new BiConsumer<Map.Entry<DBNation, DBNation>, String>() {
            @Override
            public void accept(Map.Entry<DBNation, DBNation> dbNationDBNationEntry, String msg) {
                response.append(msg + "\n");
            }
        });

        if (response.length() <= 1) return "All checks passed";

        return response.toString();
    }

    @RolePermission(Roles.MILCOM)
    @Command
    public String mailTargets(@Me GuildDB db, @Me Guild guild, @Me JSONObject command, @Me User author, @Me IMessageIO channel,
                              String warsheet, String spysheet,
                              @Default("*") Set<DBNation> allowedNations, @Default("") String header,
                              @Switch("l") boolean sendFromLocalAccount,
                              @Switch("f") boolean force,
                              @Switch("d") boolean dm) throws IOException, GeneralSecurityException {
        if (!Roles.MAIL.has(author, guild)) {
            return "You need the MAIL role on discord (see " + CM.role.setAlias.cmd.toSlashMention() + ") to add the custom message: `" + header + "`";
        }
        Map<DBNation, Set<DBNation>> warDefAttMap = new HashMap<>();
        Map<DBNation, Set<DBNation>> spyDefAttMap = new HashMap<>();
        Map<DBNation, Set<Spyop>> spyOps = new HashMap<>();

        if (dm && !Roles.ADMIN.hasOnRoot(author)) return "You do not have permission to dm users";

        if (warsheet != null) {
            SpreadSheet blitzSheet = SpreadSheet.create(warsheet);
            warDefAttMap = BlitzGenerator.getTargets(blitzSheet, 0, f -> 3, 0.75, 1.75, true, f -> true, (a, b) -> {});
        }

        if (spysheet != null) {
            SpreadSheet spySheetObj = SpreadSheet.create(spysheet);
            try {
                spyDefAttMap = BlitzGenerator.getTargets(spySheetObj, 0, f -> 3, 0.4, 1.5, false, f -> true, (a, b) -> {});
                spyOps = SpyBlitzGenerator.getTargets(spySheetObj, 0);
            } catch (NullPointerException e) {
                spyDefAttMap = BlitzGenerator.getTargets(spySheetObj, 4, f -> 3, 0.4, 1.5, false, f -> true, (a, b) -> {});
                spyOps = SpyBlitzGenerator.getTargets(spySheetObj, 4);
            }
        }

        ApiKeyPool keys = db.getMailKey();
        if (keys == null) throw new IllegalArgumentException("No API_KEY set, please use " + CM.credentials.addApiKey.cmd.toSlashMention() + "");

        Map<DBNation, Set<DBNation>> warAttDefMap = BlitzGenerator.reverse(warDefAttMap);
        Map<DBNation, Set<DBNation>> spyAttDefMap = BlitzGenerator.reverse(spyDefAttMap);
        Set<DBNation> allAttackers = new LinkedHashSet<>();
        allAttackers.addAll(warAttDefMap.keySet());
        allAttackers.addAll(spyAttDefMap.keySet());

        String date = TimeUtil.YYYY_MM_DD.format(ZonedDateTime.now());
        String subject = "Targets-" + date + "/" + channel.getIdLong();

        String blurb = "BE ACTIVE ON DISCORD. Your attack instructions are in your war room\n" +
                "\n" +
                "This is an alliance war, not a counter. The goal is battlefield control:\n" +
                "1. Try to declare raid wars just before day change (day change if possible)\n" +
                "2. If you have ground control, further attacks with tanks kills aircraft\n" +
                "3. If you have tanks and can get ground control, do ground attacks to kill planes\n" +
                "4. Get air control to halve enemy tank strength\n" +
                "5. You can rebuy units inbetween each attack\n" +
                "6. Do not waste attacks destroying infra or minimal units\n" +
                "7. Be efficient with your attacks and try NOT to get active enemies to 0 resistance\n" +
                "8. You can buy more ships when enemy planes are weak, to avoid naval losses\n" +
                "9. Some wars you may get beiged in, that is OKAY";

        long start = System.currentTimeMillis();

        Map<DBNation, Map.Entry<String, String>> mailTargets = new HashMap<>();
        int totalSpyTargets = 0;
        int totalWarTargets = 0;

        int sent = 0;
        for (DBNation attacker : allAttackers) {
            if (!allowedNations.contains(attacker)) continue;

            List<DBNation> myAttackOps = new ArrayList<>(warAttDefMap.getOrDefault(attacker, Collections.emptySet()));
            List<Spyop> mySpyOps = new ArrayList<>(spyOps.getOrDefault(attacker, Collections.emptySet()));
            if (myAttackOps.isEmpty() && mySpyOps.isEmpty()) continue;

            sent++;

            StringBuilder mail = new StringBuilder();
            mail.append(header).append("\n");

            if (!myAttackOps.isEmpty()) {
                mail.append(blurb + "\n");
                mail.append("\n");

                mail.append("Your nation:\n");
                mail.append(getStrengthInfo(attacker) + "\n");
                mail.append("\n");

                for (int i = 0; i < myAttackOps.size(); i++) {
                    totalWarTargets++;
                    DBNation defender = myAttackOps.get(i);
                    mail.append((i + 1) + ". War Target: " + MarkupUtil.htmlUrl(defender.getNation(), defender.getNationUrl()) + "\n");
                    mail.append(getStrengthInfo(defender) + "\n"); // todo

                    Set<DBNation> others = new LinkedHashSet<>(warDefAttMap.get(defender));
                    others.remove(attacker);
                    if (!others.isEmpty()) {
                        Set<String> allies = new LinkedHashSet<>();
                        for (DBNation other : others) {
                            allies.add(other.getNation());
                        }
                        mail.append("Joining you: " + StringMan.join(allies, ",") + "\n");
                    }
                    mail.append("\n");
                }
            }

            if (!mySpyOps.isEmpty()) {
                int intelOps = 0;
                int killSpies = 0;
//                int missileNuke = 0;
                double cost = 0;
                for (Spyop op : mySpyOps) {
                    if (op.operation == SpyCount.Operation.INTEL) intelOps++;
                    if (op.operation == SpyCount.Operation.SPIES) killSpies++;
//                    if (op.operation == SpyCount.Operation.MISSILE && op.defender.getMissiles() <= 4) missileNuke++;
//                    if (op.operation == SpyCount.Operation.NUKE && op.defender.getNukes() <= 4) missileNuke++;
                    else
                        cost += SpyCount.opCost(op.spies, op.safety);
                }

                mail.append("\n");
                mail.append("Espionage targets: (costs >$" + MathMan.format(cost) + ")\n");

                if (intelOps == 0) {
                    mail.append(" - These are NOT gather intelligence ops. XD\n");
                    mail.append(" - If these targets don't work, reply with the word `more` and i'll send you some more targets\n");
                }
                if (killSpies != 0) {
                    mail.append(" - If selecting (but not executing) 1 spy on quick (gather intel) yields >50% odds, it means the enemy has no spies left.\n");
                    mail.append(" - If an enemy has 0 spies, you can use 5|spies|quick (99%) for killing units.\n");
                }

                if (intelOps != myAttackOps.size()) {
                    mail.append(" - Results may be outdated when you read it, so check they still have units to spy!\n");
                }

                mail.append(
                        " - If the op doesn't require it (and it says >50%), you don't have to use more spies or covert\n" +
                                " - Reply to this message with any spy reports you do against enemies (even if not these targets)\n" +
                                " - Remember to buy spies every day :D\n\n");

                String baseUrl = "https://politicsandwar.com/nation/espionage/eid=";
                for (int i = 0; i < mySpyOps.size(); i++) {
                    totalSpyTargets++;
                    Spyop spyop = mySpyOps.get(i);
                    String safety = spyop.safety == 3 ? "covert" : spyop.safety == 2 ? "normal" : "quick";

                    String name = spyop.defender.getNation() + " | " + spyop.defender.getAllianceName();
                    String nationUrl = MarkupUtil.htmlUrl(name, "https://tinyurl.com/y26weu7d/id=" + spyop.defender.getNation_id());

                    String spyUrl = baseUrl + spyop.defender.getNation_id();
                    String attStr = spyop.operation.name() + "|" + safety + "|" + spyop.spies + "\"";
                    mail.append((i + 1) + ". " + nationUrl + " | ");
                    if (spyop.operation != SpyCount.Operation.INTEL) mail.append("kill ");
                    else mail.append("gather ");
                    mail.append(spyop.operation.name().toLowerCase() + " using " + spyop.spies + " spies on " + safety);

                    mail.append("\n");
                }
            }

            String body = mail.toString().replace("\n","<br>");

            mailTargets.put(attacker, new AbstractMap.SimpleEntry<>(subject, body));
        }

        if (!force) {
            String title = totalWarTargets + " wars & " + totalSpyTargets + " spyops";

            Set<Integer> alliances = new LinkedHashSet<>();
            for (DBNation nation : mailTargets.keySet()) alliances.add(nation.getAlliance_id());
            String embedTitle = title + " to " + mailTargets.size() + " nations";
            if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances";

            StringBuilder body = new StringBuilder();
            body.append("subject: " + subject + "\n");

            String confirmCommand = command.put("force", "true").toString();

            channel.create().confirmation(embedTitle, body.toString(), command)
                            .append(author.getAsMention())
                                    .send();
            return null;
        }

        CompletableFuture<IMessageBuilder> msgFuture = channel.send("Sending messages...");
        for (Map.Entry<DBNation, Map.Entry<String, String>> entry : mailTargets.entrySet()) {
            DBNation attacker = entry.getKey();
            subject = entry.getValue().getKey();
            String body = entry.getValue().getValue();

            attacker.sendMail(keys, subject, body);

            if (dm) {
                String markup = MarkupUtil.htmlToMarkdown(body);
                try {
                    attacker.sendDM("**" + subject + "**:\n" + markup);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }

            if (System.currentTimeMillis() - start > 10000) {
                start = System.currentTimeMillis();
                IMessageBuilder tmp = msgFuture.getNow(null);
                if (tmp != null) msgFuture = tmp.clear().append("Sending to " + attacker.getNation()).send();
            }
        }

        return "Done, sent " + sent + " messages";
    }

    @RolePermission(Roles.MILCOM)
    @Command(desc="Run checks on a blitz sheet\n" +
            "Check that all nations are in range of their blitz targets, are still in the alliance and have no more than the provided number of offensive wars")
    public String ValidateBlitzSheet(SpreadSheet sheet, @Default("3") int maxWars, @Default("*") Set<DBNation> nationsFilter, @Switch("h") Integer headerRow) {
        Function<DBNation, Boolean> isValidTarget = f -> nationsFilter.contains(f);

        StringBuilder response = new StringBuilder();
        Integer finalMaxWars = maxWars;
        if (headerRow == null) headerRow = 0;
        BlitzGenerator.getTargets(sheet, headerRow, f -> finalMaxWars, 0.75, 1.75, true, isValidTarget, new BiConsumer<Map.Entry<DBNation, DBNation>, String>() {
            @Override
            public void accept(Map.Entry<DBNation, DBNation> dbNationDBNationEntry, String msg) {
                response.append(msg + "\n");
            }
        });

        if (response.length() <= 1) return "All checks passed";

        return response.toString();
    }

    private String getStrengthInfo(DBNation nation) {
        String msg = "Ground:" + (int) nation.getGroundStrength(true, false) + ", Air: " + nation.getAircraft() + ", cities:" + nation.getCities();

        if (nation.getActive_m() > 10000) msg += " (inactive)";
        else {
            msg += " (" + ((int) (nation.avg_daily_login() * 100)) + "% active)";
        }

        return msg;
    }


    @Command(desc = "Generates a Blitz sheet.\n" +
            "`attackers`: are the nations that should be used for the attackers (can be a google sheet)\n" +
            "`defenders`: are the nations that should be used for the defenders\n" +
            "`max-off`: How many offensive slots a nation can have (defaults to 3)\n" +
            "`same-aa-priority`: Value between 0 and 1 to prioritize assigning a target to nations in the same AA\n" +
            "`same-activity-priority`: Value between 0 and 1 to prioritize assigning targets to nations with similar activity patterns\n" +
            "`turn`: The turn in the day (between 0 and 11) when you expect the blitz to happen\n" +
            "`att-activity-threshold`: A value between 0 and 1 to filter out attackers below this level of daily activity (default: 0.5, which is 50%)\n" +
            "`def-activity-threshold`: A value between 0 and 1 to filter out defenders below this level of activity (default: 0.1)\n" +
            "`guilds`: A comma separated list of discord guilds (their id), to use to check nation activity/roles (nations must be registered)\n\n" +
            "Add `-w` to process existing wars\n" +
            "Add `-e` to only assign down declares")
    @RolePermission(Roles.MILCOM)
    public String blitzSheet(@Me User author, @Me GuildDB db, Set<DBNation> attNations, Set<DBNation> defNations, @Default("3") @Range(min=1,max=5) int maxOff,
                             @Default("0") double sameAAPriority, @Default("0") double sameActivityPriority, @Default("-1") @Range(min=-1,max=11) int turn,
                             @Default("0.5") double attActivity, @Default("0.5") double defActivity,
                             @Switch("w") boolean processActiveWars,
                             @Switch("e") boolean onlyEasyTargets,
                             @Switch("c") Double maxCityRatio,
                             @Switch("g") Double maxGroundRatio,
                             @Switch("a") Double maxAirRatio,
                             @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        Set<Long> guilds = new HashSet<>();

        BlitzGenerator blitz = new BlitzGenerator(turn, maxOff, sameAAPriority, sameActivityPriority, attActivity, defActivity, guilds, processActiveWars);
        blitz.addNations(attNations, true);
        blitz.addNations(defNations, false);
        if (processActiveWars) blitz.removeSlotted();

        Map<DBNation, List<DBNation>> targets;
        if (maxCityRatio != null || maxGroundRatio != null || maxAirRatio != null) {
            onlyEasyTargets = true;
        }
        if (onlyEasyTargets) {
            if (maxCityRatio == null) maxCityRatio = 1.8;
            if (maxGroundRatio ==  null) maxGroundRatio = 1d;
            if (maxAirRatio == null) maxAirRatio = 1.22;
            targets = blitz.assignEasyTargets(maxCityRatio, maxGroundRatio, maxAirRatio);
        } else {
            targets = blitz.assignTargets();
        }

        if (sheet == null) sheet = SpreadSheet.create(db, GuildDB.Key.ACTIVITY_SHEET);

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

        for (Map.Entry<DBNation, List<DBNation>> entry : targets.entrySet()) {
            DBNation defender = entry.getKey();
            List<DBNation> attackers = entry.getValue();
            ArrayList<Object> row = new ArrayList<>();
            row.add(MarkupUtil.sheetUrl(defender.getAllianceName(), defender.getAllianceUrl()));
            row.add(MarkupUtil.sheetUrl(defender.getNation(), defender.getNationUrl()));

            row.add(defender.getCities());
            row.add(defender.getAvg_infra());
            row.add(defender.getSoldiers() + "");
            row.add(defender.getTanks() + "");
            row.add(defender.getAircraft() + "");
            row.add(defender.getShips() + "");
            row.add(defender.getSpies() + "");

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
    }

    private String getAttackerNote(DBNation nation) {
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

    @Command(desc = "List active wars\n" +
            "Add `-i` to list concluded wars")
    @RolePermission(Roles.MILCOM)
    public String warSheet(@Me GuildDB db, Set<DBNation> allies, Set<DBNation> enemies, @Default("5d") @Timestamp long cutoff, @Switch("i") boolean includeConcludedWars, @Switch("s") String sheetId) throws GeneralSecurityException, IOException {
        long now = System.currentTimeMillis();

        WarParser parser1 = WarParser.ofAANatobj(null, allies, null, enemies, cutoff, now);

        Set<DBWar> allWars = new HashSet<>();
        allWars.addAll(parser1.getWars().values());

        if (!includeConcludedWars) allWars.removeIf(f -> !f.isActive());
        allWars.removeIf(f -> {
            DBNation att = f.getNation(true);
            DBNation def = f.getNation(false);
            return (!allies.contains(att) && !enemies.contains(att)) || (!allies.contains(def) && !enemies.contains(def));
        });

        SpreadSheet sheet = null;

        if (sheetId != null) {
            sheet = SpreadSheet.create(sheetId);
        }
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.WAR_SHEET);
        }

        List<Object> headers = new ArrayList<>(Arrays.asList(
                "id",
                "type",
                "counter",
                "GS",
                "AS",
                "B",
                "ships",
                "planes",
                "tanks",
                "soldiers",
                "cities",
                "MAP",
                "Resistance",
                "Attacker",
                "Att AA",
                "Turns",
                "Def AA",
                "Defender",
                "Resistance",
                "MAP",
                "Cities",
                "Soldiers",
                "Tanks",
                "Planes",
                "Ships",
                "GS",
                "AS",
                "B"
        ));

        sheet.setHeader(headers);

        for (DBWar war : allWars) {
            DBNation att = war.getNation(true);
            DBNation def = war.getNation(false);

            if (att == null || def == null) continue;

            WarType type = war.getWarType();
            WarCard card = new WarCard(war, true, false);


            headers.set(0, MarkupUtil.sheetUrl(war.warId + "", war.toUrl()));
            headers.set(1, war.getWarType().name());
            CounterStat counterStat = card.getCounterStat();
            headers.set(2, counterStat == null ? "" : counterStat.type.name());
            headers.set(3, card.groundControl == war.attacker_id ? "Y" : "N");
            headers.set(4, card.airSuperiority == war.attacker_id ? "Y" : "N");
            headers.set(5, card.blockaded == war.attacker_id ? "Y" : "N");
            headers.set(6, att.getShips());
            headers.set(7, att.getAircraft());
            headers.set(8, att.getTanks());
            headers.set(9, att.getSoldiers());
            headers.set(10, att.getCities());
            headers.set(11, card.attackerMAP);
            headers.set(12, card.attackerResistance);
            headers.set(13, MarkupUtil.sheetUrl(att.getNation(), att.getNationUrl()));
            headers.set(14, MarkupUtil.sheetUrl(att.getAllianceName(), att.getAllianceUrl()));

            long turnStart = TimeUtil.getTurn(war.date);
            long turns = 60 - (TimeUtil.getTurn() - turnStart);
            headers.set(15, turns);

            headers.set(16, MarkupUtil.sheetUrl(def.getAllianceName(), def.getAllianceUrl()));
            headers.set(17, MarkupUtil.sheetUrl(def.getNation(), def.getNationUrl()));
            headers.set(18, card.defenderResistance);
            headers.set(19, card.defenderMAP);
            headers.set(20, def.getCities());
            headers.set(21, def.getSoldiers());
            headers.set(22, def.getTanks());
            headers.set(23, def.getAircraft());
            headers.set(24, def.getShips());
            headers.set(25, card.groundControl == war.defender_id ? "Y" : "N");
            headers.set(26, card.airSuperiority == war.defender_id ? "Y" : "N");
            headers.set(27, card.blockaded == war.defender_id ? "Y" : "N");

            sheet.addRow(headers);
        }

        sheet.clear("A:Z");
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }

    @RolePermission(value = Roles.MILCOM)
    @Command(desc = "Generate a sheet with a list of nations attacking\n" +
            "(Defaults to those attacking allies)\n" +
            "Please still check the war history in case it is not valid to counter (and add a note to the note column indicating such)\n" +
            "Add `-a` to filter out applicants\n" +
            "Add `-i` to filter out inactive members\n" +
            "Add `-e` to include enemies not attacking")
    public String counterSheet(@Me GuildDB db, @Default() Set<DBNation> enemyFilter, @Default() Set<DBAlliance> allies, @Switch("a") boolean excludeApplicants, @Switch("i") boolean excludeInactives, @Switch("e") boolean includeAllEnemies, @Switch("s") String sheetUrl) throws IOException, GeneralSecurityException {
        boolean includeProtectorates = true;
        boolean includeCoalition = true;
        boolean includeMDP = true;
        boolean includeODP = true;

        Set<Integer> alliesIds = db.getAllies();
        Set<Integer> protectorates = new HashSet<>();

        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId != null) {
            protectorates = Locutus.imp().getNationDB().getTreaties(aaId, TreatyType.PROTECTORATE).keySet();
            if (includeProtectorates) {
                alliesIds.addAll(protectorates);
            }
            if (includeMDP) {
                alliesIds.addAll(Locutus.imp().getNationDB().getTreaties(aaId, TreatyType.MDP, TreatyType.MDOAP).keySet());
            }
            if (includeODP) {
                alliesIds.addAll(Locutus.imp().getNationDB().getTreaties(aaId, TreatyType.ODP, TreatyType.ODOAP).keySet());
            }
        }

        if (allies != null) {
            for (DBAlliance ally : allies) alliesIds.add(ally.getAlliance_id());
        }

        Map<DBNation, List<DBWar>> enemies = new HashMap<>();
        Set<Integer> enemyAAs = db.getCoalition("enemies");
        List<DBWar> defWars = Locutus.imp().getWarDb().getActiveWarsByAlliance(null, alliesIds);
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
            if (excludeApplicants && defender.getPosition() <= 1) continue;
            if (excludeInactives && defender.getActive_m() > 4880) continue;
            if (!alliesIds.contains(defender.getAlliance_id())) continue;

            enemies.computeIfAbsent(enemy, f -> new ArrayList<>()).add(war);
        }

        if (includeAllEnemies) {
            for (DBNation enemy : Locutus.imp().getNationDB().getNations(enemyAAs)) {
                enemies.putIfAbsent(enemy, new ArrayList<>());
            }
        }

        if (enemyFilter != null) {
            enemies.entrySet().removeIf(f -> !enemyFilter.contains(f.getKey()));
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
        List<List<Object>> rows = sheet.get("A:Z");

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
        //

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
                } else if (alliesIds.contains(war.defender_aa)) {
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

        return "<" + sheet.getURL() + ">";
    }

    @Command(desc = "Show the war card for a war by id")
    public String warcard(@Me IMessageIO channel, int warId) throws IOException {
        new WarCard(warId).embed(channel, false);
        return null;
    }

    @Command(desc="Show war info for a nation", aliases = {"wars", "warinfo"})
    public String wars(@Me IMessageIO channel, DBNation nation) {
        List<DBWar> wars = nation.getActiveWars();
        String title = wars.size() + " wars";
        String body = nation.getWarInfoEmbed();
        channel.create().embed(title, body).send();
        return null;
    }


    @RolePermission(Roles.MEMBER)
    @Command(desc = "Calculate spies for a nation.\n" +
            "Nation argument can be nation name, id, link, or discord tag\n" +
            "If `spies-used` is provided, it will cap the odds at using that number of spies\n" +
            "`safety` defaults to what has the best net. Options: quick, normal, covert")
    public String spies(@Me DBNation me, DBNation nation, @Default("60") int spiesUsed, @Default() SpyCount.Safety requiredSafety) throws IOException {
        me.setMeta(NationMeta.INTERVIEW_SPIES, (byte) 1);

        int result = nation.updateSpies(true, true);

        StringBuilder response = new StringBuilder(nation.getNation() + " has " + result + " spies.");
        response.append("\nRecommended:");

        int minSafety = requiredSafety == null ? 1 : requiredSafety.id;
        int maxSafety = requiredSafety == null ? 3 : requiredSafety.id;

        for (SpyCount.Operation op : SpyCount.Operation.values()) {
            Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(60, nation, minSafety, maxSafety, op);
            if (best == null) continue;

            Map.Entry<Integer, Double> bestVal = best.getValue();
            Integer safetyOrd = bestVal.getKey();
            int recommended = SpyCount.getRecommendedSpies(60, result, safetyOrd, op, nation);
            recommended = Math.min(spiesUsed, recommended);

            double odds = SpyCount.getOdds(recommended, result, safetyOrd, op, nation);

            response.append("\n - ").append(op.name()).append(": ");

            String safety = safetyOrd == 3 ? "covert" : safetyOrd == 2 ? "normal" : "quick";

            response.append(recommended + " spies on " + safety + " = " + MathMan.format(Math.min(95, odds)) + "%");
        }
        if (nation.getMissiles() > 0 || nation.getNukes() > 0) {
            long dcTime = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - (TimeUtil.getTurn() % 12));

            int maxMissile = nation.hasProject(Projects.SPACE_PROGRAM) ? 2 : 1;
            if (nation.getMissiles() == maxMissile) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.MISSILE, dcTime);
                if (!purchases.isEmpty()) {
                    response.append("\n`note: bought missile today`");
                }
            }

            if (nation.getNukes() == 1) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.NUKE, dcTime);
                if (!purchases.isEmpty()) {
                    response.append("\n`note: bought nuke today`");
                }
            }
        }

        return response.toString();
    }

    @RolePermission(value = Roles.MILCOM)
    @Command(desc="Get a list of nations to counter\n" +
            "Add `-o` to ignore nations with 5 offensive slots\n" +
            "Add `-w` to filter out weak attackers\n" +
            "Add `-a` to only list active nations (past hour)")
    public String counterWar(@Me DBNation me, @Me GuildDB db, DBWar war, @Default Set<DBNation> counterWith, @Switch("o")
    boolean allowAttackersWithMaxOffensives, @Switch("w") boolean filterWeak, @Switch("a") boolean onlyActive, @Switch("d") boolean requireDiscord, @Switch("p") boolean ping, @Switch("s") boolean allowSameAlliance) {
        Set<Integer> allies = db.getAllies(true);
        int enemyId = allies.contains(war.attacker_aa) ? war.defender_id : war.attacker_id;
        DBNation enemy = DBNation.byId(enemyId);
        if (enemy == null) throw new IllegalArgumentException("No nation found for id `" + enemyId + "`");
        return counter(me, db, enemy, counterWith, allowAttackersWithMaxOffensives, filterWeak, onlyActive, requireDiscord, ping, allowSameAlliance);
    }

    @RolePermission(value = Roles.MILCOM)
    @Command(desc="Get a list of nations to counter\n" +
            "Add `-o` to ignore nations with 5 offensive slots\n" +
            "Add `-w` to filter out weak attackers\n" +
            "Add `-a` to only list active nations (past hour)")
    public String counter(@Me DBNation me, @Me GuildDB db, DBNation target, @Default Set<DBNation> counterWith, @Switch("o")
            boolean allowAttackersWithMaxOffensives, @Switch("w") boolean filterWeak, @Switch("a") boolean onlyActive, @Switch("d") boolean requireDiscord, @Switch("p") boolean ping, @Switch("s") boolean allowSameAlliance) {
        if (counterWith == null) {
            Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId == null) {
                Set<Integer> allies = db.getAllies(true);
                if (allies.isEmpty()) {
                    aaId = me.getAlliance_id();
                    if (aaId == 0) return "No alliance or allies are set.\n" + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), "<alliance>") + "\nOR\n`" + Settings.commandPrefix(true) + "setcoalition <alliance> allies`";
                    counterWith = new HashSet<>(DBAlliance.getOrCreate(aaId).getNations(true, 10000, true));
                } else {
                    counterWith = new HashSet<>(Locutus.imp().getNationDB().getNations(allies));
                }
            } else {
                counterWith = new HashSet<>(DBAlliance.getOrCreate(aaId).getNations(true, 10000, true));
            }
        }
        counterWith.removeIf(f -> f.getVm_turns() > 0 || f.getActive_m() > 10000 || f.getPosition() <= Rank.APPLICANT.id || (f.getCities() < 10 && f.getActive_m() > 4880));
        if (requireDiscord) counterWith.removeIf(f -> f.getUser() == null);

        double score = target.getScore();
        double scoreMin = score / 1.75;
        double scoreMax = score / 0.75;

        for (DBWar activeWar : target.getActiveWars()) {
            counterWith.remove(activeWar.getNation(!activeWar.isAttacker(target)));
        }

        if (onlyActive) counterWith.removeIf(f -> !f.isOnline());
        counterWith.removeIf(nation -> nation.getScore() < scoreMin || nation.getScore() > scoreMax);
        if (!allowAttackersWithMaxOffensives) counterWith.removeIf(nation -> nation.getOff() >= (nation.hasProject(Projects.PIRATE_ECONOMY) ? 6 : 5));
        counterWith.removeIf(nation -> nation.getAlliance_id() == 0);
        counterWith.removeIf(nation -> nation.getActive_m() > TimeUnit.DAYS.toMinutes(2));
        counterWith.removeIf(nation -> nation.getVm_turns() != 0);
        counterWith.removeIf(f -> f.getAircraft() < target.getAircraft() * 0.6 && target.getAircraft() > 100);
        if (filterWeak) counterWith.removeIf(nation -> nation.getStrength() < target.getStrength());
        Set<Integer> counterWithAlliances = counterWith.stream().map(DBNation::getAlliance_id).collect(Collectors.toSet());
        if (counterWithAlliances.size() == 1 && !allowSameAlliance && counterWithAlliances.contains(target.getAlliance_id())) {
            return "Please enable `-s allowSameAlliance` to counter with the same alliance";
        }
        if (!allowSameAlliance) counterWith.removeIf(nation -> nation.getAlliance_id() == target.getAlliance_id());

        List<DBNation> attackersSorted = new ArrayList<>(counterWith);
        if (filterWeak) {
            attackersSorted = CounterGenerator.generateCounters(db, target, attackersSorted, allowAttackersWithMaxOffensives);
        } else {
            attackersSorted = CounterGenerator.generateCounters(db, target, attackersSorted, allowAttackersWithMaxOffensives, false);
        }

        if (attackersSorted.isEmpty()) {
            return "No nations available to counter";
        }

        StringBuilder response = new StringBuilder();
        response.append("**Enemy: **").append(target.toMarkdown()).append("\n**Counters**\n");

        int count = 0;
        int maxResults = 25;
        for (DBNation nation : attackersSorted) {
            if (count++ == maxResults) break;

            String statusStr = "";

            User user = nation.getUser();
            if (user != null) {
                List<Guild> mutual = user.getMutualGuilds();
                if (!mutual.isEmpty()) {
                    Guild guild = mutual.get(0);
                    Member member = guild.getMember(user);
                    if (member != null) {
                        OnlineStatus status = member.getOnlineStatus();
                        if (status != OnlineStatus.OFFLINE && status != OnlineStatus.UNKNOWN) {
                            statusStr = status.name() + " | ";
                        }
                    }
                }
            }
            if (user != null) {
                response.append(statusStr);
                response.append(user.getName() + " / ");
                if (ping) response.append(user.getAsMention());
                else response.append("`" + user.getAsMention() + "` ");
            }
            response.append(nation.toMarkdown()).append('\n');
        }

        return response.toString();
    }

    @RolePermission(value = Roles.MILCOM)
    @Command(desc = "Auto generate counters\n" +
            "Add `-p` to ping users that are added\n" +
            "Add `-a` to skip adding users\n" +
            "Add `-m` to send standard counter messages")
    public String autocounter(@Me IMessageIO channel, @Me JSONObject command, @Me WarCategory warCat, @Me DBNation me, @Me User author, @Me GuildDB db,
                              DBNation enemy, @Default Set<DBNation> attackers, @Default("3") @Range(min=0) int max
            , @Switch("p") boolean pingMembers, @Switch("a") boolean skipAddMembers, @Switch("m") boolean sendMail) {
        if (attackers == null) {
            DBAlliance alliance = db.getAlliance();
            if (alliance != null) {
                attackers = new HashSet<>(alliance.getNations(true, 2440, true));
            } else {
                throw new IllegalArgumentException("This guild is not in an alliance, please provide the nations to counter with");
            }
        }
        attackers.removeIf(f -> enemy.getCities() >= f.getCities() * 2);
        attackers.removeIf(f -> f.getUser() == null || db.getGuild().getMember(f.getUser()) == null);
        return warroom(channel, command, warCat, me, author, db, enemy, attackers, max, false, true, false, true, pingMembers, skipAddMembers, sendMail);
    }

    @RolePermission(value = Roles.MILCOM)
    @Command(desc = "Sorts the war rooms into  the correct category\n" +
            "e.g. `warcat-c1-10`")
    public String sortWarRooms(@Me WarCategory warCat) {
        int moved = warCat.sort();
        return "Done! Moved " + moved + " channels";
    }


    @RolePermission(value = Roles.MILCOM)
    @Command(desc = "Create a war room\n" +
            "Add `-p` to ping users that are added\n" +
            "Add `-a` to skip adding users\n" +
            "Add `-f` to force create channels (if checks fail)\n" +
            "Add `-m` to send standard counter messages")
    public String warroom(@Me IMessageIO channel, @Me JSONObject command, @Me WarCategory warCat, @Me DBNation me, @Me User author, @Me GuildDB db,
                          DBNation enemy, Set<DBNation> attackers, @Default("3") @Range(min=0) int max,
                          @Switch("f") boolean force, @Switch("w") boolean excludeWeakAttackers, @Switch("d") boolean requireDiscord, @Switch("o") boolean allowAttackersWithMaxOffensives, @Switch("p") boolean pingMembers, @Switch("a") boolean skipAddMembers, @Switch("m") boolean sendMail) {
        List<DBNation> attackersSorted = new ArrayList<>(attackers);

        if (excludeWeakAttackers) {
            if (requireDiscord) attackersSorted.removeIf(f -> f.getUser() == null);
            attackersSorted = CounterGenerator.generateCounters(db, enemy, attackersSorted, allowAttackersWithMaxOffensives);
            if (attackersSorted.isEmpty()) {
                return "No nations available to counter";
            }
        }

        Set<Integer> tracked = db.getAllies();
        if (!force) {
            for (DBNation attacker : attackersSorted) {
                if (!tracked.contains(attacker.getAlliance_id())) {
                    channel.create().confirmation("Error: Unsuitable counter", attacker.getNationUrlMarkup(true) + " | " + attacker.getAllianceUrlMarkup(true) + " is not an ally.", command).send();
                    return null;
                }
                if (enemy.getScore() < attacker.getScore() * 0.75 || enemy.getScore() > attacker.getScore() * 1.75) {
//                    DiscordUtil.pending(channel, message, "Error: Unsuitable counter", attacker.getNationUrlMarkup(true) + " | " + attacker.getAllianceUrlMarkup(true) + " is outside war range (see " + CM.nation.score.cmd.toSlashMention() + "). ", 'f');
                    channel.create().confirmation("Error: Unsuitable counter", attacker.getNationUrlMarkup(true) + " | " + attacker.getAllianceUrlMarkup(true) + " is outside war range (see " + CM.nation.score.cmd.toSlashMention() + "). ", command).send();
                    return null;
                }
                if (attacker.getOff() >= attacker.getMaxOff() && !allowAttackersWithMaxOffensives) {
                    channel.create().confirmation("Error: Unsuitable counter", attacker.getNationUrlMarkup(true) + " | " + attacker.getAllianceUrlMarkup(true) +  " already has max offensives. ", command).send();
                    return null;
                }
                if (attacker.getVm_turns() > 0) {
                    channel.create().confirmation( "Error: Unsuitable counter", attacker.getNationUrlMarkup(true) + " | " + attacker.getAllianceUrlMarkup(true) + " is in VM. ", command).send();
                    return null;
                }
                if (attacker.isGray() && attacker.getActive_m() > 1440 || attacker.getCities() < 10 && attacker.getActive_m() > 2000) {
                    channel.create().confirmation( "Error: Unsuitable counter", attacker.getNationUrlMarkup(true) + " | " + attacker.getAllianceUrlMarkup(true) + " is gray/inactive. ", command).send();
                    return null;
                }
                if (attacker.getNumWars() > 0 && attacker.getRelativeStrength() < 1) {
                    channel.create().confirmation( "Error: Unsuitable counter", attacker.getNationUrlMarkup(true) + " | " + attacker.getAllianceUrlMarkup(true) + " is already involved in heavy conflict.", command).send();
                    return null;
                }
            }
        }

        StringBuilder response = new StringBuilder();
        if (attackersSorted.size() > max) {
            int removed = attackersSorted.size() - max;
            response.append("Skipped adding " + removed + " nations as `max` is set to " + max + ". Provide a higher value to override this\n");
            attackersSorted = attackersSorted.subList(0, max);
        }

        WarCategory.WarRoom warChan = warCat.createChannel(author, new Consumer<String>() {
            @Override
            public void accept(String s) {
                response.append(s + "\n");
            }
        }, pingMembers, !skipAddMembers, sendMail, enemy, attackersSorted);

        response.append(warChan.getChannel().getAsMention());

        me.setMeta(NationMeta.INTERVIEW_WAR_ROOM, (byte) 1);

        if (!sendMail && db.getOrNull(GuildDB.Key.API_KEY) != null) response.append("\n - add `-m` to send standard counter instructions");
        if (!pingMembers && db.getOrNull(GuildDB.Key.API_KEY) != null) response.append("\n - add `-p` to ping users in the war channel");

        if (!skipAddMembers) {
            for (DBNation dbNation : attackersSorted) {
                response.append("\nAdded " + dbNation.toMarkdown(false, true, false, true, false));
            }
        }

        return response.toString();
    }

    @RolePermission(value = Roles.MEMBER)
    @Command(desc = "Update the pin in the war room")
    public String warpin(@Me WarCategory.WarRoom warRoom) {
        IMessageBuilder message = warRoom.updatePin(true);
        return "Updated: " + DiscordUtil.getChannelUrl(warRoom.channel) + "/" + message.getId();
    }

    @RolePermission(value = Roles.MILCOM)
    @Command(desc = "Run this command in a war room to assign it to a category\n" +
            "`{prefix}WarCat raid`")
    public String warcat(@Me WarCategory warCat, @Me WarCategory.WarRoom waRoom, @Me TextChannel channel, @Filter("warcat.*") Category category) {
        if (category.equals(channel.getParentCategory())) {
            return "Already in category: " + category.getName();
        }

        channel.getManager().setParent(category).complete();

        return "Set category for " + channel.getAsMention() + " to " + category.getName();
    }

    private Map<Long, List<String>> blitzTargetCache = new HashMap<>();

    @RolePermission(value = Roles.MILCOM)
    @Command(desc = "Generate a list of possible blitz targets (for practice)", aliases = {"blitzpractice","blitztargets"})
    public String BlitzPractice(@Me GuildDB db, int topX, @Me IMessageIO channel, @Me JSONObject command, @Switch("p") Integer page) {
        Set<Integer> dnr = db.getCoalition("allies");

        List<String> results = blitzTargetCache.getOrDefault(db.getGuild().getIdLong() + topX, new ArrayList<>());
        if (results.isEmpty()) {

            List<DBAlliance> alliances = new ArrayList<>(Locutus.imp().getNationDB().getAlliances(true, true, true, 1000));
            Set<DBAlliance> top30 = new LinkedHashSet<>(Locutus.imp().getNationDB().getAlliances(true, true, true, topX));

            outer:
            for (DBAlliance alliance : alliances) {
                if (top30.contains(alliance)) continue;
                Set<DBNation> nations = alliance.getNations(true, 5000, true);
                if (nations.size() <= 2) continue;
                for (Map.Entry<Integer, Treaty> entry : alliance.getDefenseTreaties().entrySet()) {
                    if (dnr.contains(entry.getKey())) continue outer;
                    if (top30.contains(DBAlliance.getOrCreate(entry.getKey()))) continue outer;
                }
                int slots = 0;
                for (DBNation nation : nations) {
                    if (nation.isBeige()) continue;
                    slots += 3 - nation.getDef();
                }
                if (slots <= 2) continue;

                int myRank = alliances.indexOf(alliance);
                int largestAlly = myRank;
                boolean hasProtection = false;
                for (Map.Entry<Integer, Treaty> entry : alliance.getDefenseTreaties().entrySet()) {
                    DBAlliance other = DBAlliance.getOrCreate(entry.getKey());
                    int min = alliances.indexOf(other);
                    if (min != -1 && min < largestAlly) {
                        largestAlly = min;
                        hasProtection = true;
                    }
                }
                String protectionStr = hasProtection ? " Allied: #" + largestAlly : "";

                List<DBAlliance> sphere = alliance.getSphereRanked();
                String sphereStr = sphere.isEmpty() ? "None" : sphere.get(0).getMarkdownUrl();

                results.add(alliance.getMarkdownUrl() + " #" + myRank + protectionStr + " | sphere?:" + sphereStr + " | members:" + nations.size() + " | slots:" + slots);
            }
            blitzTargetCache.put(db.getGuild().getIdLong() + topX, results);
        }

        int perPage = 10;
        String title = "Blitz targets";

        channel.create().paginate(title, command, page, perPage, results).send();

        return null;
    }
}
