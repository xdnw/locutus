package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.rankings.builder.GroupedRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationScoreMap;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.battle.sim.AttackTypeNode;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.offshore.test.IAChannel;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.nation.MultiReport;
import link.locutus.discord.util.task.roles.IAutoRoleTask;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.SAllianceContainer;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static link.locutus.discord.util.math.ArrayUtil.memorize;
import static org.example.jooq.bank.Tables.TRANSACTIONS_2;

public class UtilityCommands {
    @Command(desc = "List the color blocs")
    public String calculateColorRevenue(@Default Set<DBNation> forceAqua,
                                        @Default Set<DBNation> forceBlack,
                                        @Default Set<DBNation> forceBlue,
                                        @Default Set<DBNation> forceBrown,
                                        @Default Set<DBNation> forceGreen,
                                        @Default Set<DBNation> forceLime,
                                        @Default Set<DBNation> forceMaroon,
                                        @Default Set<DBNation> forceOlive,
                                        @Default Set<DBNation> forceOrange,
                                        @Default Set<DBNation> forcePink,
                                        @Default Set<DBNation> forcePurple,
                                        @Default Set<DBNation> forceRed,
                                        @Default Set<DBNation> forceWhite,
                                        @Default Set<DBNation> forceYellow,
                                        @Default Set<DBNation> forceGrayOrBeige
    ) {
        Map<NationColor, Set<DBNation>> changeColors = new HashMap<>();
        changeColors.put(NationColor.AQUA, forceAqua);
        changeColors.put(NationColor.BLACK, forceBlack);
        changeColors.put(NationColor.BLUE, forceBlue);
        changeColors.put(NationColor.BROWN, forceBrown);
        changeColors.put(NationColor.GREEN, forceGreen);
        changeColors.put(NationColor.LIME, forceLime);
        changeColors.put(NationColor.MAROON, forceMaroon);
        changeColors.put(NationColor.OLIVE, forceOlive);
        changeColors.put(NationColor.ORANGE, forceOrange);
        changeColors.put(NationColor.PINK, forcePink);
        changeColors.put(NationColor.PURPLE, forcePurple);
        changeColors.put(NationColor.RED, forceRed);
        changeColors.put(NationColor.WHITE, forceWhite);
        changeColors.put(NationColor.YELLOW, forceYellow);
        changeColors.put(NationColor.GRAY, forceGrayOrBeige);

        Map<DBNation, NationColor> newColors = new HashMap<>();

        for (Map.Entry<NationColor, Set<DBNation>> entry : changeColors.entrySet()) {
            if (entry.getValue() == null) continue;
            for (DBNation nation : entry.getValue()) {
                if (nation.getColor() == entry.getKey() || ((nation.isGray() || nation.isBeige()) && entry.getKey() == NationColor.GRAY)) continue;

                newColors.put(nation, entry.getKey());
            }
        }

        Map<NationColor, Integer> oldRevenueMap = new LinkedHashMap<>();
        Map<NationColor, Double> revenueTotalMap = new LinkedHashMap<>();
        Map<NationColor, Integer> numNationsMap = new LinkedHashMap<>();
        Map<NationColor, Integer> oldNumNationsMap = new LinkedHashMap<>();

        Set<NationColor> requiresScaling = new HashSet<>();
        List<Map.Entry<Double, Integer>> scalingCounts = new ArrayList<>();

        for (NationColor color : NationColor.values()) {
            Set<DBNation> oldNations = Locutus.imp().getNationDB().getNationsMatching(f -> f.getVm_turns() == 0 && f.getColor() == color);
            boolean isChanged = false;
            for (Map.Entry<DBNation, NationColor> entry : newColors.entrySet()) {
                if (entry.getKey().getColor() == color || entry.getValue() == color) {
                    isChanged = true;
                    break;
                }
            }

            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(f -> f.getVm_turns() == 0 && newColors.getOrDefault(f, f.getColor()) == color);

            oldNumNationsMap.put(color, oldNations.size());
            if (isChanged) numNationsMap.put(color, nations.size());
            else numNationsMap.put(color, oldNations.size());

            int oldColorRev = color.getTurnBonus();

            oldRevenueMap.put(color, oldColorRev);

            if (color == NationColor.BEIGE || color == NationColor.GRAY) {
                continue;
            }
            if (!newColors.isEmpty()) {
                Supplier<Double> oldRevenueTotal = memorize(() -> PnwUtil.convertedTotal(new SimpleNationList(oldNations).getRevenue()));
                Supplier<Double> newRevenueTotal = memorize(() -> PnwUtil.convertedTotal(new SimpleNationList(nations).getRevenue()));

                double scalingFactor = -1;
                if (oldColorRev > 0 && oldColorRev < 75_000) {
                    double oldRevTotalEstimate = oldRevenueTotal.get();
                    double oldRevTotalExact = ((double) oldColorRev) * oldNations.size() * oldNations.size();

                    scalingFactor = (1d / oldRevTotalEstimate) * oldRevTotalExact;
                    scalingCounts.add(new AbstractMap.SimpleEntry<>(scalingFactor, nations.size()));
                }
                if (isChanged) {
                    double newColorRev = newRevenueTotal.get();
                    if (scalingFactor != -1) {
                        newColorRev = newColorRev * scalingFactor;
                    } else {
                        requiresScaling.add(color);
                    }
                    revenueTotalMap.put(color, newColorRev);
                }
            }
        }

        // calculate scaling factor
        double scalingFactor = 1;
        if (!scalingCounts.isEmpty() && !requiresScaling.isEmpty()) {
            double totalValue = 0;
            long num = 0L;
            for (Map.Entry<Double, Integer> entry : scalingCounts) {
                totalValue += entry.getKey() * entry.getValue();
                num += entry.getValue();
            }
            scalingFactor = totalValue / num;
        }

        StringBuilder lines = new StringBuilder();
        for (NationColor color : NationColor.values) {
            lines.append(color + " | ");

            int numNations = numNationsMap.get(color);
            int oldNumNations = oldNumNationsMap.get(color);
            lines.append("nations: " + MathMan.format(numNations));
            if (oldNumNations != numNations) {
                String signSym = (numNations > oldNumNations) ? "+" : "-";
                lines.append(" (").append(signSym).append(MathMan.format(Math.abs(numNations - oldNumNations)) + ")");
            }

            int oldTurnIncome = oldRevenueMap.get(color);
            int newTurnIncome = oldTurnIncome;

            Double revenueTotal = revenueTotalMap.get(color);
            if (revenueTotal != null) {
                if (requiresScaling.contains(color)) {
                    revenueTotal *= scalingFactor;
                }
                newTurnIncome = (int) Math.round(revenueTotal / (numNations * numNations));
            }
            newTurnIncome = Math.max(0, Math.min(75_000, newTurnIncome));
            lines.append(" | bonus: " + newTurnIncome);
            if (oldTurnIncome != newTurnIncome) {
                String signSym = (newTurnIncome > oldTurnIncome) ? "+" : "-";
                lines.append(" (").append(signSym).append(MathMan.format(Math.abs(newTurnIncome - oldTurnIncome)) + ")");
            }
            lines.append("\n");
        }
        return lines.toString();
    }

    @Command(desc = "list channels")
    @RolePermission(Roles.ADMIN)
    public String channelCount(@Me IMessageIO channel, @Me Guild guild) {
        StringBuilder channelList = new StringBuilder();

        List<Category> categories = guild.getCategories();
        for (Category category : categories) {
            channelList.append(category.getName() + "\n");

            for (GuildChannel catChannel : category.getChannels()) {
                String prefix = "+ ";
                if (catChannel instanceof VoiceChannel) {
                    prefix = "\uD83D\uDD0A ";
                }
                channelList.append(prefix + catChannel.getName() + "\n");
            }

            channelList.append("\n");
        }

        channel.create().file(guild.getChannels().size() + "/500 channels", channelList.toString()).send();
        return null;
    }

    @Command(desc = "Find potential offshores used by an alliance")
    @RolePermission(Roles.ECON)
    public String findOffshore(@Me IMessageIO channel, @Me JSONObject command, DBAlliance alliance, @Default @Timestamp Long cutoffMs) {
        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();

        List<Transaction2> transactions = Locutus.imp().getBankDB().getToNationTransactions(cutoffMs);
        long now = System.currentTimeMillis();
        transactions.removeIf(t -> {
            DBNation nation = DBNation.byId((int) t.getReceiver());
            return nation == null || t.getDate() > now || t.getSender() == alliance.getAlliance_id() || nation.getAlliance_id() != alliance.getAlliance_id() || (t.note != null && t.note.contains("defeated"));
        });
        Map<Integer, Integer> numTransactions = new HashMap<>();
        Map<Integer, Double> valueTransferred = new HashMap<>();

        for (Transaction2 t : transactions) {
            numTransactions.put((int) t.getSender(), numTransactions.getOrDefault((int) t.getSender(), 0) + 1);

            double value = PnwUtil.convertedTotal(t.resources);

            valueTransferred.put((int) t.getSender(), valueTransferred.getOrDefault((int) t.getSender(), 0d) + value);
        }



        new SummedMapRankBuilder<>(numTransactions).sort().name(new Function<Map.Entry<Integer, Integer>, String>() {
            @Override
            public String apply(Map.Entry<Integer, Integer> e) {
                return PnwUtil.getName(e.getKey(), true) + ": " + e.getValue() + " | $" + MathMan.format(valueTransferred.get(e.getKey()));
            }
        }).build(channel, command, "Potential offshores", true);

        return null;
    }

