package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.Spyop;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.battle.SpyBlitzGenerator;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MailTargets extends Command {
    public MailTargets() {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return (Roles.MAIL.has(user, server)) && Locutus.imp().getGuildDB(server).isValidAlliance();
    }

    @Override
    public String help() {
        return super.help() + " <war-sheet> <spy-sheet> <nation-filter=*> [message]";
    }

    @Override
    public String desc() {
        return "Send war and spy blitz targets to a nation as ingame message";
    }

    public static String getStrengthInfo(DBNation nation) {
        String msg = "Ground:" + (int) nation.getGroundStrength(true, false) + ", Air: " + nation.getAircraft() + ", cities:" + nation.getCities();

        if (nation.getActive_m() > 10000) msg += " (inactive)";
        else {
            msg += " (" + ((int) (nation.avg_daily_login() * 100)) + "% active)";
        }

        return msg;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 3 && args.size() != 4) return usage(event);

        Map<DBNation, Set<DBNation>> warDefAttMap = new HashMap<>();
        Map<DBNation, Set<DBNation>> spyDefAttMap = new HashMap<>();
        Map<DBNation, Set<Spyop>> spyOps = new HashMap<>();

        if (!args.get(0).equalsIgnoreCase("null")) {
            SpreadSheet blitzSheet = SpreadSheet.create(args.get(0));
            warDefAttMap = BlitzGenerator.getTargets(blitzSheet, 0, f -> 3, 0.75, 1.75, true, f -> true, (a, b) -> {});
        }

        if (!args.get(1).equalsIgnoreCase("null")) {
            SpreadSheet spySheet = SpreadSheet.create(args.get(1));
            try {
                spyDefAttMap = BlitzGenerator.getTargets(spySheet, 0, f -> 3, 0.4, 1.5, false, f -> true, (a, b) -> {});
                spyOps = SpyBlitzGenerator.getTargets(spySheet, 0);
            } catch (NullPointerException e) {
                e.printStackTrace();
                spyDefAttMap = BlitzGenerator.getTargets(spySheet, 4, f -> 3, 0.4, 1.5, false, f -> true, (a, b) -> {});
                spyOps = SpyBlitzGenerator.getTargets(spySheet, 4);
            }
        }

        String[] keys = { Locutus.imp().getRootAuth().getApiKey() };
//        Auth auth = Locutus.imp().getRootAuth();
        if (flags.contains('l') || (!Roles.MILCOM.hasOnRoot(event.getAuthor()) && !Roles.INTERNAL_AFFAIRS.hasOnRoot(event.getAuthor()))) {
            keys = Locutus.imp().getGuildDB(event).getOrThrow(GuildDB.Key.API_KEY);
        }

        String header = "";
        if (args.size() >= 4) {
            header = args.get(3);

            if (!Roles.MAIL.has(author, guild)) return "You need the MAIL role on discord (see `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "aliasRole`) to add the custom message: `" + header + "`";
        }

        Map<DBNation, Set<DBNation>> warAttDefMap = BlitzGenerator.reverse(warDefAttMap);
        Map<DBNation, Set<DBNation>> spyAttDefMap = BlitzGenerator.reverse(spyDefAttMap);
        Set<DBNation> allAttackers = new LinkedHashSet<>();
        allAttackers.addAll(warAttDefMap.keySet());
        allAttackers.addAll(spyAttDefMap.keySet());

        String date = TimeUtil.YYYY_MM_DD.format(ZonedDateTime.now());
        String subject = "Targets-" + date + "/" + event.getChannel().getIdLong();

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

        Set<DBNation> allowedNations = DiscordUtil.parseNations(guild, args.get(2));
        if (allowedNations.isEmpty()) return "No nations found for: " + args.get(2);

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
                // Rebuy units after each attack

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
                    mail.append(" - If these targets don't work, reply with the word `more` and i'll send you some more targets (or spy who you are attacking)\n");
                }
                if (killSpies != 0) {
                    mail.append(" - If selecting (but not executing) 1 spy on quick (gather intel) yields >50% odds, it means the enemy has no spies left.\n");
                    mail.append(" - If an enemy has 0 spies, you can use 5|spies|quick (99%) for killing units.\n");
                }

                if (intelOps != myAttackOps.size()) {
                    mail.append(" - Results may be outdated when you read it, so check they still have units to spy\n");
                }

                mail.append(
                        " - If the op doesn't require it (and it says >50%), you don't have to use more spies or covert\n" +
                        " - Reply to this message with any spy reports you do against enemies (even if not these targets)\n" +
                        " - Remember to buy spies every day\n\n");

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

        String key = keys[0];
        Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(keys[0]);
        if (nationId == null) return "Invalid `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore API_KEY`";
        DBNation sender = DBNation.byId(nationId);

        if (!flags.contains('f')) {
            String title = totalWarTargets + " wars & " + totalSpyTargets + " spyops";
            String pending = Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "pending '" + title + "' " + DiscordUtil.trimContent(event.getMessage().getContentRaw()) + " -f";

            Set<Integer> alliances = new LinkedHashSet<>();
            for (DBNation nation : mailTargets.keySet()) alliances.add(nation.getAlliance_id());
            String embedTitle = title + " to " + mailTargets.size() + " nations";
            if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances";

            StringBuilder body = new StringBuilder();
            body.append("subject: " + subject + "\n");
            body.append("Send from: " + sender.getNationUrlMarkup(true) + "\n");

            if (sender.getNation_id() == Settings.INSTANCE.NATION_ID) {
                body.append("\nAdd `-l` to send from your alliance instead of Borg");
            }

            DiscordUtil.createEmbedCommand(event.getChannel(), embedTitle, body.toString(), "\u2705", pending);
            return event.getAuthor().getAsMention();
        }

        Message msg = RateLimitUtil.complete(event.getChannel().sendMessage("Sending messages..."));
        for (Map.Entry<DBNation, Map.Entry<String, String>> entry : mailTargets.entrySet()) {
            DBNation attacker = entry.getKey();
            subject = entry.getValue().getKey();
            String body = entry.getValue().getValue();

            attacker.sendMail(keys, subject, body);

            if (flags.contains('d')) {
                String markup = MarkupUtil.htmlToMarkdown(body);
                try {
                    attacker.sendDM("**" + subject + "**:\n" + markup);
                } catch (Throwable e) {
                    RateLimitUtil.queue(event.getChannel().sendMessage(e.getMessage() + " for " + attacker.getNation()));
                    e.printStackTrace();
                }
            }

            if (System.currentTimeMillis() - start > 10000) {
                start = System.currentTimeMillis();
                RateLimitUtil.queue(event.getChannel().editMessageById(msg.getIdLong(), "Sending to " + attacker.getNation()));
            }
        }


        RateLimitUtil.queue(event.getChannel().deleteMessageById(msg.getIdLong()));

        return "Done, sent " + sent + " messages";
    }
}
