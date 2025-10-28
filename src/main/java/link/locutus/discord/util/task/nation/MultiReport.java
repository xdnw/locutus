package link.locutus.discord.util.task.nation;

import com.google.api.client.util.Lists;
import com.politicsandwar.graphql.model.BBGame;
import com.ptsmods.mysqlw.query.QueryCondition;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.entities.PwUid;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.DBBan;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.task.multi.GetUid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MultiReport {
    private final int nationId;
    private final Map<Integer, Set<PwUid>>  uuidByNation = new LinkedHashMap<>();
    private final Map<Integer, Map<PwUid, Long>>  diffMap;
    private final Map<Integer, Set<Map.Entry<DBWar, DBWar>>> illegalWarsByMulti;
    private final Map<Integer, Set<DBTrade>> illegalTradesByMulti;

    private final Map<Integer, Long> challengeEarnings = new LinkedHashMap<>();
    private final Map<Integer, Integer> challengeGames = new LinkedHashMap<>();
    private final Map<Integer, Set<Transaction2>> illegalTransfers = new HashMap<>();
    private final Map<Integer, Set<Map.Entry<Transaction2, Transaction2>>> illegalIndirectTransfers = new HashMap<>();

    private final Map<Integer, List<DBBan>> bansByNation = new HashMap<>();

    @Override
    public String toString() {
        return toString(false);
    }
    public String toString(boolean simple) {
        StringBuilder response = new StringBuilder();

        if (diffMap.isEmpty()) return "No multis founds. Networks: " + uuidByNation.getOrDefault(nationId, Collections.emptySet()).size();

        response.append("**Possible multis for: **" + PW.getMarkdownUrl(nationId, false)).append("\n");

        Set<PwUid> myUUIDS = uuidByNation.get(nationId);

        response.append("verified: ").append(Locutus.imp().getDiscordDB().isVerified(nationId)).append("\n");
        response.append("networks total: ").append(myUUIDS.size()).append('\n');

        for (Map.Entry<Integer, Map<PwUid, Long>> entry : diffMap.entrySet()) {
            int nationId = entry.getKey();
            DBNation nation = Locutus.imp().getNationDB().getNationById(nationId);

            String name;
            if (nation == null) name = nationId + "";
            else name = nation.getNationUrlMarkup() + " | " + nation.getAllianceUrlMarkup();

            response.append("**Possible multis for: **" + PW.getMarkdownUrl(nationId, false)).append("\n");

            Set<PwUid> multiUUIDS = uuidByNation.get(nationId);
            Set<PwUid> shared = new HashSet<>(multiUUIDS);
            shared.retainAll(myUUIDS);

            if (simple) {
                Map<PwUid, Long> diff = entry.getValue();
                int concurrent = 0;
                for (Map.Entry<PwUid, Long> timeDiff : diff.entrySet()) {
                    if (timeDiff.getValue() <= TimeUnit.DAYS.toMillis(1)) concurrent++;
                }
                response.append("shared networks: " + shared.size() + "/" + multiUUIDS.size())
                        .append(" ( " + concurrent + " same day)")
                        .append("\n");
            } else {
                response.append("shared networks: " + shared.size() + "/" + multiUUIDS.size()).append("\n");
                Map<PwUid, Long> diff = entry.getValue();
                for (Map.Entry<PwUid, Long> timeDiff : diff.entrySet()) {
                    String timeStr;
                    long uuidTimeDiff = timeDiff.getValue();
                    if (uuidTimeDiff == 0) timeStr = "concurrent";
                    else if (uuidTimeDiff >= Integer.MAX_VALUE || uuidTimeDiff < 0) timeStr = "unknown";
                    else timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, uuidTimeDiff);
                    response.append("- ").append(timeDiff.getKey()).append(": " + timeStr + "\n");
                }
            }

            Set<Map.Entry<DBWar, DBWar>> wars = illegalWarsByMulti.get(nationId);
            if (wars != null && !wars.isEmpty()) {
                List<String> simpleList = new ArrayList<>();
                for (Map.Entry<DBWar, DBWar> warEntry : wars) {
                    String key = warEntry.getKey().warId + "";
                    String value = warEntry.getValue().warId + "";
                    if (warEntry.getKey().getAttacker_id() == this.nationId || warEntry.getKey().getDefender_id() == this.nationId) {
                        key = "**" + key + "**";
                    }
                    if (warEntry.getValue().getAttacker_id() == this.nationId || warEntry.getValue().getDefender_id() == this.nationId) {
                        value = "**" + value + "**";
                    }
                    simpleList.add("(" + key + "," + value + ")");
                }
                response.append("shared wars: " + (simple ? "" : "\n"));
                response.append(StringMan.getString(simpleList)).append("\n");
                if (!simple) response.append('\n');
            }

            List<DBBan> bans = bansByNation.get(nationId);
            if (bans != null && !bans.isEmpty()) {
                for (DBBan ban : bans) {
                    if (simple) {
                        response.append(" BAN: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, ban.getTimeRemaining()) + "\n");
                    } else {
                        response.append("ban: " + ban.reason + " | " +
                                "<@" + ban.discord_id + "> | " +
                                "remaining:" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, ban.getTimeRemaining()) + "\n");
                    }
                }
            }

            Set<DBTrade> trades = illegalTradesByMulti.get(nationId);
            if (trades != null && !trades.isEmpty()) {
                List<Integer> tradeIds = new ArrayList<>();
                double worth = 0;
                for (DBTrade offer : trades) {
                    int per = offer.getPpu();
                    ResourceType type = offer.getResource();
                    if (per <= 1 || (per > 10000 || (type == ResourceType.FOOD && per > 1000)) || type == ResourceType.CREDITS) {
                        tradeIds.add(offer.getTradeId());
                        worth += Math.abs(ResourceType.convertedTotal(offer.getResource(), offer.getQuantity()));
                    }
                }
                if (!tradeIds.isEmpty()) {
                    Collections.sort(tradeIds);
                    response.append("\nshared trades:\n").append(StringMan.getString(tradeIds))
                            .append(" worth: $" + MathMan.format(worth))
                            .append("\n");
                    if (!simple) response.append('\n');
                }
            }

            Set<Transaction2> transfers = illegalTransfers.get(nationId);

            if (transfers != null && !transfers.isEmpty()) {
                if (simple) {
                    double worth = 0;
                    for (Transaction2 transfer : transfers) {
                        worth += transfer.convertedTotal();
                    }
                    response.append("shared transfers: " + transfers.size() + " worth: $" + MathMan.format(worth)).append("\n");
                } else {
                    response.append("shared transfers:\n- ").append(StringMan.join(transfers.stream().map(Transaction2::toSimpleString).collect(Collectors.toList()), "\n- ")).append("\n\n");
                }
            }

            Set<Map.Entry<Transaction2, Transaction2>> proxyTransfers = illegalIndirectTransfers.get(nationId);
            if (proxyTransfers != null && !proxyTransfers.isEmpty()) {
                if (simple) {
                    double worth = 0;
                    for (Map.Entry<Transaction2, Transaction2> pair : proxyTransfers) {
                        worth += pair.getValue().convertedTotal();
                    }
                    response.append("3rd party transfers: " + proxyTransfers.size() + " worth: $" + MathMan.format(worth)).append("\n");
                } else {
                    List<String> msg = proxyTransfers.stream().map(e -> e.getKey().toSimpleString() + " -> " + e.getValue().toSimpleString()).collect(Collectors.toList());
                    response.append("\n3rd party transfers:\n- ").append(StringMan.join(msg, "\n- ")).append("\n\n");
                }
            }
            {
                Long earnings = challengeEarnings.get(nationId);
                Integer numGames = challengeGames.get(nationId);

                if (earnings != null || numGames != null) {
                    response.append("BB Wagers:");
                    if (simple) {
                        if (numGames != null) response.append(" " + MathMan.format(numGames));
                        if (earnings != null) response.append(" net $" + MathMan.format(earnings));
                    } else {
                        response.append("\n");
                        if (earnings != null) response.append("- Net: $" + MathMan.format(earnings)).append("\n");
                        if (numGames != null)
                            response.append("- # Wagered Games: " + MathMan.format(numGames)).append("\n");
                    }
                    response.append("\n");
                }
            }
        }

        return response.toString();
    }

    public MultiReport(int nationId) {
        this.nationId = nationId;
        DBNation nation = Locutus.imp().getNationDB().getNationById(nationId);
        try {
            if (nation != null) {
                new GetUid(nation, true).call();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Map<PwUid, List<Map.Entry<Long, Long>>> uuidsByTime = Locutus.imp().getDiscordDB().getUuids(nationId);

        uuidByNation.put(nationId, uuidsByTime.keySet());

        Set<Integer> multis = new IntOpenHashSet();
        for (PwUid uuid : uuidsByTime.keySet()) {
            Set<Integer> currMultis = Locutus.imp().getDiscordDB().getMultis(uuid);
            multis.addAll(currMultis);
        }
        multis.remove(nationId);

        diffMap = new HashMap<>();

        Map<Long, List<DBBan>> userIdsChecked = new HashMap<>();
        PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(nationId);
        if (user != null) {
            List<DBBan> bans = Locutus.imp().getNationDB().getBansForUser(user.getDiscordId());
            if (!bans.isEmpty()) {
                bansByNation.put(nationId, bans);
            }
            userIdsChecked.put(user.getDiscordId(), bans);
        } else {
            List<DBBan> bans = Locutus.imp().getNationDB().getBansForNation(nationId);
            if (!bans.isEmpty()) {
                bansByNation.put(nationId, bans);
            }
        }

        for (Integer multi : multis) {
            Map<PwUid, List<Map.Entry<Long, Long>>> multiUuids = Locutus.imp().getDiscordDB().getUuids(multi);

            uuidByNation.put(multi, multiUuids.keySet());

            Map<PwUid, Long> myDiffMap = new LinkedHashMap<>();
            for (Map.Entry<PwUid, List<Map.Entry<Long, Long>>> entry : uuidsByTime.entrySet()) {
                PwUid uuid = entry.getKey();
                List<Map.Entry<Long, Long>> multiTime = multiUuids.get(uuid);
                if (multiTime == null) continue;
                List<Map.Entry<Long, Long>> myTime = entry.getValue();

                long diff = Long.MAX_VALUE;
                outer:
                for (Map.Entry<Long, Long> a : multiTime) {
                    for (Map.Entry<Long, Long> b : myTime) {
                        if ((a.getKey() == 0 && a.getValue() != Long.MAX_VALUE) || (b.getKey() == 0 && b.getValue() != Long.MAX_VALUE)) continue;
                        if (a.getKey() <= b.getValue() && a.getValue() >= b.getKey()) {
                            diff = 0;
                            break outer;
                        } else if (a.getValue() < b.getKey()) {
                            diff = Math.min(diff, Math.abs(a.getValue() - b.getKey()));
                        } else if (a.getKey() > b.getValue()) {
                            diff = Math.min(diff, Math.abs(a.getKey() - b.getValue()));
                        } else {
                            diff = Math.min(diff, Math.abs(a.getValue() - b.getKey()));
                            diff = Math.min(diff, Math.abs(a.getKey() - b.getValue()));
                        }
                    }
                }
                myDiffMap.put(uuid, diff);
            }
            if (!myDiffMap.isEmpty()) {
                diffMap.put(multi, myDiffMap);
            }
            PNWUser otherUser = Locutus.imp().getDiscordDB().getUserFromNationId(multi);
            if (otherUser != null && !userIdsChecked.containsKey(otherUser.getDiscordId())) {
                List<DBBan> bans = Locutus.imp().getNationDB().getBansForUser(otherUser.getDiscordId());
                if (!bans.isEmpty()) {
                    bansByNation.put(multi, bans);
                }
                userIdsChecked.put(otherUser.getDiscordId(), bans);
            } else {
                if (otherUser != null) {
                    bansByNation.put(multi, userIdsChecked.get(otherUser.getDiscordId()));
                }
                List<DBBan> bans = Locutus.imp().getNationDB().getBansForNation(multi);
                bansByNation.computeIfAbsent(multi, i -> new ArrayList<>()).addAll(bans);
            }
        }

        this.illegalWarsByMulti = new HashMap<>();
        {
            Map<Integer, List<DBWar>> wars = getDefenders(nationId);
            Set<Integer> sharedWars = new IntOpenHashSet();

            for (int otherNationId : multis) {
                Map<Integer, List<DBWar>> otherWars = getDefenders(otherNationId);
                for (Map.Entry<Integer, List<DBWar>> entry : otherWars.entrySet()) {
                    if (wars.containsKey(entry.getKey())) {
                        sharedWars.add(entry.getKey());
                    }
                    wars.computeIfAbsent(entry.getKey(), i -> Lists.newArrayList()).addAll(entry.getValue());
                }
            }

            for (int defender : sharedWars) {
                List<DBWar> checkWars = wars.get(defender);
                for (int i = 0; i < checkWars.size(); i++) {
                    for (int j = i + 1; j < checkWars.size(); j++) {
                        DBWar a = checkWars.get(i);
                        DBWar b = checkWars.get(j);

                        int multiId1 = a.getAttacker_id() == defender ? a.getDefender_id() : a.getAttacker_id();
                        int multiId2 = b.getAttacker_id() == defender ? b.getDefender_id() : b.getAttacker_id();
                        if (multiId1 == multiId2) continue;
                        if (a.getAttacker_id() != multiId1 && b.getAttacker_id() != multiId2) continue;

                        if (TimeUnit.MILLISECONDS.toDays(Math.abs(a.getDate() - b.getDate())) > 6) {
                            continue;
                        }

                        illegalWarsByMulti.computeIfAbsent(multiId1, f -> new ObjectLinkedOpenHashSet<>()).add(new KeyValue<>(a, b));
                        illegalWarsByMulti.computeIfAbsent(multiId2, f -> new ObjectLinkedOpenHashSet<>()).add(new KeyValue<>(b, a));
                    }
                }
            }
        }
        illegalWarsByMulti.remove(nationId);


        this.illegalTradesByMulti = new LinkedHashMap<>();
        List<DBTrade> myTrades = Locutus.imp().getTradeManager().getTradeDb().getTrades(nationId, 0);
        for (DBTrade offer : myTrades) {
            if ((offer.getSeller() == (nationId) || multis.contains(offer.getSeller())) && (offer.getBuyer() == (nationId) || multis.contains(offer.getBuyer()))) {
                int multiId = (offer.getSeller() == (nationId)) ? offer.getBuyer() : offer.getSeller();
                illegalTradesByMulti.computeIfAbsent(multiId, f -> new ObjectLinkedOpenHashSet<>()).add(offer);
            }
        }

        {
          boolean sentBank = false;
            List<Transaction2> transfers = trimTransfers(Locutus.imp().getBankDB().getNationTransfers(nationId, 0));
            for (Transaction2 transfer : transfers) {
                if (transfer.banker_nation == nationId) {
                    if (multis.contains((int) transfer.getReceiver())) {
                        sentBank = true;
                        illegalTransfers.computeIfAbsent((int) transfer.getReceiver(), f -> new HashSet<>()).add(transfer);
                    }
                } else {
                    if (multis.contains(transfer.banker_nation)) {
                        sentBank = true;
                        illegalTransfers.computeIfAbsent(transfer.banker_nation, f -> new HashSet<>()).add(transfer);
                    }
                }
            }

            if (sentBank) {
//                body.append("**Illegal Bank transfers**:\n - " + StringMan.join(illegalTransfers, "\n - ")).append("\n\n");
            }

            Map<Long, List<Transaction2>> sharedTransfers = toShared(transfers);
            Set<Long> sharedKeys = new LongOpenHashSet();
            for (int otherId : multis) {
                Map<Long, List<Transaction2>> multiTransfers = toShared(trimTransfers(Locutus.imp().getBankDB().getNationTransfers(otherId, 0)));
                for (Map.Entry<Long, List<Transaction2>> entry : multiTransfers.entrySet()) {
                    if (sharedTransfers.containsKey(entry.getKey())) {
                        sharedKeys.add(entry.getKey());
                    }
                    sharedTransfers.computeIfAbsent(entry.getKey(), i -> Lists.newArrayList()).addAll(entry.getValue());
                }
            }

//            Set<String> illegalThirdPartyTransfers = new HashSet<>();

            for (long key : sharedKeys) {
                List<Transaction2> list = sharedTransfers.get(key);
                for (int i = 0; i < list.size(); i++) {
                    for (int j = i + 1; j < list.size(); j++) {
                        Transaction2 a = list.get(i);
                        Transaction2 b = list.get(j);

                        if (a.receiver_type == b.receiver_type) continue;

                        long diff = Math.abs(a.getDate() - b.getDate());
                        if (TimeUnit.MILLISECONDS.toDays(diff) > 2) continue;

                        int nationA = (int) (a.receiver_type == 2 ? a.getSender() : a.getReceiver());
                        int nationB = (int) (b.receiver_type == 2 ? b.getSender() : b.getReceiver());

                        if (nationA == nationB) continue;

                        Map.Entry<Transaction2, Transaction2> entry = new KeyValue<>(a, b);
                        illegalIndirectTransfers.computeIfAbsent(nationA, f -> new ObjectLinkedOpenHashSet<>()).add(entry);
                        illegalIndirectTransfers.computeIfAbsent(nationB, f -> new ObjectLinkedOpenHashSet<>()).add(entry);
                    }
                }
            }
        }

        {
            calculateBBGames();
        }
    }

    private void calculateBBGames() {
        List<BBGame> games = getChallengeGames(nationId);
        for (BBGame game : games) {
            challengeGames.merge(game.getHome_nation_id(), 1, Integer::sum);
            challengeGames.merge(game.getAway_nation_id(), 1, Integer::sum);
            if (game.getHome_score() > game.getAway_score()) {
                challengeEarnings.merge(game.getHome_nation_id(), game.getWager().longValue(), Long::sum);
                challengeEarnings.merge(game.getAway_nation_id(), -game.getWager().longValue(), Long::sum);
            } else if (game.getAway_score() > game.getHome_score()) {
                challengeEarnings.merge(game.getAway_nation_id(), game.getWager().longValue(), Long::sum);
                challengeEarnings.merge(game.getHome_nation_id(), -game.getWager().longValue(), Long::sum);
            }
        }
    }

    private List<Transaction2> trimTransfers(List<Transaction2> transfers) {
        transfers.removeIf(t -> t.note != null && t.note.toLowerCase().contains("of the alliance bank inventory"));
        return transfers;
    }

    /**
     * Maps a list of  wars to the opponent's nation id
     */
    private Map<Integer, List<DBWar>> getDefenders(int nationId) {
        Set<DBWar> myWars = Locutus.imp().getWarDb().getWarsByNation(nationId);
        Map<Integer, List<DBWar>> defenders = new HashMap<>();
        for (DBWar war : myWars) {
            int otherId = war.getDefender_id() == nationId ? war.getAttacker_id() : war.getDefender_id();
            defenders.computeIfAbsent(otherId, i -> Lists.newArrayList()).add(war);
        }
        return defenders;
    }

    public Map<Long, List<Transaction2>> toShared(List<Transaction2> transfers) {
        HashMap<Long, List<Transaction2>> map = new HashMap<>();
        for (Transaction2 transfer : transfers) {
            long key = Arrays.hashCode(transfer.resources);
            map.computeIfAbsent(key, i -> Lists.newArrayList()).add(transfer);
        }
        return map;
    }

    private Map<Integer, List<Map.Entry<Integer, Transaction2>>> getWarTransfers(int nationId) {
        Map<Integer, List<Map.Entry<Integer, Transaction2>>> warTransfers = new HashMap<>();

        List<Transaction2> transfers = Locutus.imp().getBankDB().getNationTransfers(nationId, 0);
        for (Transaction2 transfer : transfers) {
            if (transfer.note == null) continue;

            String[] split = transfer.note.split(" defeated ");
            if (split.length != 2) continue;
            String name1 = split[0];
            String name2 = split[1].split("'s nation and captured")[0];
            try {
                DBNation nation1 = Locutus.imp().getNationDB().getNationByLeader(name1);
                DBNation nation2 = Locutus.imp().getNationDB().getNationByLeader(name2);
                if (nation1 == null || nation2 == null) {
                    continue;
                }
                int otherId = nation1.getNation_id() == nationId ? nation2.getNation_id() : nation1.getNation_id();
                warTransfers.computeIfAbsent(otherId, i -> Lists.newArrayList()).add(new KeyValue<>(nationId, transfer));
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return warTransfers;
    }

    public List<BBGame> getChallengeGames(int nationid) {
        return Locutus.imp().getBaseballDB().getBaseballGames(new Consumer<SelectBuilder>() {
            @Override
            public void accept(SelectBuilder f) {
                f.where(QueryCondition.greater("wager", 0).and(QueryCondition.equals("home_nation_id", nationid).or(QueryCondition.equals("away_nation_id", nationid))));
            }
        });
    }

    public static long getTimeDiff(List<Map.Entry<Long, PwUid>> mine, List<Map.Entry<Long, PwUid>> other) {
        long end = Long.MAX_VALUE;
        for (Map.Entry<Long, PwUid> myEntry : mine) {
            long start = myEntry.getKey();
            PwUid myUUID = myEntry.getValue();

            long otherEnd = Long.MAX_VALUE;
            for (Map.Entry<Long, PwUid> otherEntry : other) {
                long otherStart = otherEntry.getKey();
                PwUid otherUUID = otherEntry.getValue();

            }
        }
        return 0l;


    }
}