    @Command(desc = "See who was online at the time of a spy op (UTC)")
    @RolePermission(Roles.MEMBER)
    @WhitelistPermission
    public String findSpyOp(@Me DBNation me, String times, int defenderSpies, @Default DBNation defender) {
        if (defender == null) defender = me;

        Set<Integer> ids = new HashSet<>();
        Map<DBSpyUpdate, Long> updatesTmp = new HashMap<>();
        long interval = TimeUnit.MINUTES.toMillis(3);

        for (String timeStr : times.split(",")) {
            long timestamp = TimeUtil.parseDate(TimeUtil.MMDD_HH_MM_A, timeStr, true);
            List<DBSpyUpdate> updates = Locutus.imp().getNationDB().getSpyActivity(timestamp, interval);
            for (DBSpyUpdate update : updates) {
//                nations.put(update.nation_id, nations.getOrDefault(update.nation_id, 0) + 1);
                DBNation nation = DBNation.byId(update.nation_id);
                if (nation == null) continue;
                if (!defender.isInSpyRange(nation)) continue;

                if (ids.contains(update.nation_id)) continue;
                ids.add(update.nation_id);
                updatesTmp.put(update, Math.abs(timestamp - update.timestamp));

            }
        }

        if (updatesTmp.isEmpty()) return "No results (0)";

        Map<DBNation, Map.Entry<Double, String>> allOdds = new HashMap<>();

        for (Map.Entry<DBSpyUpdate, Long> entry : updatesTmp.entrySet()) {
            DBSpyUpdate update = entry.getKey();
            long diff = entry.getValue();
//            if (update.spies <= 0) continue;
//            if (update.change == 0) continue;

            DBNation attacker = Locutus.imp().getNationDB().getNation(update.nation_id);
            if (attacker == null || (defender != null && !attacker.isInSpyRange(defender)) || attacker.getNation_id() == defender.getNation_id()) continue;

            int spiesUsed = update.spies;


            int safety = 3;
            int uncertainty = -1;
            boolean foundOp = false;
            boolean spySatellite = Projects.SPY_SATELLITE.has(update.projects);
            boolean intelligence = Projects.INTELLIGENCE_AGENCY.has(update.projects);

            if (spiesUsed == -1) spiesUsed = attacker.getSpies();

            double odds = SpyCount.getOdds(spiesUsed, defenderSpies, safety, SpyCount.Operation.SPIES, defender);
            if (spySatellite) odds = Math.min(100, odds * 1.2);
//            if (odds < 10) continue;

            double ratio = odds;

            int numOps = (int) Math.ceil((double) spiesUsed / attacker.getSpies());

            if (!foundOp) {
                ratio -= ratio * 0.1 * Math.abs((double) diff / interval);

                ratio *= 0.1;

                if (attacker.getPosition() <= 1) ratio *= 0.1;
            } else {
                ratio -= 0.1 * ratio * uncertainty;

                if (attacker.getPosition() <= 1) ratio *= 0.5;
            }

            StringBuilder message = new StringBuilder();

            if (foundOp) message.append("**");
            message.append(MathMan.format(odds) + "%");
            if (spySatellite) message.append(" | SAT");
            if (intelligence) message.append(" | IA");
            message.append(" | " + spiesUsed + "? spies (" + safety + ")");
            long diff_m = Math.abs(diff / TimeUnit.MINUTES.toMillis(1));
            message.append(" | " + diff_m + "m");
            if (foundOp) message.append("**");

            allOdds.put(attacker, new AbstractMap.SimpleEntry<>(ratio, message.toString()));
        }

        List<Map.Entry<DBNation, Map.Entry<Double, String>>> sorted = new ArrayList<>(allOdds.entrySet());
        sorted.sort((o1, o2) -> Double.compare(o2.getValue().getKey(), o1.getValue().getKey()));

        if (sorted.isEmpty()) {
            return "No results";
        }

        StringBuilder response = new StringBuilder();
        for (Map.Entry<DBNation, Map.Entry<Double, String>> entry : sorted) {
            DBNation att = entry.getKey();

            response.append(att.getNation() + " | " + att.getAllianceName() + "\n");
            response.append(entry.getValue().getValue()).append("\n\n");
        }

        return response.toString();

    }

    @Command
    @RolePermission(value = {Roles.ADMIN}, root = true)
    public String listOffshores() {
        StringBuilder response = new StringBuilder();

        Map<Integer, List<DBNation>> map = Locutus.imp().getNationDB().getNationsByAlliance(false, false, true, true);
        outer:
        for (Map.Entry<Integer, List<DBNation>> entry : map.entrySet()) {
            DBAlliance aa = DBAlliance.getOrCreate(entry.getKey());
            int aaid = aa.getAlliance_id();
            List<DBNation> members = entry.getValue();

            if (members.size() > 3) {
                continue;
            }
            if (members.size() == 0) {
                continue;
            }
            int maxCities = 0;
            int maxAge = 0;
            int activeMembers = 0;
            int numVM = 0;

            for (DBNation member : members) {
                if (member.getVm_turns() > 0) numVM++;
                if (member.getVm_turns() == 0 && member.getActive_m() > 10000) {
                    continue outer;
                }
                if (member.getVm_turns() == 0) {
                    activeMembers++;
                    maxCities = Math.max(maxCities, member.getCities());
                    maxAge = Math.max(maxAge, member.getAgeDays());
                }
            }
            if (numVM >= 3) {
                continue;
            }
            if (activeMembers == 0) {
                continue;
            }


            for (DBWar war : Locutus.imp().getWarDb().getWarsByAlliance(aa.getAlliance_id())) {

                List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacksByWar(war);
                attacks.removeIf(f -> f.attack_type != AttackType.A_LOOT);
                if (attacks.size() != 1) continue;

                DBAttack attack = attacks.get(0);
                int attAA = war.isAttacker(attack.attacker_nation_id) ? war.attacker_aa : war.defender_aa;
                if (attAA == aa.getAlliance_id()) continue;
                boolean lowMil = false;
                for (DBNation member : members) {
                    if (member.getVm_turns() != 0) continue;
                    if (member.getActive_m() > 7200) {
                        continue outer;
                    }
                    if (member.isGray()) {
                        continue outer;
                    }
                    if (member.getCities() > 1 && member.getAircraftPct() < 0.8) {
                        lowMil = true;
                    }
                }
                if (lowMil) {
                    for (DBNation member : members) {
                        if (member.daysSinceLastOffensive() < 30) lowMil = false;
                    }
                    if (lowMil) {
                        continue outer;
                    }
                }
            }

            response.append(aa.getName() + ": " + aa.getUrl()).append("\n");

            List<String> treaties = aa.getTreaties().keySet().stream().map(f -> PnwUtil.getName(f, true)).collect(Collectors.toList());
            if (!treaties.isEmpty()) {
                response.append(" - Treaties: " + StringMan.join(treaties, ", ")).append("\n");
            } else{
                Set<String> previousOfficer = new HashSet<>();
                for (DBNation member : members) {
                    if (member.getVm_turns() != 0) continue;
                    for (Map.Entry<Integer, Map.Entry<Long, Rank>> aaEntry : member.getAllianceHistory().entrySet()) {
                        int previousAA = aaEntry.getKey();
                        Map.Entry<Long, Rank> timeRank = aaEntry.getValue();

                        Rank rank = timeRank.getValue();
                        if (rank.id >= Rank.OFFICER.id) {
                            List<DBNation> alliance = map.get(previousAA);
                            if (DBAlliance.getOrCreate(previousAA).getScore() < 50000) continue;
                            previousOfficer.add(PnwUtil.getName(previousAA, true));

                        }
                    }
                }
                if (!previousOfficer.isEmpty()) {
                    response.append(" - Previous officer in: " + StringMan.join(previousOfficer, ", ")).append("\n");
                }
            }
            response.append(" - Age: " + maxAge).append("\n");
            response.append(" - Cities: " + maxCities).append("\n");
        }
        return response.toString();
    }

    @Command(desc = "Mark an alliance as the offshore of another")
    public String markAsOffshore(@Me User author, @Me DBNation me, DBAlliance offshore, DBAlliance parent) {
        if (!Roles.ADMIN.hasOnRoot(author)) {
            DBAlliance expectedParent = offshore.findParentOfThisOffshore();
            if (expectedParent != parent) {
                if (me.getAlliance_id() != offshore.getAlliance_id()) {
                    return "You are not in " + offshore.getName();
                }
                if (me.getPositionEnum().id < Rank.OFFICER.id) return "You are not leader in " + offshore.getName();

                GuildDB db = parent.getGuildDB();
                if (db != null) {
                    Guild guild = db.getGuild();
                    Member member = guild.getMember(author);
                    if (member == null) return "You are not member in " + guild;
                    if (member.hasPermission(Permission.ADMINISTRATOR)) return "You are not admin in: " + guild;
                }
            }
        }
        offshore.setMeta(AllianceMeta.OFFSHORE_PARENT, parent.getAlliance_id());
        return "Set " + offshore.getName() + " as an offshore for " + parent.getName();
    }

