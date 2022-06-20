package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.alliance.*;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.task.GetMemberResources;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.locutus.discord.apiv1.domains.AllianceMembers;
import link.locutus.discord.apiv1.domains.subdomains.AllianceBankContainer;
import link.locutus.discord.apiv1.domains.subdomains.AllianceMembersContainer;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class DBAlliance implements NationList, NationOrAlliance {
    private final int allianceId;
    private String name;
    private String acronym;
    private String flag;
    private String forum_link;
    private String discord_link;
    private String wiki_link;
    private long dateCreated;
    private NationColor color;

    private volatile long lastUpdated = 0;

    public DBAlliance(com.politicsandwar.graphql.model.Alliance alliance) {
        this.allianceId = alliance.getId();
        set(alliance, null);
    }

    public DBAlliance(DBAlliance other) {
        this(other.allianceId,
                other.name,
                other.acronym,
                other.flag,
                other.forum_link,
                other.discord_link,
                other.wiki_link,
                other.dateCreated,
                other.color);
    }

    @Command
    public String getAcronym() {
        return acronym;
    }

    @Command
    public String getFlag() {
        return flag;
    }

    @Command
    public String getForum_link() {
        return forum_link;
    }

    @Command
    public String getDiscord_link() {
        return discord_link;
    }

    @Command
    public String getWiki_link() {
        return wiki_link;
    }

    @Command
    public long getDateCreated() {
        return dateCreated;
    }

    @Command
    public NationColor getColor() {
        return color;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public boolean set(com.politicsandwar.graphql.model.Alliance alliance, Consumer<Event> eventConsumer) {
        lastUpdated = System.currentTimeMillis();

        boolean dirty = false;

        DBAlliance copy = null;
        if (alliance.getDate() != null && this.dateCreated != alliance.getDate().toEpochMilli()) {
            dirty = true;
            // creation date should never change
            this.dateCreated = alliance.getDate().toEpochMilli();
        }
        if (alliance.getName() != null && !alliance.getName().equals(name)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.name = alliance.getName();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeNameEvent(copy, this));
        }
        if (alliance.getAcronym() != null && !alliance.getAcronym().equals(acronym)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.acronym = alliance.getAcronym();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeAcronymEvent(copy, this));
        }
        if (alliance.getColor() != null) {
            NationColor newColor = NationColor.valueOf(alliance.getColor().toUpperCase(Locale.ROOT));
            if (newColor != this.color) {
                dirty = true;
                if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
                this.color = newColor;
                if (eventConsumer != null) eventConsumer.accept(new AllianceChangeColorEvent(copy, this));
            }
        }
        if (alliance.getFlag() != null && !alliance.getFlag().equals(flag)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.flag = alliance.getFlag();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeFlagEvent(copy, this));
        }
        if (alliance.getForum_link() != null && !alliance.getForum_link().equals(forum_link)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.forum_link = alliance.getForum_link();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeForumLinkEvent(copy, this));
        }
        if (alliance.getDiscord_link() != null && !alliance.getDiscord_link().equals(discord_link)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.discord_link = alliance.getDiscord_link();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeDiscordLinkEvent(copy, this));
        }
        if (alliance.getWiki_link() != null && !alliance.getWiki_link().equals(wiki_link)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.wiki_link = alliance.getWiki_link();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeWikiLinkEvent(copy, this));
        }
        return dirty;
    }

    public DBAlliance(int allianceId, String name, String acronym, String flag, String forum_link, String discord_link, String wiki_link, long dateCreated, NationColor color) {
        this.allianceId = allianceId;
        this.dateCreated = dateCreated;
        this.name = name;
        this.acronym = acronym;
        this.color = color;
        this.flag = flag;
        this.forum_link = forum_link;
        this.discord_link = discord_link;
        this.wiki_link = wiki_link;
    }

    @Command(desc = "Number of offensive and defensive wars since date")
    public int getNumWarsSince(long date) {
        return Locutus.imp().getWarDb().countWarsByAlliance(allianceId, date);
    }


    public static Set<DBAlliance> getTopX(int topX, boolean checkTreaty) {
        Set<DBAlliance> results = new LinkedHashSet<>();
        Map<Integer, Double> aas = new RankBuilder<>(Locutus.imp().getNationDB().getNations().values()).group(DBNation::getAlliance_id).sumValues(DBNation::getScore).sort().get();
        for (Map.Entry<Integer, Double> entry : aas.entrySet()) {
            if (entry.getKey() == 0) continue;
            if (topX-- <= 0) break;
            int allianceId = entry.getKey();
            results.add(new DBAlliance(allianceId));
            if (checkTreaty) {
                Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(allianceId);
                for (Map.Entry<Integer, Treaty> aaTreatyEntry : treaties.entrySet()) {
                    switch (aaTreatyEntry.getValue().type) {
                        case MDP:
                        case MDOAP:
                        case PROTECTORATE:
                            results.add(new DBAlliance(aaTreatyEntry.getKey()));
                    }
                }
            }
        }
        return results;
    }

    public String getMarkdownUrl() {
        return PnwUtil.getMarkdownUrl(allianceId, true);
    }

    @Override
    @Command
    public int getId() {
        return allianceId;
    }

    @Override
    public boolean isAlliance() {
        return true;
    }

    @Command
    public String getName() {
        return PnwUtil.getName(allianceId, true);
    }

    @Override
    public String toString() {
        return getName();
    }

    public List<DBNation> getNations(boolean removeVM, int removeInactiveM, boolean removeApps) {
        List<DBNation> nations = getNations();
        if (removeVM) nations.removeIf(f -> f.getVm_turns() != 0);
        if (removeInactiveM > 0) nations.removeIf(f -> f.getActive_m() > removeInactiveM);
        if (removeApps) nations.removeIf(f -> f.getPosition() <= 1);
        return nations;
    }

    private List<DBNation> getNationsCache = null;

    public List<DBNation> getNations() {
        if (getNationsCache == null) {
            getNationsCache = Locutus.imp().getNationDB().getNations(Collections.singleton(allianceId));
        }
        return new LinkedList<>(getNationsCache);
    }

    public DBNation getMembersTotal() {
        DBNation result = new DBNation(getName(), getNations(true, 0, true), false);
        result.setAlliance(result.getNation());
        result.setAlliance_id(allianceId);
        return result;
    }

    public DBNation getMembersAverage() {
        DBNation result = new DBNation(getName(), getNations(true, 0, true), true);
        result.setAlliance(result.getNation());
        result.setAlliance_id(allianceId);
        return result;
    }

    public boolean updateSpies(AllianceMembers members) {
        Set<DBNation> toUpdate = new LinkedHashSet<>();
        for (AllianceMembersContainer member : members.getNations()) {
            Integer spies = Integer.parseInt(member.getSpies());
            DBNation nation = Locutus.imp().getNationDB().getNation(member.getNationId());
            if (nation != null && !spies.equals(nation.getSpies())) {
                nation.setSpies(spies);
                Locutus.imp().getNationDB().setSpies(nation.getNation_id(), spies);
                toUpdate.add(nation);
            }
        }
        Locutus.imp().getNationDB().addNations(toUpdate);
        return true;
    }

    public boolean updateSpies(boolean updateManually) {
        GuildDB db = getGuildDB();
        if (db != null && db.isValidAlliance() && db.getOrNull(GuildDB.Key.API_KEY) != null) {
            PoliticsAndWarV2 api = db.getApi();
            if (api != null) {
                try {
                    api.getAllianceMembers(allianceId);
                    return true;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        if (!updateManually) return false;
        for (DBNation nation : getNations(true, 1440, true)) {
            nation.updateSpies();
        }
        return true;
    }

    public DBNation getTotal() {
        DBNation result = new DBNation(getName(), getNations(), false);
        result.setAlliance(result.getNation());
        result.setAlliance_id(allianceId);
        return result;
    }

    public Set<DBAlliance> getTreatiedAllies() {
        Set<DBAlliance> allies = new HashSet<>();
        for (Map.Entry<Integer, Treaty> treatyEntry : getDefenseTreaties().entrySet()) {
            Treaty treaty = treatyEntry.getValue();
            int other = treaty.from == allianceId ? treaty.to : treaty.from;
            allies.add(new DBAlliance(other));
        }
        return allies;
    }

    public Map<Integer, Treaty> getDefenseTreaties() {
        HashMap<Integer, Treaty> defTreaties = new HashMap<>(getTreaties());
        defTreaties.entrySet().removeIf(f -> f.getValue().type == TreatyType.NAP || f.getValue().type == TreatyType.PIAT);
        return defTreaties;
    }

    public Map<Integer, Treaty> getTreaties() {
        if (this.treaties == null) {
            this.treaties = Locutus.imp().getNationDB().getTreaties(allianceId);
        }
        return this.treaties;
    }

    public Set<DBAlliance> getSphere() {
        return getSphereCached(new HashMap<>());
    }

    public Set<DBAlliance> getSphereCached(Map<Integer, DBAlliance> aaCache) {
        return getTreaties(this, new HashMap<>(), aaCache);
    }

    public List<DBAlliance> getSphereRankedCached(Map<Integer, DBAlliance> aaCache) {
        ArrayList<DBAlliance> list = new ArrayList<>(getSphereCached(aaCache));
        Collections.sort(list, (o1, o2) -> Double.compare(o2.getScore(), o1.getScore()));
        return list;
    }

    public List<DBAlliance> getSphereRanked() {
        return getSphereRankedCached( new HashMap<>());
    }

    double scoreCached = -1;

    @Override
    @Command
    public double getScore() {
        if (scoreCached == -1) {
            scoreCached = new SimpleNationList(getNations(true, 0, true)).getScore();
        }
        return scoreCached;
    }

    private Integer rank;

    @Command(desc = "Rank by score")
    public int getRank() {
        if (rank == null) {
            Map<Integer, List<DBNation>> byScore = Locutus.imp().getNationDB().getNationsByAlliance(true, true, true, true);
            rank = 0;
            for (Map.Entry<Integer, List<DBNation>> entry : byScore.entrySet()) {
                rank++;
                if (entry.getKey() == allianceId) return rank;
            }
            return rank = Integer.MAX_VALUE;
        }
        return rank;
    }

    @Command
    public int getAlliance_id() {
        return allianceId;
    }

    private static Set<DBAlliance> getTreaties(DBAlliance currentAA, Map<DBAlliance, Double> currentWeb, Map<Integer, DBAlliance> aaCache) {
        if (!currentWeb.containsKey(currentAA)) currentWeb.put(currentAA, currentAA.getScore());
        aaCache.put(currentAA.allianceId, currentAA);

        Map<Integer, Treaty> treaties = currentAA.getTreaties();

        DBAlliance protector = null;
        double protectorScore = 0;
        for (Map.Entry<Integer, Treaty> entry : treaties.entrySet()) {
            int otherId = entry.getKey();
            DBAlliance otherAA = aaCache.computeIfAbsent(otherId, f -> new DBAlliance(otherId));
            if (currentWeb.containsKey(otherAA)) continue;
            if (otherAA.getAlliance_id() == 4150 || currentAA.allianceId == 4150) continue;
//            if (otherAA.getAlliance_id() == 770 || currentAA.allianceId == 770) continue;
            switch (entry.getValue().type) {
                case NAP:
                    if (otherAA.getAlliance_id() != 3339 && currentAA.getAlliance_id() != 3339) continue;
                case MDP:
                    if (otherAA.getAlliance_id() == 3669 || currentAA.getAlliance_id() == 3669) continue;
                case MDOAP:
                    if (protector != null) continue;
                    getTreaties(otherAA, currentWeb, aaCache);
                    continue;
                case PROTECTORATE:
                    double score = otherAA.getScore();
                    double currentScore = 0;
                    for (double value : currentWeb.values()) currentScore = Math.max(currentScore, value);
                    if (score > currentScore && score > protectorScore) {
                        protectorScore = score;
                        protector = otherAA;
                    }
            }
        }
        if (protector != null) {
            return getTreaties(protector, new HashMap<>(), aaCache);
        }

        return new HashSet<>(currentWeb.keySet());
    }

    public DBNation getAverage() {
        DBNation result = new DBNation(getName(), getNations(), true);
        result.setAlliance(result.getNation());
        result.setAlliance_id(allianceId);
        return result;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) {
            if (o instanceof Number) {
                return ((Number) o).intValue() == allianceId;
            }
            return false;
        }

        DBAlliance alliance = (DBAlliance) o;

        return allianceId == alliance.allianceId;
    }

    @Override
    public int hashCode() {
        return allianceId;
    }

    public String getUrl() {
        return "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + getAlliance_id();
    }

    public boolean exists() {
        return Locutus.imp().getNationDB().getAllianceName(allianceId) != null;
    }

//    public boolean addBalance(GuildDB db, Map<ResourceType, Double> transfer, String note) {
//        synchronized (db) {
//            Map<ResourceType, Map<String, Double>> offset = db.getDepositOffset(-allianceId);
//
//            boolean result = false;
//            for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
//                ResourceType rss = entry.getKey();
//                Double amt = entry.getValue();
//                if (amt == 0) continue;
//
//                double currentAmt = offset.getOrDefault(rss, new HashMap<>()).getOrDefault(note, 0d);
//                double newAmount = amt + currentAmt;
//
//                db.setDepositOffset(-allianceId, rss, newAmount, note);
//
//                result = true;
//            }
//
//            return result;
//        }
//    }

    public GuildDB getGuildDB() {
        return Locutus.imp().getGuildDBByAA(allianceId);
    }

    public List<Map.Entry<Long, Map.Entry<Integer, Rank>>> getRankChanges() {
        return Locutus.imp().getNationDB().getRankChanges(allianceId);
    }

    public Map<Integer, Map.Entry<Long, Rank>> getRemoves() {
        return Locutus.imp().getNationDB().getRemovesByAlliance(allianceId);
    }

    public List<DBWar> getActiveWars() {
        return Locutus.imp().getWarDb().getActiveWars(Collections.singleton(allianceId), WarStatus.ACTIVE);
    }

    public void setMeta(AllianceMeta key, byte... value) {
        Locutus.imp().getNationDB().setMeta(-allianceId, key.ordinal(), value);
    }

    public ByteBuffer getMeta(AllianceMeta key) {
        byte[] result = Locutus.imp().getNationDB().getMeta(-allianceId, key.ordinal());
        return result == null ? null : ByteBuffer.wrap(result);
    }

    public void setMeta(AllianceMeta key, byte value) {
        setMeta(key, new byte[] {value});
    }

    public void setMeta(AllianceMeta key, int value) {
        setMeta(key, ByteBuffer.allocate(4).putInt(value).array());
    }

    public void setMeta(AllianceMeta key, long value) {
        setMeta(key, ByteBuffer.allocate(8).putLong(value).array());
    }

    public void setMeta(AllianceMeta key, double value) {
        setMeta(key, ByteBuffer.allocate(8).putDouble(value).array());
    }

    public void setMeta(AllianceMeta key, String value) {
        setMeta(key, value.getBytes(StandardCharsets.ISO_8859_1));
    }

    public Map<DBNation, Map<ResourceType, Double>> getMemberStockpile() throws IOException {
        Map<Integer, Map<ResourceType, Double>> stockpile = new GetMemberResources(allianceId).call();
        Map<DBNation, Map<ResourceType, Double>> result = new HashMap<>();
        for (Map.Entry<Integer, Map<ResourceType, Double>> entry : stockpile.entrySet()) {
            DBNation nation = DBNation.byId(entry.getKey());
            if (nation == null) continue;
            result.put(nation, entry.getValue());
        }
        return result;
    }

    public Map<ResourceType, Double> getStockpile() throws IOException {
        Map<ResourceType, Double> totals = new LinkedHashMap<>();

        PoliticsAndWarV2 api = Locutus.imp().getApi(allianceId);
        AllianceBankContainer bank = api.getBank(allianceId).getAllianceBanks().get(0);
        String json = new Gson().toJson(bank);
        JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
        for (ResourceType type : ResourceType.values) {
            JsonElement amt = obj.get(type.name().toLowerCase());
            if (amt != null) {
                totals.put(type, amt.getAsDouble());
            }
        }
        return totals;
    }

    public void updateCities() throws IOException, ParseException {
        Locutus.imp().getPnwApi().getV3_legacy().updateNations(allianceId);
    }
}
