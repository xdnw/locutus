package link.locutus.discord.commands.info;

import com.google.api.client.util.Sets;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.builder.GroupedRankBuilder;
import link.locutus.discord.commands.manager.v2.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.math.BigInteger;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class ListMultisByAlliance extends Command {
    public ListMultisByAlliance() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.list.multis.cmd);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        NationDB nationDB = Locutus.imp().getNationDB();

        String[] lines = Objects.requireNonNull(FileUtil.readFile("/debug/nonreferrals.txt")).split("\\r?\\n");
        Map<Integer, DBNation> referredNations = new Int2ObjectOpenHashMap<>(Locutus.imp().getNationDB().getNationsById());
        Map<String, DBNation> nationsByName = new HashMap<>();
        for (Map.Entry<Integer, DBNation> entry : referredNations.entrySet()) {
            nationsByName.putIfAbsent(entry.getValue().getNation(), entry.getValue());
        }
        int oldest = 0;
        for (String line : lines) {
            String[] columns = line.split("\t");
            DBNation other = nationsByName.get(columns[1]);
            if (other != null) {
                oldest = other.getNation_id();
                referredNations.remove(other.getNation_id());
            }
        }
        int finalOldest = oldest;
        referredNations.entrySet().removeIf(e -> e.getValue().getNation_id() > finalOldest);

        Map<BigInteger, Set<Integer>> multis = Locutus.imp().getDiscordDB().getUuidMap();
        Map<Integer, Set<Integer>> multisByNation = new Int2ObjectOpenHashMap<>();
        Set<Integer> verified = Locutus.imp().getDiscordDB().getVerified();

        for (Map.Entry<BigInteger, Set<Integer>> entry : multis.entrySet()) {
            for (Integer nationId : entry.getValue()) {
                if (verified.contains(nationId)) continue;

                Set<Integer> others = new IntOpenHashSet(entry.getValue());
                others.remove(nationId);
                others.removeAll(verified);
                multisByNation.computeIfAbsent(nationId, t -> Sets.newHashSet()).addAll(others);
            }
        }

        Map<Integer, DBWar> allWars = Locutus.imp().getWarDb().getWarsForNationOrAlliance(multisByNation::containsKey, null, null);
        List<DBTrade> allOffers = Locutus.imp().getTradeManager().getTradeDb().getTrades(0);
        Map<Integer, List<DBTrade>> offersByNation = new RankBuilder<>(allOffers).group((BiConsumer<DBTrade, GroupedRankBuilder<Integer, DBTrade>>) (offer, builder) -> {
            builder.put(offer.getBuyer(), offer);
            builder.put(offer.getSeller(), offer);
        }).get();

        Map<Integer, List<Transaction2>> allTransfers = Locutus.imp().getBankDB().getNationTransfersByNation(0, Long.MAX_VALUE, multisByNation.keySet());

        StringBuilder response = new StringBuilder();
        int i = 0;

        for (Map.Entry<Integer, Set<Integer>> entry : multisByNation.entrySet()) {
            Integer nationId = entry.getKey();
            DBNation nation = nationDB.getNationById(nationId);
            if (nation == null || entry.getValue().isEmpty()) continue;
            if (verified.contains(nationId)) continue;

            boolean sharedWars = false;

            Set<Integer> defenders = getDefenders(nationId, allWars);
            for (int otherNationId : entry.getValue()) {
                Set<Integer> otherDefenders = getDefenders(otherNationId, allWars);
                if (otherDefenders.removeAll(defenders)) {
                    sharedWars = true;
                }
            }

            boolean sharedTrades = false;

            List<DBTrade> myTrades = offersByNation.getOrDefault(nationId, Collections.emptyList());
            for (DBTrade offer : myTrades) {
                sharedTrades |= (offer.getSeller() == (nationId) || entry.getValue().contains(offer.getSeller())) && (offer.getBuyer() == (nationId) || entry.getValue().contains(offer.getBuyer()));
            }

            boolean sentBank = false;
            List<Transaction2> transfers = allTransfers.getOrDefault(nationId, Collections.emptyList());
            for (Transaction2 transfer : transfers) {
                if (transfer.banker_nation == nationId) {
                    if (entry.getValue().contains((int) transfer.getReceiver())) {
                        sentBank = true;
                    }
                } else {
                    if (entry.getValue().contains(transfer.banker_nation)) {
                        sentBank = true;
                    }
                }
            }

            String nationUrl = url(nationId);

            String allianceName = nationDB.getAllianceName(nation.getAlliance_id());
            String allianceUrl = "=HYPERLINK(\"" + Settings.PNW_URL() + "/alliance/id=%s\",\"%s\")";
            allianceUrl = String.format(allianceUrl, nation.getAlliance_id(), allianceName);

            String multiStr = StringMan.join(entry.getValue().stream().map(this::url).collect(Collectors.toList()), "\t");

            response.append(nationUrl).append("\t").append(allianceUrl).append("\t").append(referredNations.containsKey(nationId)).append("\t").append(sharedWars).append("\t").append(sharedTrades).append("\t").append(sentBank).append("\t").append(sentBank || sharedTrades || sharedWars).append("\t").append(nation.getAgeDays()).append("\t").append(nation.getPosition()).append("\t").append(multiStr).append("\n");
        }

        channel.create().embed("Potential multis:", response.toString()).send();

        return null;
    }

    private Set<Integer> getDefenders(int nationId, Map<Integer, DBWar> allWars) {
        List<DBWar> myWars = allWars.values().stream().filter(f -> f.getAttacker_id() == nationId || f.getDefender_id() == nationId).toList();
        Set<Integer> defenders = new IntOpenHashSet();
        for (DBWar war : myWars) {
            if (war.getDefender_id() != nationId) {
                defenders.add(war.getDefender_id());
            }
        }
        return defenders;
    }

    private String url(int nationId) {
        String name = PW.getName(nationId, false);
        String nationUrl = "=HYPERLINK(\"" + Settings.PNW_URL() + "/nation/id=%s\",\"%s\")";
        return String.format(nationUrl, nationId, name);
    }
}