    @Command
    @RolePermission(value = {Roles.ECON, Roles.MILCOM}, any = true)
    public String findOffshores(@Timestamp long cutoff, Set<DBAlliance> enemiesList, @Default() Set<DBAlliance> alliesList) {
        if (alliesList == null) alliesList = Collections.emptySet();
        Set<Integer> enemies = enemiesList.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());
        Set<Integer> allies = alliesList.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());

        long start1 = System.currentTimeMillis();
        Map<Integer, Map<Integer, Map.Entry<Long, Rank>>> removes = Locutus.imp().getNationDB().getRemovesByNationAlliance(enemies, cutoff);

        Map<Integer, Integer> offshoresWar = new HashMap<>();
        Map<Integer, Integer> offshoresTreaty = new HashMap<>();
        Map<Integer, Integer> offshoresOfficer = new HashMap<>();
        Map<Integer, Integer> offshoresMember = new HashMap<>();
        Map<Integer, Map.Entry<Integer, Map.Entry<Integer, Double>>> offshoresTransfers = new HashMap<>(); // Ofshore AA id -> (parent AA id, num transfers, value transferred)

        long totalBankDiff = 0;

        Map<Integer, List<DBNation>> alliances = Locutus.imp().getNationDB().getNationsByAlliance(false, false, true, true);
        outer:
        for (Map.Entry<Integer, List<DBNation>> entry : alliances.entrySet()) {
            int aaId = entry.getKey();
            if (allies.contains(aaId) || enemies.contains(aaId)) continue;
            List<DBNation> nations = new ArrayList<>(entry.getValue());
            nations.removeIf(f -> f.getVm_turns() > 0);

            if (nations.size() > 2) continue;

            for (DBNation nation : nations) {
                if (nation.getActive_m() > 7200) continue outer;
            }

            Set<DBAlliance> treaties = DBAlliance.getOrCreate(aaId).getTreatiedAllies();
            for (DBAlliance treaty : treaties) {
                if (allies.contains(treaty.getAlliance_id())) continue outer;
            }


            for (DBAlliance treatiedAlly : treaties) {
                if (enemies.contains(treatiedAlly.getAlliance_id())) {
                    offshoresTreaty.put(aaId, treatiedAlly.getAlliance_id());
                }
            }

            long start = System.currentTimeMillis();
            List<Transaction2> transfers = Locutus.imp().getBankDB().getTransactions(TRANSACTIONS_2.SENDER_ID.eq((long) aaId).and(TRANSACTIONS_2.SENDER_TYPE.eq(2)).and(TRANSACTIONS_2.TX_DATETIME.gt(cutoff)));
            totalBankDiff += System.currentTimeMillis() - start;

            transfers.removeIf(f -> f.sender_id != aaId || f.tx_datetime < cutoff);
            transfers.removeIf(f -> f.note != null && f.note.contains("of the alliance bank inventory"));
            if (!transfers.isEmpty()) {

                Map<Integer, Integer> numTransfers = new HashMap<>();
                Map<Integer, Double> valueTransfers = new HashMap<>();

                for (Transaction2 tx : transfers) {
                    if (tx.banker_nation == tx.receiver_id) continue;
                    DBNation receiver = DBNation.byId((int) tx.getReceiver());
                    if (receiver != null && enemies.contains(receiver.getAlliance_id())) {
                        int existingNum = numTransfers.getOrDefault(receiver.getAlliance_id(), 0);
                        double existingVal = valueTransfers.getOrDefault(receiver.getAlliance_id(), 0d);

                        numTransfers.put(receiver.getAlliance_id(), existingNum + 1);
                        valueTransfers.put(receiver.getAlliance_id(), existingVal + tx.convertedTotal());
                    }
                }

                if (!numTransfers.isEmpty()) {
                    for (Map.Entry<Integer, Integer> transferEntry : numTransfers.entrySet()) {
                        int enemyAAId = transferEntry.getKey();
                        int num = transferEntry.getValue();
                        double val = valueTransfers.get(enemyAAId);

                        Map.Entry<Integer, Double> numValPair = new AbstractMap.SimpleEntry<>(num, val);
                        offshoresTransfers.put(aaId, new AbstractMap.SimpleEntry<>(enemyAAId, numValPair));
                    }

                }
            }

            for (DBNation nation : nations) {
                for (DBWar war : nation.getActiveWars()) {
                    DBNation other = war.getNation(!war.isAttacker(nation));
                    if (other == null) continue;
                    if (allies.contains(other.getAlliance_id())) {
                        offshoresWar.put(aaId, other.getAlliance_id());
                    }
                }

                Map<Integer, Map.Entry<Long, Rank>> nationRemoves = removes.getOrDefault(nation.getNation_id(), Collections.emptyMap());
                for (Map.Entry<Integer, Map.Entry<Long, Rank>> aaEntry : nationRemoves.entrySet()) {
                    int previousAA = aaEntry.getKey();
                    if (!enemies.contains(previousAA)) continue;
                    Map.Entry<Long, Rank> timeRank = aaEntry.getValue();
                    if (timeRank.getKey() < cutoff) continue;

                    Rank rank = timeRank.getValue();
                    if (rank.id >= Rank.OFFICER.id) {
                        offshoresOfficer.put(aaId, previousAA);
                    } else {
                        offshoresMember.put(aaId, previousAA);
                    }
                }
            }
        }

        {
            StringBuilder response = new StringBuilder();
            if (!offshoresWar.isEmpty()) {
                response.append("Attacking us:\n");
                for (Map.Entry<Integer, Integer> entry : offshoresWar.entrySet()) {
                    response.append(PnwUtil.getName(entry.getKey(), true) + " <" + PnwUtil.getAllianceUrl(entry.getKey()) + "> attacking " + PnwUtil.getName(entry.getValue(), true) + "\n");
                }
                response.append("\n");
            }

            if (!offshoresTreaty.isEmpty()) {
                response.append("Has treaty:\n");
                for (Map.Entry<Integer, Integer> entry : offshoresTreaty.entrySet()) {
                    response.append(PnwUtil.getName(entry.getKey(), true) + " <" + PnwUtil.getAllianceUrl(entry.getKey()) + "> treatied " + PnwUtil.getName(entry.getValue(), true) + "\n");
                }
                response.append("\n");
            }

            if (!offshoresOfficer.isEmpty()) {
                response.append("Former Officer:\n");
                for (Map.Entry<Integer, Integer> entry : offshoresOfficer.entrySet()) {
                    response.append(PnwUtil.getName(entry.getKey(), true) + " <" + PnwUtil.getAllianceUrl(entry.getKey()) + "> formerly officer in " + PnwUtil.getName(entry.getValue(), true) + "\n");
                }
                response.append("\n");
            }

            if (!offshoresMember.isEmpty()) {
                response.append("Former Member:\n");
                for (Map.Entry<Integer, Integer> entry : offshoresMember.entrySet()) {
                    response.append(PnwUtil.getName(entry.getKey(), true) + " <" + PnwUtil.getAllianceUrl(entry.getKey()) + "> formerly member in " + PnwUtil.getName(entry.getValue(), true) + "\n");
                }
                response.append("\n");
            }

            if (!offshoresTransfers.isEmpty()) {
                response.append("Bank transfers:\n");
                for (Map.Entry<Integer, Map.Entry<Integer, Map.Entry<Integer, Double>>> entry : offshoresTransfers.entrySet()) {
                    Map.Entry<Integer, Map.Entry<Integer, Double>> transferEntry = entry.getValue();
                    int otherAAId = transferEntry.getKey();
                    int num = transferEntry.getValue().getKey();
                    double value = transferEntry.getValue().getValue();

                    response.append(PnwUtil.getName(entry.getKey(), true) + " <" + PnwUtil.getAllianceUrl(entry.getKey()) + "> has " + num + " transfers with" + PnwUtil.getName(otherAAId, true) + " worth ~$" + MathMan.format(value) + "\n");
                }
            }
            if (response.length() == 0) return "No results founds in the specified timeframe";
            return "**__possible offshores__**\n" + response.toString();
        }
    }

    @Command(aliases = {"nap", "napdown"})
    public String nap() {
        // TODO update this every NAP
        long turnEnd = 227742;
        String napLink = "https://politicsandwar.fandom.com/wiki/Brawlywood";
        long turn = TimeUtil.getTurn();

        long diff = (turnEnd - turn) * TimeUnit.HOURS.toMillis(2);

        if (diff <= 0) {
            String[] images = new String[] {
                    "3556290",
                    "13099758",
                    "5876175",
                    "15996537",
                    "13578642",
                    "11331727",
                    "13776910",
                    "3381639",
                    "8476393",
                    "3581186",
                    "13354647",
                    "5652813",
                    "7645437",
                    "9507023",
                    "8832122",
                    "4735568",
                    "7391113",
                    "5196956",
                    "11955188",
                    "5483839",
                    "12321108",
                    "17686107",
                    "12262416",
                    "13093956",
                    "4909014",
                    "17318955",
                    "4655431",
                    "14853646",
                    "14464332",
                    "14583973",
                    "18127160",
                    "4897716",
                    "15353915",
                    "8503723"
            };
            String baseUrl = "https://tenor.com/view/";
            String id = images[ThreadLocalRandom.current().nextInt(images.length)];
            return baseUrl + id;
        }

        String title = "GW Countdown: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff) + " | " + (turnEnd - turn) + " turns";
        StringBuilder response = new StringBuilder();

        String url = "https://www.epochconverter.com/countdown?q=" + TimeUtil.getTimeFromTurn(turnEnd);
        response.append(url);
        if (napLink != null && !napLink.isEmpty()) {
            response.append("\n" + napLink);
        }

        return "**" + title + "**\n" + response.toString();
    }
    @Command
    public String warRanking(@Me JSONObject command, @Me IMessageIO channel, @Timestamp long time, Set<NationOrAlliance> attackers, Set<NationOrAlliance> defenders,
                             @Switch("o") boolean onlyOffensives,
                             @Switch("d") boolean onlyDefensives,
                             @Switch("n") boolean normalizePerMember,
                             @Switch("i") boolean ignore2dInactives,
                             @Switch("a") boolean rankByNation,
                             @Switch("t") WarType warType,
                             @Switch("s") Set<WarStatus> statuses) {
        WarParser parser = WarParser.of(attackers, defenders, time, Long.MAX_VALUE);
        Map<Integer, DBWar> wars = parser.getWars();

        SummedMapRankBuilder<Integer, Double> ranksUnsorted = new RankBuilder<>(wars.values()).group(new BiConsumer<DBWar, GroupedRankBuilder<Integer, DBWar>>() {
            @Override
            public void accept(DBWar dbWar, GroupedRankBuilder<Integer, DBWar> builder) {
                if (warType != null && dbWar.warType != warType) return;
                if (statuses != null && !statuses.contains(dbWar.status)) return;
                if (!rankByNation) {
                    if (dbWar.attacker_aa != 0 && !onlyDefensives) builder.put(dbWar.attacker_aa, dbWar);
                    if (dbWar.defender_aa != 0 && !onlyOffensives) builder.put(dbWar.defender_aa, dbWar);
                } else {
                    if (!onlyDefensives) builder.put(dbWar.attacker_id, dbWar);
                    if (!onlyOffensives) builder.put(dbWar.defender_id, dbWar);
                }
            }
        }).sumValues(f -> 1d);
        if (normalizePerMember && !rankByNation) {
            ranksUnsorted = ranksUnsorted.adapt((aaId, numWars) -> {
                int num = DBAlliance.getOrCreate(aaId).getNations(true, ignore2dInactives ? 2440 : Integer.MAX_VALUE, true).size();
                if (num == 0) return 0d;
                return numWars / (double) num;
            });
        }

        RankBuilder<String> ranks = ranksUnsorted.sort().nameKeys(i -> PnwUtil.getName(i, !rankByNation));
        String offOrDef ="";
        if (onlyOffensives != onlyDefensives) {
            if (!onlyDefensives) offOrDef = "offensive ";
            else offOrDef = "defensive ";
        }

        String title = "Most " + offOrDef + "wars (" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - time) +")";
        if (normalizePerMember) title += "(per " + (ignore2dInactives ? "active " : "") + "nation)";

        ranks.build(channel, command, title, true);

        return null;
    }

    @Command(desc = "Calculate the costs of purchasing cities (from current to max)", aliases = {"citycost", "citycosts"})
    public String CityCost(@Range(min=1, max=100) int currentCity, @Range(min=1, max=100) int maxCity, @Default("false") boolean manifestDestiny, @Default("false") boolean urbanPlanning, @Default("false") boolean advancedUrbanPlanning, @Default("false") boolean metropolitanPlanning, @Default("false") boolean governmentSupportAgency) {
        if (maxCity > 1000) throw new IllegalArgumentException("Max cities 1000");

        double total = 0;

        for (int i = Math.max(1, currentCity); i < maxCity; i++) {
            total += PnwUtil.nextCityCost(i, manifestDestiny,
                    urbanPlanning && i >= Projects.URBAN_PLANNING.requiredCities(),
                    advancedUrbanPlanning && i >= Projects.ADVANCED_URBAN_PLANNING.requiredCities(),
                    metropolitanPlanning && i >= Projects.METROPOLITAN_PLANNING.requiredCities(),
                    governmentSupportAgency);
        }

        return "$" + MathMan.format(total);
    }

    @Command(desc = "Calculate the costs of purchasing infra (from current to max)", aliases = {"InfraCost", "infrastructurecost", "infra", "infrastructure", "infracosts"})
    public String InfraCost(@Range(min=0, max=40000) int currentInfra, @Range(min=0, max=40000) int maxInfra, @Default("false") boolean urbanization, @Default("false") boolean cce, @Default("false") boolean aec, @Default("false") boolean gsa, @Switch("c") @Default("1") int cities) {
        if (maxInfra > 40000) throw new IllegalArgumentException("Max infra 40000");

        double total = 0;

        total = PnwUtil.calculateInfra(currentInfra, maxInfra);

        double discountFactor = 1;
        if (urbanization) {
            discountFactor -= 0.05;
            if (gsa) {
                discountFactor -= 0.025;
            }
        }
        if (cce) discountFactor -= 0.05;
        if (aec) discountFactor -= 0.05;

        total = total * discountFactor * cities;

        return "$" + MathMan.format(total);
    }

    @Command(desc = "Calculate the costs of purchasing land (from current to max)", aliases = {"LandCost", "land", "landcosts"})
    public String LandCost(@Range(min=0, max=40000) int currentLand, @Range(min=0, max=40000) int maxLand, @Default("false") boolean rapidExpansion, @Default("false") boolean ala, @Default("false") boolean aec, @Default("false") boolean gsa, @Switch("c") @Default("1") int cities) {
        if (maxLand > 40000) throw new IllegalArgumentException("Max land 40000");

        double total = 0;

        total = PnwUtil.calculateLand(currentLand, maxLand);

        double discountFactor = 1;
        if (rapidExpansion) {
            discountFactor -= 0.05;
            if (gsa) discountFactor -= 0.025;
        }
        if (ala) discountFactor -= 0.05;
        if (aec) discountFactor -= 0.05;

        total = total * discountFactor * cities;

        return "$" + MathMan.format(total);
    }

    @Command(desc = "Calculate the score of various things. Each argument is option, and can go in any order")
    public String score(@Default DBNation nation,
                        @Switch("c") Integer cities,
                        @Switch("s") Integer soldiers,
                        @Switch("t") Integer tanks,
                        @Switch("a") Integer aircraft,
                        @Switch("b") Integer boats,
                        @Switch("m") Integer missiles,
                        @Switch("n") Integer nukes,
                        @Switch("p") Integer projects,
                        @Switch("i") Integer avg_infra,
                        @Switch("I") Integer infraTotal,
                        @Switch("b") String builtMMR
    ) {
        if (nation == null) {
            nation = new DBNation();
            nation.setMissiles(0);
            nation.setNukes(0);
            nation.setSoldiers(0);
            nation.setTanks(0);
            nation.setAircraft(0);
            nation.setShips(0);
        } else {
            nation = new DBNation(nation);
        }

        if (cities != null) nation.setCities(cities);
        if (soldiers != null) nation.setSoldiers(soldiers);
        if (tanks != null) nation.setTanks(tanks);

        if (aircraft != null) nation.setAircraft(aircraft);
        if (boats != null) nation.setShips(boats);
        if (missiles != null) nation.setMissiles(missiles);
        if (nukes != null) nation.setNukes(nukes);
        if (projects != null) {
            if (projects >= Projects.values.length) return "Too many projects: " + projects;
            nation.setProjectsRaw(0);
            for (int i = 0; i < projects; i++) {
                nation.setProject(Projects.values[i]);
            }
        }
        double infra = -1;
        if (avg_infra != null) {
            infra = avg_infra * nation.getCities();
        }
        if (infraTotal != null) {
            infra = infraTotal;
        }
        if (builtMMR != null) {
            nation.setMMR((builtMMR.charAt(0) - '0'), (builtMMR.charAt(1) - '0'), (builtMMR.charAt(2) - '0'), (builtMMR.charAt(3) - '0'));
        }
        double score = nation.estimateScore(infra == -1 ? nation.getInfra() : infra);

        if (score == 0) throw new IllegalArgumentException("No arguments provided");

        return "Score: " + MathMan.format(score) + "\n" +
                "WarRange: " + MathMan.format(score * 0.75) + " - " + MathMan.format(score * 1.75) + "\n" +
                "Can be Attacked By: " + MathMan.format(score / 1.75) + " - " + MathMan.format(score / 0.75) + "\n" +
                "Spy range: " + MathMan.format(score * 0.4) + " - " + MathMan.format(score * 1.5);


    }

    @Command(desc = "Check how many turns are left in the city/project timer", aliases = {"TurnTimer", "Timer", "CityTimer", "ProjectTimer"})
    public String TurnTimer(@Me GuildDB db, DBNation nation) throws IOException {
        StringBuilder response = new StringBuilder();
        response.append("City: " + nation.getCityTurns() + " turns (" + nation.getCities() + " cities)\n");
        response.append("Project: " + nation.getProjectTurns() + "turns | " +
                "(" + nation.getProjects().size() + "/" + nation.projectSlots() + " slots)\n");
        response.append("Color: " + nation.getColorTurns() + "turns \n");
        response.append("Domestic Policy: " + nation.getDomesticPolicyTurns() + "turns \n");
        response.append("War Policy: " + nation.getWarPolicyTurns() + "turns \n");
        response.append("Beige Turns: " + nation.getBeigeTurns() + "turns \n");
        response.append("Vacation: " + nation.getVm_turns());

        return response.toString();
    }

    @Command(desc = "Check how many projects slots a nation has", aliases = {"ProjectSlots", "ProjectSlot", "projects"})
    public String ProjectSlots(DBNation nation) {
        Set<Project> projects = nation.getProjects();
        Map<ResourceType, Double> value = new HashMap<>();
        for (Project project : projects) {
            value = PnwUtil.add(value, project.cost());
        }



        StringBuilder result = new StringBuilder();
        result.append(nation.getNation() + " has " + projects.size() + "/" + nation.projectSlots());
        for (Project project : projects) {
            result.append("\n - " + project.name());
        }
        result.append("\nworth: ~$" + MathMan.format(PnwUtil.convertedTotal(value)));
        result.append("\n`" + PnwUtil.resourcesToString(value) + "`");
        return result.toString();
    }

    @Command(desc = "Generate csv file of project costs")
    public String projectCostCsv() {
        StringBuilder response = new StringBuilder();
        response.append("PROJECT");
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            response.append("\t" + type);
        }
        response.append("marketValue");
        for (Project project : Projects.values) {
            response.append("\n");
            response.append(project.name());
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                Double rssValue = project.cost().getOrDefault(type, 0d);
                response.append("\t").append(MathMan.format(rssValue));
            }
            response.append("\t" + MathMan.format(PnwUtil.convertedTotal(project.cost())));
        }
        return response.toString();
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public static String loot(@Me DBNation me, NationOrAlliance nationOrAlliance, @Default Double nationScore, @Switch("p") boolean pirate) {
        double[] totalStored = null;
        double[] nationLoot = null;
        double[] allianceLoot = null;
        double[] revenue = null;
        int revenueTurns = 0;
        double revenueFactor = 0;
        double[] total = ResourceType.getBuffer();

        if (nationScore == null) nationScore = nationOrAlliance.isNation() ? nationOrAlliance.asNation().getScore() : me.getScore();
        DBAlliance alliance = nationOrAlliance.isAlliance() ? nationOrAlliance.asAlliance() : nationOrAlliance.asNation().getAlliance(false);

        List<String> extraInfo = new ArrayList<>();
        if (alliance != null) {
            LootEntry aaLootEntry = alliance.getLoot();
            if (aaLootEntry != null) {
                totalStored = aaLootEntry.getTotal_rss();
                Long date = aaLootEntry.getDate();
                extraInfo.add("Alliance Last looted: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - date));

                double aaScore = alliance.getScore();
                double ratio = ((nationScore * 10000) / aaScore) / 2d;
                double percent = Math.min(Math.min(ratio, 10000) / 30000, 0.33);
                allianceLoot = PnwUtil.multiply(totalStored.clone(), percent);
            } else {
                extraInfo.add("Alliance has not been looted yet");
            }
        }
        if (nationOrAlliance.isNation()) {
            revenueFactor = me.getWarPolicy() == WarPolicy.PIRATE || pirate ? 0.14 : 0.1;
            DBNation nation = nationOrAlliance.asNation();
            revenueFactor *= nation.lootModifier();

            LootEntry lootInfo = Locutus.imp().getNationDB().getLoot(nationOrAlliance.getId());
            if (lootInfo != null) {
                totalStored = lootInfo.getTotal_rss();
                nationLoot = PnwUtil.multiply(totalStored.clone(), revenueFactor);

                double originalValue = lootInfo.convertedTotal();
                double originalLootable = originalValue * revenueFactor;
                String type = lootInfo.getType().name();
                StringBuilder info = new StringBuilder();
                info.append("Nation Loot from " + type);
                info.append("(" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - lootInfo.getDate()) + " ago)");
                info.append(", worth: $" + MathMan.format(originalValue) + "($" + MathMan.format(originalLootable) + " lootable)");
                if (nationOrAlliance.asNation().getActive_m() > 1440) info.append(" - inactive for " + TimeUtil.secToTime(TimeUnit.MINUTES, nationOrAlliance.asNation().getActive_m()));
                extraInfo.add(info.toString());
            } else {
                extraInfo.add("No spy or beige loot found");
            }

            revenueTurns = nation.getTurnsInactive(lootInfo);
            if (revenueTurns > 0) {
                revenue = nation.getRevenue(revenueTurns + 24, true, true, false, true, false, false);
                if (lootInfo != null) {
                    revenue = PnwUtil.capManuFromRaws(revenue, lootInfo.getTotal_rss());
                }
                revenue = PnwUtil.multiply(revenue, revenueFactor);
            }

            if (nation.active_m() > 1440 && nation.active_m() < 20160) {
                if (nation.active_m() < 10080) {
                    Activity activity = nation.getActivity(14 * 12);
                    double loginChance = activity.loginChance(12, true);
                    double loginPct = (loginChance * 100);
                    extraInfo.add("Prior Week Login History (next 12 turns): " + MathMan.format(loginPct) + "%");
                }
                List<DBNation.LoginFactor> factors = DBNation.getLoginFactors(nation);

                long turnNow = TimeUtil.getTurn();
                int maxTurn = 30 * 12;
                int candidateTurnInactive = (int) (turnNow - TimeUtil.getTurn(nation.lastActiveMs()));

//                Set<DBNation> activeNations = Locutus.imp().getNationDB().getNationsMatching(f -> f.getVm_turns() == 0 && f.active_m() < 1440);
                Set<DBNation> nations1dInactive = Locutus.imp().getNationDB().getNationsMatching(f -> f.active_m() >= 1440 && f.getVm_turns() == 0 && f.active_m() <= TimeUnit.DAYS.toMinutes(30));
                NationScoreMap<DBNation> inactiveByTurn = new NationScoreMap<DBNation>(nations1dInactive, f -> {
                    return (double) (turnNow - TimeUtil.getTurn(f.lastActiveMs()));
                }, 1, 1);

                for (DBNation.LoginFactor factor : factors) {
                    Predicate<DBNation> matches = f -> factor.matches(factor.get(nation), factor.get(f));
                    BiFunction<Integer, Integer, Integer> sumFactor = inactiveByTurn.getSummedFunction(matches);

                    int numCandidateActivity = sumFactor.apply(Math.min(maxTurn - 23, candidateTurnInactive), Math.min(maxTurn, candidateTurnInactive + 24));
                    int numInactive = sumFactor.apply(14 * 12, 30 * 12) / (30 - 14);

                    System.out.println(factor.name + " | " + numCandidateActivity + " | " + numInactive);
                    System.out.println(":|| " + Math.min(maxTurn - 23, candidateTurnInactive) + " | " + Math.min(maxTurn, candidateTurnInactive + 24));
                    System.out.println(inactiveByTurn.getMinScore() + " | " + inactiveByTurn.getMaxScore());

                    double loginPct = Math.min(0.95, Math.max(0.05, numCandidateActivity > numInactive ? (1d - ((double) (numInactive) / (double) numCandidateActivity)) : 0)) * 100;

                    extraInfo.add("quit chance (" + factor.name + "=" + factor.toString(factor.get(nation)) + "): " + MathMan.format(100 - loginPct) + "%");
                }
            }

            // 100
            // 10
            // 90% will login

            // 15
            // 10
        }

        me.setMeta(NationMeta.INTERVIEW_LOOT, (byte) 1);

        if (nationLoot == null && allianceLoot == null) {
            return "No loot history";
        }

        StringBuilder response = new StringBuilder();

        response.append("Total Stored: ```" + PnwUtil.resourcesToString(totalStored) + "``` ");
        if (nationLoot != null) {
            response.append("Nation Loot (worth: $" + MathMan.format(PnwUtil.convertedTotal(nationLoot)) + "): ```" + PnwUtil.resourcesToString(nationLoot) + "``` ");
            PnwUtil.add(total, nationLoot);
        }
        if (allianceLoot != null) {
            response.append("Alliance Loot (worth: $" + MathMan.format(PnwUtil.convertedTotal(allianceLoot)) + "): ```" + PnwUtil.resourcesToString(allianceLoot) + "``` ");
            PnwUtil.add(total, allianceLoot);
        }
        if (revenue != null) {
            response.append("Revenue (" + revenueTurns + " turns @" + MathMan.format(revenueFactor) + "x, worth: $" + MathMan.format(PnwUtil.convertedTotal(revenue)) + ") ```" + PnwUtil.resourcesToString(revenue) + "``` ");
            PnwUtil.add(total, revenue);
        }
        response.append("Total Loot (worth: $" + MathMan.format(PnwUtil.convertedTotal(total)) + "): ```" + PnwUtil.resourcesToString(total) + "``` ");
        if (!extraInfo.isEmpty()) response.append("\n`notes:`\n` - " + StringMan.join(extraInfo, "`\n` - ") +"`");
        return response.toString();
    }

    @Command(desc = "Shows the cost of a project")
    public String ProjectCost(Project project, @Default("false") boolean technologicalAdvancement, @Default("false") boolean governmentSupportAgency) {
        Map<ResourceType, Double> cost = project.cost();
        if (technologicalAdvancement) {
            double factor = 0.05;
            if (governmentSupportAgency) {
                factor *= 1.5;
            }
            cost = PnwUtil.multiply(cost, 1 - factor);
        }
        return project.name() + ":\n```" + PnwUtil.resourcesToString(cost) + "```\nworth: ~$" + MathMan.format(PnwUtil.convertedTotal(cost));
    }

    @Command(desc = "Auto rank users on discord")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String autoroleall(@Me User author, @Me GuildDB db, @Me IMessageIO channel) {
        IAutoRoleTask task = db.getAutoRoleTask();
        task.syncDB();

        StringBuilder response = new StringBuilder();

        Function<Long, Boolean> func = new Function<Long, Boolean>() {
            @Override
            public Boolean apply(Long i) {
                task.autoRoleAll(new Consumer<String>() {
                    private boolean messaged = false;
                    @Override
                    public void accept(String s) {
                        if (messaged) return;
                        messaged = true;
                        channel.send(s);
                    }
                });
                return true;
            }
        };
        if (Roles.ADMIN.hasOnRoot(author) || true) {
            channel.send("Please wait...");
            func.apply(0L);
        } else if (Roles.INTERNAL_AFFAIRS.has(author, db.getGuild())) {
            String taskId = getClass().getSimpleName() + "." + db.getGuild().getId();
            Boolean result = TimeUtil.runTurnTask(taskId, func);
            if (result != Boolean.TRUE) return "Task already ran this turn";
        } else {
            return "No permission";
        }

        if (db.hasAlliance()) {
            for (Map.Entry<Member, GuildDB.UnmaskedReason> entry : db.getMaskedNonMembers().entrySet()) {
                User user = entry.getKey().getUser();
                response.append("`" + user.getName() + "#" + user.getDiscriminator() + "`" + "`<@" + user.getIdLong() + ">`");
                DBNation nation = DiscordUtil.getNation(user);
                if (nation != null) {
                    String active = TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m());
                    if (nation.getActive_m() > 10000) active = "**" + active + "**";
                    response.append(nation.getName() + " | <" + nation.getNationUrl() + "> | " + active + " | " + Rank.byId(nation.getPosition()) + " in AA:" + nation.getAllianceName());
                }
                response.append(" - ").append(entry.getValue());
                response.append("\n");
            }
        }

        if (db.getOrNull(GuildDB.Key.AUTOROLE) == null) {
            response.append("\n - AutoRole disabled. To enable it use: " + CM.settings.cmd.create(GuildDB.Key.AUTOROLE.name(), null, null, null).toSlashCommand() + "");
        }
        else response.append("\n - AutoRole Mode: ").append(db.getOrNull(GuildDB.Key.AUTOROLE) + "");
        if (db.getOrNull(GuildDB.Key.AUTONICK) == null) {
            response.append("\n - AutoNick disabled. To enable it use: " + CM.settings.cmd.create(GuildDB.Key.AUTONICK.name(), null, null, null).toSlashCommand() + "");
        }
        else response.append("\n - AutoNick Mode: ").append(db.getOrNull(GuildDB.Key.AUTONICK) + "");
        if (Roles.REGISTERED.toRole(db) == null) response.append("\n - Please set a registered role: " + CM.role.setAlias.cmd.create(Roles.REGISTERED.name(), "", null, null).toSlashCommand() + "");
        return response.toString();
    }

    @Command(desc = "Auto rank users on discord")
    public String autorole(@Me GuildDB db, @Me IMessageIO channel, Member member) {
        IAutoRoleTask task = db.getAutoRoleTask();
        task.syncDB();

        DBNation nation = DiscordUtil.getNation(member.getUser());
        Consumer<String> out = channel::send;
        if (nation == null) out.accept("That nation isn't registered: " + CM.register.cmd.toSlashMention() + "");
        task.autoRole(member, out);

        StringBuilder response = new StringBuilder("Done!");

        if (db.getOrNull(GuildDB.Key.AUTOROLE) == null) {
            response.append("\n - AutoRole disabled. To enable it use: " + CM.settings.cmd.create(GuildDB.Key.AUTOROLE.name(), null, null, null).toSlashCommand() + "");
        }
        else response.append("\n - AutoRole Mode: ").append((Object) db.getOrNull(GuildDB.Key.AUTOROLE));
        if (db.getOrNull(GuildDB.Key.AUTONICK) == null) {
            response.append("\n - AutoNick disabled. To enable it use: " + CM.settings.cmd.create(GuildDB.Key.AUTONICK.name(), null, null, null).toSlashCommand() + "");
        }
        else response.append("\n - AutoNick Mode: ").append((Object) db.getOrNull(GuildDB.Key.AUTONICK));
        if (Roles.REGISTERED.toRole(db) == null) response.append("\n - Please set a registered role: " + CM.role.setAlias.cmd.create(Roles.REGISTERED.name(), "", null, null).toSlashCommand() + "");
        return response.toString();
    }

    @RolePermission(value = {Roles.MILCOM, Roles.INTERNAL_AFFAIRS,Roles.ECON,Roles.FOREIGN_AFFAIRS}, any=true)
    @Command(desc = "Create an alliance sheet.\n" +
            "See `{prefix}placeholders Alliance` for a list of placeholders")
    @WhitelistPermission
    public String AllianceSheet(NationPlaceholders placeholders, ValueStore store, @Me Guild guild, @Me IMessageIO channel, @Me User author, @Me GuildDB db, Set<DBNation> nations, List<String> columns,
                                @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException, IllegalAccessException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.ALLIANCES_SHEET);
        }
        List<String> header = new ArrayList<>(columns);
        for (int i = 0; i < header.size(); i++) {
            String arg = header.get(i);
            arg = arg.replace("{", "").replace("}", "").replace("=", "");
            header.set(i, arg);
        }

        List<SAllianceContainer> allianceList = Locutus.imp().getPnwApi().getAlliances().getAlliances();

        Map<Integer, SAllianceContainer> alliances = allianceList.stream().collect(Collectors.toMap(f -> Integer.parseInt(f.getId()), f -> f));
        Map<Integer, List<DBNation>> nationMap = new RankBuilder<>(nations).group(n -> n.getAlliance_id()).get();

        Map<Integer, DBNation> totals = new HashMap<>();
        for (Map.Entry<Integer, List<DBNation>> entry : nationMap.entrySet()) {
            Integer id = entry.getKey();
            DBNation total = DBNation.createFromList(PnwUtil.getName(id, true), entry.getValue(), false);
            totals.put(id, total);
        }

        sheet.setHeader(header);

        for (Map.Entry<Integer, DBNation> entry : totals.entrySet()) {
            SAllianceContainer alliance = alliances.get(entry.getKey());
            if (alliance == null) continue;
            DBAlliance dbAlliance = DBAlliance.getOrCreate(entry.getKey());

            DBNation nation = entry.getValue();
            for (int i = 0; i < columns.size(); i++) {
                String arg = columns.get(i);

                // Format alliance id
                for (Field field : SAllianceContainer.class.getDeclaredFields()) {
                    String placeholder = "{" + field.getName() + "}";
                    if (arg.contains(placeholder)) {
                        field.setAccessible(true);
                        arg = arg.replace(placeholder, field.get(alliance) + "");
                    }
                }

                for (Field field : DBAlliance.class.getDeclaredFields()) {
                    String placeholder = "{" + field.getName() + "}";
                    if (arg.contains(placeholder)) {
                        field.setAccessible(true);
                        arg = arg.replace(placeholder, field.get(dbAlliance) + "");
                    }
                }

                String formatted = placeholders.format(store, arg);

                header.set(i, formatted);
            }

            sheet.addRow(new ArrayList<>(header));
        }

        sheet.clearAll();
        sheet.set(0, 0);

        sheet.attach(channel.create()).send();
        return null;
    }

    @RolePermission(value = {Roles.MILCOM, Roles.ECON, Roles.INTERNAL_AFFAIRS}, any=true)
    @Command
    public String NationSheet(ValueStore store, NationPlaceholders placeholders, @Me IMessageIO channel, @Me User author, @Me GuildDB db, Set<DBNation> nations, List<String> columns,
                              @Switch("e") boolean updateSpies, @Switch("s") SpreadSheet sheet,
                              @Switch("t") boolean updateTimer) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.NATION_SHEET);
        }
        List<String> header = new ArrayList<>(columns);
        for (int i = 0; i < header.size(); i++) {
            String arg = header.get(i);
            arg = arg.replace("{", "").replace("}", "").replace("=", "");
            header.set(i, arg);
        }

        sheet.setHeader(header);

        for (DBNation nation : nations) {
            if (updateSpies) {
                nation.updateSpies();
            }
            for (int i = 0; i < columns.size(); i++) {
                String arg = columns.get(i);
                String formatted = placeholders.format(store, arg);

                header.set(i, formatted);
            }

            sheet.addRow(new ArrayList<>(header));
        }

        sheet.clear("A:ZZ");
        sheet.set(0, 0);

        sheet.attach(channel.create()).send();
        return null;
    }

    @Command(desc = "Check if a nation shares networks with others")
    public String multi(@Me IMessageIO channel, DBNation nation) {
        MultiReport report = new MultiReport(nation.getNation_id());
        String result = report.toString();

        String title = PnwUtil.getName(nation.getNation_id(), false) + " multi report";
        if (result.length() + title.length() >= 2000) {
            String condensed = report.toString(true);
            if (condensed.length() + title.length() < 2000) {
                channel.create().embed(PnwUtil.getName(nation.getNation_id(), false), condensed).send();
            }
        }
        channel.create().embed(title, result).send();
        String disclaimer = "```Disclaimer:\n" +
                " - Sharing networks does not mean they are the same person (mobile networks, schools, public wifi, vpns, dynamic ips)\n" +
                " - A network not shared 'concurrently' or within a short timeframe may be a false positive\n" +
                " - Having many networks, but only a few shared may be a sign of a VPN being used (there are legitimate reasons for using a VPN)\n" +
                " - It is against game rules to use evidence to threaten or coerce others\n" +
                "See: https://politicsandwar.com/rules/" +
                "```";
        return disclaimer;
    }

    @Command(desc = "Return number of turns a nation has left of beige color bloc")
    public String beigeTurns(DBNation nation) {
        return nation.getBeigeTurns() + " turns";
    }

    @Command(desc = "Return quickest attacks to beige an enemy", aliases = {"fastBeige", "quickestBeige", "quickBeige", "fastestBeige"})
    public String quickestBeige(@Range(min=1, max=100) int resistance, @Switch("g") boolean noGround, @Switch("s") boolean noShip, @Switch("a") boolean noAir, @Switch("m") boolean noMissile, @Switch("n") boolean noNuke) {
        if (resistance > 1000 || resistance < 1) throw new IllegalArgumentException("Resistance must be between 1 and 100");
        List<AttackType> allowed = new ArrayList<>(List.of(AttackType.values));
        if (noGround) allowed.removeIf(f -> f == AttackType.GROUND);
        if (noShip) allowed.removeIf(f -> f.getUnits().length > 0 && f.getUnits()[0] == MilitaryUnit.SHIP);
        if (noAir) allowed.removeIf(f -> f.getUnits().length > 0 && f.getUnits()[0] == MilitaryUnit.AIRCRAFT);
        if (noMissile) allowed.removeIf(f -> f.getUnits().length > 0 && f.getUnits()[0] == MilitaryUnit.MISSILE);
        if (noNuke) allowed.removeIf(f -> f.getUnits().length > 0 && f.getUnits()[0] == MilitaryUnit.NUKE);
        AttackTypeNode best = AttackTypeNode.findQuickest(allowed, resistance);

        return "Result: " + best.toString() + " MAP: " + best.map + " resistance:" + best.resistance;
    }

    @Command(desc = "Get info about your own nation")
    public String me(@Me JSONObject command, @Me Guild guild, @Me IMessageIO channel, @Me DBNation me) throws IOException {
        return who(command, guild, channel, Collections.singleton(me), null, false, false, false, false, false, false, null);
    }

    @Command(aliases = {"who", "pnw-who", "who", "pw-who", "pw-info", "how", "where", "when", "why", "whois"},
            desc = "Get detailed information about a nation\n" +
                    "Nation argument can be nation name, id, link, or discord tag\n" +
                    "Use `-l` to list the nations instead of just providing a summary\n" +
                    "Use `-r` to list discord tag (raw)\n" +
                    "Use `-p` to list discord tag (ping)\n" +
                    "Use `-i` to list individual nation info\n" +
                    "Use `-c` to list individual nation channels" +
                    "e.g. `{prefix}who @borg`")
    public String who(@Me JSONObject command, @Me Guild guild, @Me IMessageIO channel,
                      Set<DBNation> nations,
                      @Default() NationPlaceholder sortBy,
                      @Switch("l") boolean list,
                      @Switch("a") boolean listAlliances,
                      @Switch("r") boolean listRawUserIds,
                      @Switch("m") boolean listMentions,
                      @Switch("i") boolean listInfo,
                      @Switch("c") boolean listChannels,
                      @Switch("p") Integer page) throws IOException {

        /*
        // TODO get commands that can run with
            // NationList
            // Set<NationOrAlliance>
            // Set<DBNation>
            // Set<NationOrAllianceOrGuild>
            // NationFilter
            //
            // for nation:
            // DBNation
            //
            // for alliance
            // DBAlliance
            // Set<DBAlliance>

            CommandManager2 commands = Locutus.imp().getCommandManager().getV2();
//            commands.getCommands()
public Map<ParametricCallable, String> getEndpoints() {

    }
         */

        int perpage = 15;
        StringBuilder response = new StringBuilder();

//        if (nationsOrAlliances.isEmpty()) {
//            return "Not found: `" + Settings.commandPrefix(false) + "who <user>`";
//        }
//        if (nationsOrAlliances.size() == 1) {
//            NationOrAlliance nationOrAlliance = nationsOrAlliances.iterator().next();
//            if (nationOrAlliance.isNation()) {
//                // nation card
//                // -
//
//            } else {
//                // alliance card
//                // - Tiering
//                // - Militarization
//                // - Active conflicts
//                // - Offshores
//                // - List of alliance commands
//
//            }
//        } else {
//
//        }


        String arg0;
        String title;
        if (nations.size() == 1) {
            DBNation nation = nations.iterator().next();
            title = nation.getNation();
            boolean showMoney = false;
            nation.toCard(channel, false, showMoney);

            List<CommandRef> commands = new ArrayList<>();
            commands.add(CM.nation.list.multi.cmd.create(nation.getNation_id() + ""));
            commands.add(CM.nation.revenue.cmd.create(nation.getNation_id() + "", null, null));
            commands.add(CM.war.info.cmd.create(nation.getNation_id() + ""));
            commands.add(CM.unit.history.cmd.create(nation.getNation_id() + "", null, null));
        } else {
            int allianceId = -1;
            for (DBNation nation : nations) {
                if (allianceId == -1 || allianceId == nation.getAlliance_id()) {
                    allianceId = nation.getAlliance_id();
                } else {
                    allianceId = -2;
                }
            }
            if (allianceId != -2) {
                String name = PnwUtil.getName(allianceId, true);
                String url = PnwUtil.getUrl(allianceId, true);
                title = "AA: " + name;
                arg0 = MarkupUtil.markdownUrl(name, url);
            } else {
                arg0 = "coalition";
                title = "`" + arg0 + "`";
            }
            if (nations.isEmpty()) return "No nations found";
            title = "(" + nations.size() + " nations) " + title;

            DBNation total = new DBNation(arg0, nations, false);
            DBNation average = new DBNation(arg0, nations, true);

            response.append("Total for " + arg0 + ":").append('\n');

            printAA(response, total, true);

            response.append("Average for " + arg0 + ":").append('\n');

            printAA(response, average, true);

            // min score
            // max score
            // Num members
            // averages
        }
        IMessageBuilder msg = channel.create();
        if (!listInfo && page == null && nations.size() > 1) {
            msg.embed(title, response.toString());
        }

        if (list || listMentions || listRawUserIds || listChannels || listAlliances) {
//            if (perpage == null) perpage = 15;
            if (page == null) page = 0;
            List<String> nationList = new ArrayList<>();

            if (listAlliances) {
                // alliances
                Set<Integer> alliances = new HashSet<>();
                for (DBNation nation : nations) {
                    if (!alliances.contains(nation.getAlliance_id())) {
                        alliances.add(nation.getAlliance_id());
                        nationList.add(nation.getAllianceUrlMarkup(true));
                    }
                }
            } else {
                GuildDB db = guild == null ? null : Locutus.imp().getGuildDB(guild);
                IACategory iaCat = listChannels && db != null ? db.getIACategory() : null;
                for (DBNation nation : nations) {
                    String nationStr = list ? nation.getNationUrlMarkup(true) : "";
                    if (listMentions) {
                        PNWUser user = nation.getDBUser();
                        if (user != null) {
                            nationStr += (" <@" + user.getDiscordId() + ">");
                        }
                    }
                    if (listRawUserIds) {
                        PNWUser user = nation.getDBUser();
                        if (user != null) {
                            nationStr += (" `<@" + user.getDiscordId() + ">`");
                        }
                    }
                    if (iaCat != null) {
                        IAChannel iaChan = iaCat.get(nation);
                        if (channel != null) {
                            if (listRawUserIds) {
                                nationStr += " `" + iaChan.getChannel().getAsMention() + "`";
                            } else {
                                nationStr += " " + iaChan.getChannel().getAsMention();
                            }
                        }
                    }
                    nationList.add(nationStr);
                }
            }
            int pages = (nations.size() + perpage - 1) / perpage;
            title += "(" + (page + 1) + "/" + pages + ")";

            msg.paginate(title, command, page, perpage, nationList).send();
        }
        if (listInfo) {
//            if (perpage == null) perpage = 5;
            perpage = 5;
            ArrayList<DBNation> sorted = new ArrayList<>(nations);

            if (sortBy != null) {
                Collections.sort(sorted, (o1, o2) -> Double.compare(((Number)sortBy.apply(o2)).doubleValue(), ((Number) sortBy.apply(o1)).doubleValue()));
            }

            List<String> results = new ArrayList<>();

            for (DBNation nation : sorted) {
                StringBuilder entry = new StringBuilder();
                entry.append("<" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + nation.getNation_id() + ">")
                        .append(" | " + String.format("%16s", nation.getNation()))
                        .append(" | " + String.format("%16s", nation.getAllianceName()))
                        .append("\n```")
//                            .append(String.format("%5s", (int) nation.getScore())).append(" ns").append(" | ")
                        .append(String.format("%2s", nation.getCities())).append(" \uD83C\uDFD9").append(" | ")
                        .append(String.format("%5s", nation.getAvg_infra())).append(" \uD83C\uDFD7").append(" | ")
                        .append(String.format("%6s", nation.getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                        .append(String.format("%5s", nation.getTanks())).append(" \u2699").append(" | ")
                        .append(String.format("%5s", nation.getAircraft())).append(" \u2708").append(" | ")
                        .append(String.format("%4s", nation.getShips())).append(" \u26F5").append(" | ")
                        .append(String.format("%1s", nation.getOff())).append(" \uD83D\uDDE1").append(" | ")
                        .append(String.format("%1s", nation.getDef())).append(" \uD83D\uDEE1").append(" | ")
                        .append(String.format("%2s", nation.getSpies())).append(" \uD83D\uDD0D").append(" | ");
//                Activity activity = nation.getActivity(14 * 12);
//                double loginChance = activity.loginChance((int) Math.max(1, (12 - (currentTurn % 12))), true);
//                int loginPct = (int) (loginChance * 100);
//                response.append("login=" + loginPct + "%").append(" | ");
                entry.append("```");

                results.add(entry.toString());
            }
            msg.paginate("Nations", command, page, perpage, results).send();
        }
        msg.send();

        return null;
    }

    private void printAA(StringBuilder response, DBNation nation, boolean spies) {
        response.append(String.format("%4s", TimeUtil.secToTime(TimeUnit.DAYS, nation.getAgeDays()))).append(" ");
        response.append(nation.toMarkdown(false, false, true, false, false));
        response.append(nation.toMarkdown(false, false, false, true, spies));
    }

    @Command
    @RolePermission(value = Roles.ECON)
    public String interest(@Me IMessageIO channel, @Me GuildDB db, Set<DBNation> nations, double interestPositivePercent, double interestNegativePercent) throws IOException, GeneralSecurityException {
        if (nations.isEmpty()) throw new IllegalArgumentException("No nations specified");
        if (nations.size() > 180) throw new IllegalArgumentException("Cannot do intest to that many people");

        interestPositivePercent /= 100d;
        interestNegativePercent /= 100d;

        Map<ResourceType, Double> total = new HashMap<>();
        Map<DBNation, Map<ResourceType, Double>> transfers = new HashMap<>();

        for (DBNation nation : nations) {
            Map<ResourceType, Double> deposits = PnwUtil.resourcesToMap(nation.getNetDeposits(db));
            deposits.remove(ResourceType.CREDITS);
            deposits = PnwUtil.normalize(deposits);

            Map<ResourceType, Double> toAdd = new LinkedHashMap<>();
            for (Map.Entry<ResourceType, Double> entry : deposits.entrySet()) {
                ResourceType type = entry.getKey();
                double amt = entry.getValue();

                double interest;
                if (amt > 1 && interestPositivePercent > 0) {
                    interest = interestPositivePercent * amt;
                } else if (amt < -1 && interestNegativePercent > 0) {
                    interest = interestNegativePercent * amt;
                } else continue;

                toAdd.put(type, interest);
            }
            total = PnwUtil.add(total, toAdd);

            if (toAdd.isEmpty()) continue;
            transfers.put(nation, toAdd);
        }

        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.TRANSFER_SHEET);
        List<String> header = new ArrayList<>(Arrays.asList("nation", "alliance", "cities"));
        for (ResourceType value : ResourceType.values) {
            if (value != ResourceType.CREDITS) {
                header.add(value.name());
            }
        }
        sheet.setHeader(header);

        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : transfers.entrySet()) {
            ArrayList<Object> row = new ArrayList<>();
            DBNation nation = entry.getKey();

            row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            row.add(MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getAllianceUrl()));
            row.add(nation.getCities());
            Map<ResourceType, Double> transfer = entry.getValue();
            for (ResourceType type : ResourceType.values) {
                if (type != ResourceType.CREDITS) {
                    double amt = transfer.getOrDefault(type, 0d);
                    row.add(amt);
                }
            }

            sheet.addRow(row);
        }
        sheet.clear("A:Z");
        sheet.set(0, 0);

        CM.deposits.addSheet cmd = CM.deposits.addSheet.cmd.create(sheet.getURL(), "#deposit", null, null);

        IMessageBuilder msg = channel.create();
        StringBuilder result = new StringBuilder();
        sheet.attach(msg, result, false, 0);

        result.append("Total: `" + PnwUtil.resourcesToString(total) + "`" +
                "\nWorth: $" + MathMan.format(PnwUtil.convertedTotal(total)));
        result.append("\n\nUse " + CM.transfer.bulk.cmd.toSlashMention());
        result.append("\nOr press \uD83C\uDFE6 to run " + cmd.toSlashCommand() + "");

        String title = "Nation Interest";
        String emoji = "Confirm";

        msg.embed(title, result.toString())
                .commandButton(cmd, emoji)
                .send();

        return null;
    }

    @Command(aliases = {"dnr", "caniraid"})
    public String dnr(@Me GuildDB db, DBNation nation) {
        Integer dnrTopX = db.getOrNull(GuildDB.Key.DO_NOT_RAID_TOP_X);
        Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);
        Set<Integer> canRaid = db.getCoalition(Coalition.CAN_RAID);
        Set<Integer> canRaidInactive = db.getCoalition(Coalition.CAN_RAID_INACTIVE);

        String title;
        Boolean dnr = db.getCanRaid().apply(nation);
        if (!dnr) {
            title = ("do NOT raid " + nation.getNation());
        }  else if (nation.getPosition() > 1 && nation.getActive_m() < 10000) {
            title = ("You CAN raid " + nation.getNation() + " (however they are an active member of an alliance), see also: " + CM.alliance.stats.counterStats.cmd.toSlashMention() + "");
        } else if (nation.getPosition() > 1) {
            title =  "You CAN raid " + nation.getNation() + " (however they are a member of an alliance), see also: " + CM.alliance.stats.counterStats.cmd.toSlashMention() + "";
        } else if (nation.getAlliance_id() != 0) {
            title =  "You CAN raid " + nation.getNation() + ", see also: " + CM.alliance.stats.counterStats.cmd.toSlashMention() + "";
        } else {
            title =  "You CAN raid " + nation.getNation();
        }
        StringBuilder response = new StringBuilder();
        response.append("**" + title + "**");
        if (dnrTopX != null) {
            response.append("\n\n> Do Not Raid Guidelines:");
            response.append("\n>  - Avoid members of the top " + dnrTopX + " alliances and members of direct allies (check ingame treaties)");

            Set<String> enemiesStr = enemies.stream().map(f -> Locutus.imp().getNationDB().getAllianceName(f)).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<String> canRaidStr = canRaid.stream().map(f -> Locutus.imp().getNationDB().getAllianceName(f)).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<String> canRaidInactiveStr = canRaidInactive.stream().map(f -> Locutus.imp().getNationDB().getAllianceName(f)).filter(Objects::nonNull).collect(Collectors.toSet());

            if (!enemiesStr.isEmpty()) {
                response.append("\n>  - Enemies: " + StringMan.getString(enemiesStr));
            }
            if (!canRaidStr.isEmpty()) {
                response.append("\n>  - You CAN raid: " + StringMan.getString(canRaidStr));
            }
            if (!canRaidInactiveStr.isEmpty()) {
                response.append("\n>  - You CAN raid inactives (1w) of: " + StringMan.getString(canRaidInactiveStr));
            }
        }
        return response.toString();
    }

    @Command(aliases = {"setloot"})
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setLoot(@Me IMessageIO channel, @Me DBNation me, DBNation nation, Map<ResourceType, Double> resources, @Default("ESPIONAGE") NationLootType type, @Default("1") double fraction) {
        resources = PnwUtil.multiply(resources, 1d / fraction);
        Locutus.imp().getNationDB().saveLoot(nation.getNation_id(), TimeUtil.getTurn(), PnwUtil.resourcesToArray(resources), type);
        return "Set " + nation.getNation() + " to " + PnwUtil.resourcesToString(resources) + " worth: ~$" + PnwUtil.convertedTotal(resources);
    }

    @Command(desc = "List alliances by their new members over a timeframe")
    public String recruitmentRankings(@Me User author, @Me IMessageIO channel, @Me JSONObject command, @Timestamp long cutoff, @Default("80") int topX, @Switch("u") boolean uploadFile) {
        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances(true, true, true, topX);

        Map<DBAlliance, Integer> rankings = new HashMap<DBAlliance, Integer>();

        for (DBAlliance alliance : alliances) {
            Set<Integer> applied = new HashSet<>();
            Set<DBNation> potentialMembers = new HashSet<>();

            List<Map.Entry<Long, Map.Entry<Integer, Rank>>> rankChanges = alliance.getRankChanges();
            for (Map.Entry<Long, Map.Entry<Integer, Rank>> change : rankChanges) {
                Map.Entry<Integer, Rank> natRank = change.getValue();
                int nationId = natRank.getKey();
                boolean alreadyApplied = (!applied.add(nationId));
                if (alreadyApplied) continue;

                long date = change.getKey();
                if (date < cutoff) continue;

                DBNation nation = DBNation.byId(nationId);
                if (nation == null || nation.getPosition() <= 1) continue;

                Rank rank = natRank.getValue();

                if (rank == Rank.APPLICANT || rank == Rank.MEMBER) {
                    potentialMembers.add(nation);
                }
            }

            int total =0;
            for (DBNation nation : potentialMembers) {
                Map.Entry<Integer, Rank> position = nation.getAlliancePosition(cutoff);
                if (position.getKey() == alliance.getAlliance_id() && position.getValue().id >= Rank.MEMBER.id) continue;
                total++;
            }

//            int total = potentialMembers.size();
            rankings.put(alliance, total);
        }
        new SummedMapRankBuilder<>(rankings).sort().nameKeys(f -> f.getName()).build(author, channel, command, "Most new members", uploadFile);
        return null;
    }

    @Command()
    public String unitCost(Map<MilitaryUnit, Long> units, @Default Boolean wartime) {
        if (wartime == null) wartime = false;
        StringBuilder response = new StringBuilder();

        response.append("**Units " + StringMan.getString(units) + "**:\n");
        double[] cost = ResourceType.getBuffer();
        double[] upkeep = ResourceType.getBuffer();

        for (Map.Entry<MilitaryUnit, Long> entry : units.entrySet()) {
            MilitaryUnit unit = entry.getKey();
            Long amt = entry.getValue();

            double[] unitCost = unit.getCost(amt.intValue()).clone();
            double[] unitUpkeep = unit.getUpkeep(wartime).clone();

            unitUpkeep = PnwUtil.multiply(unitUpkeep, amt);

            PnwUtil.add(cost, unitCost);
            PnwUtil.add(upkeep, unitUpkeep);
        }
        response.append("Cost:\n```" + PnwUtil.resourcesToString(cost) + "``` ");
        if (PnwUtil.resourcesToMap(cost).size() > 1) {
            response.append("Worth: ~$" + MathMan.format(PnwUtil.convertedTotal(cost))).append("\n");
        }
        response.append("\nUpkeep:\n```" + PnwUtil.resourcesToString(upkeep) + "``` ");
        if (PnwUtil.resourcesToMap(upkeep).size() > 1) {
            response.append("Worth: ~$" + MathMan.format(PnwUtil.convertedTotal(upkeep))).append("\n");
        }

        return response.toString();
    }

    @Command(aliases = {"alliancecost", "aacost"})
    public String allianceCost(@Me IMessageIO channel, Set<DBNation> nations, @Switch("u") boolean update) {
        double infraCost = 0;
        double landCost = 0;
        double cityCost = 0;
        Map<ResourceType, Double> projectCost = new HashMap<>();
        Map<ResourceType, Double> militaryCost = new HashMap<>();
        Map<ResourceType, Double> buildingCost = new HashMap<>();
        for (DBNation nation : nations) {
            Set<Project> projects = nation.getProjects();
            for (Project project : projects) {
                projectCost = PnwUtil.addResourcesToA(projectCost, project.cost());
            }
            for (MilitaryUnit unit : MilitaryUnit.values) {
                int units = nation.getUnits(unit);
                militaryCost = PnwUtil.addResourcesToA(militaryCost, PnwUtil.resourcesToMap(unit.getCost(units)));
            }
            int cities = nation.getCities();
            for (int i = 1; i <= cities; i++) {
                boolean manifest = true;
                boolean cp = i > Projects.URBAN_PLANNING.requiredCities() && projects.contains(Projects.URBAN_PLANNING);
                boolean acp = i > Projects.ADVANCED_URBAN_PLANNING.requiredCities() && projects.contains(Projects.ADVANCED_URBAN_PLANNING);
                boolean mp = i > Projects.METROPOLITAN_PLANNING.requiredCities() && projects.contains(Projects.METROPOLITAN_PLANNING);
                boolean gsa = projects.contains(Projects.GOVERNMENT_SUPPORT_AGENCY);
                cityCost += PnwUtil.nextCityCost(i, manifest, cp, acp, mp, gsa);
            }
            Map<Integer, JavaCity> cityMap = nation.getCityMap(update, false);
            for (Map.Entry<Integer, JavaCity> cityEntry : cityMap.entrySet()) {
                JavaCity city = cityEntry.getValue();
                {
                    double landFactor = 1;
                    double infraFactor = 0.95;
                    if (projects.contains(Projects.ARABLE_LAND_AGENCY)) landFactor *= 0.95;
                    if (projects.contains(Projects.CENTER_FOR_CIVIL_ENGINEERING)) infraFactor *= 0.95;
                    landCost += PnwUtil.calculateLand(250, city.getLand()) * landFactor;
                    infraCost += PnwUtil.calculateInfra(10, city.getInfra()) * infraFactor;
                }
                city = cityEntry.getValue();
                JavaCity empty = new JavaCity();
                empty.setLand(city.getLand());
                empty.setInfra(city.getInfra());
                double[] myBuildingCost = city.calculateCost(empty);
                buildingCost = PnwUtil.addResourcesToA(buildingCost, PnwUtil.resourcesToMap(myBuildingCost));
            }
        }

        Map<ResourceType, Double> total = new HashMap<>();
        total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + infraCost);
        total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + landCost);
        total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + cityCost);
        total = PnwUtil.add(total, projectCost);
        total = PnwUtil.add(total, militaryCost);
        total = PnwUtil.add(total, buildingCost);
        double totalConverted = PnwUtil.convertedTotal(total);
        String title = nations.size() + " nations worth ~$" + MathMan.format(totalConverted);

        StringBuilder response = new StringBuilder();
        response.append("**Infra**: $" + MathMan.format(infraCost));
        response.append("\n").append("**Land**: $" + MathMan.format(landCost));
        response.append("\n").append("**Cities**: $" + MathMan.format(cityCost));
        response.append("\n").append("**Projects**: $" + MathMan.format(PnwUtil.convertedTotal(projectCost)) + "\n`" + PnwUtil.resourcesToString(projectCost) + "`");
        response.append("\n").append("**Military**: $" + MathMan.format(PnwUtil.convertedTotal(militaryCost)) + "\n`" + PnwUtil.resourcesToString(militaryCost) + "`");
        response.append("\n").append("**Buildings**: $" + MathMan.format(PnwUtil.convertedTotal(buildingCost)) + "\n`" + PnwUtil.resourcesToString(buildingCost) + "`");
        response.append("\n").append("**Total**: $" + MathMan.format(PnwUtil.convertedTotal(total)) + "\n`" + PnwUtil.resourcesToString(total) + "`");

        channel.create().embed(title, response.toString()).send();
        return null;
    }


}