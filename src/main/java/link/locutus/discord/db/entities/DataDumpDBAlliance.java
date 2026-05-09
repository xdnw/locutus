package link.locutus.discord.db.entities;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.shrink.IShrink;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.event.Event;
import link.locutus.discord.sim.combat.WarOutcomeMath;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DataDumpDBAlliance extends DBAlliance {
    private final AllianceSnapshotContext context;
    private final Double snapshotScore;
    private volatile LootEntry historicalLoot;
    private volatile boolean historicalLootLoaded;

    public DataDumpDBAlliance(int allianceId, String name, String acronym, String flag, long dateCreated,
            NationColor color, AllianceSnapshotContext context, Double snapshotScore) {
        super(allianceId, name, acronym, flag, dateCreated, color);
        this.context = context;
        this.snapshotScore = snapshotScore;
    }

    public DataDumpDBAlliance(DBAlliance other, AllianceSnapshotContext context, Double snapshotScore) {
        this(other.getAlliance_id(), other.getName(), other.getAcronym(), other.getFlag(), other.getDateCreated(),
                other.getColor(), context,
                snapshotScore != null ? snapshotScore
                        : (other instanceof DataDumpDBAlliance data ? data.getSnapshotScore() : null));
    }

    public Long getSnapshotDate() {
        return context == null ? null : context.getSnapshotDate();
    }

    public Double getSnapshotScore() {
        return snapshotScore;
    }

    @Override
    protected DBAlliance copyForChangeTracking() {
        throw unsupportedSnapshot("change tracking copies");
    }

    @Override
    public boolean set(com.politicsandwar.graphql.model.Alliance alliance, Consumer<Event> eventConsumer) {
        throw unsupportedSnapshot("live alliance mutation");
    }

    @Override
    public void setLoot(LootEntry lootEntry) {
        throw unsupportedSnapshot("loot mutation");
    }

    @Override
    public void markTreasuresDirty() {
        throw unsupportedSnapshot("treasure mutation");
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public Set<DBNation> getNations() {
        if (context == null || getAlliance_id() == 0) {
            return Collections.emptySet();
        }
        return context.getNationsByAlliance(getAlliance_id());
    }

    @Override
    public double getScore(NationFilter filter) {
        if (filter == null && snapshotScore != null) {
            return snapshotScore;
        }
        return super.getScore(filter);
    }

    private Double getHistoricalMetric(AllianceMetric metric, long turn) {
        Map<AllianceMetric, Map<Long, Double>> byMetric = Locutus.imp().getNationDB()
            .getAllianceMetrics(Collections.singleton(getAlliance_id()), metric, turn)
            .get(getAlliance_id());
        if (byMetric == null) {
            return null;
        }
        Map<Long, Double> values = byMetric.get(metric);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.values().iterator().next();
    }

    @Override
    public int getNumTreasures() {
        Long snapshotDate = getSnapshotDate();
        if (getAlliance_id() == 0 || snapshotDate == null) {
            return 0;
        }
        Double metric = getHistoricalMetric(AllianceMetric.TREASURE, TimeUtil.getTurn(snapshotDate));
        return metric == null ? 0 : Math.max(0, (int) Math.round(metric));
    }

    @Override
    public double getMetricAt(ValueStore store, AllianceMetric metric, Long date) {
        Long snapshotDate = getSnapshotDate();
        Long effectiveDate = date;
        if (snapshotDate != null) {
            effectiveDate = effectiveDate == null ? snapshotDate : Math.min(effectiveDate, snapshotDate);
        }
        if (effectiveDate != null) {
            Double stored = getHistoricalMetric(metric, TimeUtil.getTurn(effectiveDate));
            if (stored != null) {
                return stored;
            }
            return metric.apply(this);
        }
        return super.getMetricAt(store, metric, null);
    }

    @Override
    public int getRank(NationFilter filter) {
        if (context == null) {
            throw unsupportedSnapshot("rank lookups");
        }
        return context.rankFor(this, filter);
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public synchronized Map<Integer, TaxBracket> getTaxBrackets(long cacheFor) {
        Map<Integer, TaxBracket> result = new Int2ObjectOpenHashMap<>();
        for (DBNation nation : getNations()) {
            int taxId = nation.getTax_id();
            if (taxId == 0) {
                continue;
            }
            TaxBracket bracket = nation.getTaxBracket();
            if (bracket != null) {
                result.put(taxId, bracket);
            } else {
                result.putIfAbsent(taxId, new TaxBracket(taxId, getAlliance_id(), "#" + taxId, -1, -1, 0L));
            }
        }
        return result;
    }

    @Override
    public Map<Integer, Treaty> getTreaties(Predicate<TreatyType> allowedType, boolean update) {
        Long snapshotDate = getSnapshotDate();
        if (snapshotDate == null || getAlliance_id() == 0) {
            return Collections.emptyMap();
        }
        Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreatiesAt(getAlliance_id(), snapshotDate);
        if (treaties.isEmpty() || allowedType == null) {
            return treaties;
        }
        Map<Integer, Treaty> filtered = new Int2ObjectOpenHashMap<>(treaties);
        filtered.entrySet().removeIf(entry -> !allowedType.test(entry.getValue().getType()));
        return filtered;
    }

    @Override
    public Set<DBAlliance> getTreatiedAllies() {
        return getTreatiedAllies(TreatyType::isDefensive, false);
    }

    @Override
    public Set<DBAlliance> getTreatiedAllies(boolean checkOffshore) {
        return getTreatiedAllies(TreatyType::isDefensive, checkOffshore);
    }

    @Override
    public Set<DBAlliance> getTreatiedAllies(Predicate<TreatyType> allowedType, boolean checkOffshore) {
        if (checkOffshore) {
            throw unsupportedSnapshot("offshore-aware treaty ally resolution");
        }
        Set<DBAlliance> allies = new ObjectOpenHashSet<>();
        for (Map.Entry<Integer, Treaty> treatyEntry : getTreaties(allowedType, false).entrySet()) {
            int otherAllianceId = treatyEntry.getKey();
            DBAlliance alliance = context == null ? DBAlliance.getOrCreate(otherAllianceId)
                    : context.getAlliance(otherAllianceId);
            if (alliance != null) {
                allies.add(alliance);
            }
        }
        return allies;
    }

    @Override
    public Set<DBAlliance> getSphere() {
        throw unsupportedSnapshot("sphere topology");
    }

    @Override
    public Set<DBAlliance> getSphereCached(Map<Integer, DBAlliance> aaCache) {
        throw unsupportedSnapshot("sphere topology");
    }

    @Override
    public List<DBAlliance> getSphereRankedCached(Map<Integer, DBAlliance> aaCache) {
        throw unsupportedSnapshot("sphere topology");
    }

    @Override
    public List<DBAlliance> getSphereRanked() {
        throw unsupportedSnapshot("sphere topology");
    }

    @Override
    public String getForum_link() {
        String forumLink = super.getForum_link();
        if (forumLink != null && !forumLink.isEmpty()) {
            return forumLink;
        }
        DBAlliance live = DBAlliance.get(getAlliance_id());
        return live == null || live == this ? "" : live.getForum_link();
    }

    @Override
    public String getDiscord_link() {
        String discordLink = super.getDiscord_link();
        if (discordLink != null && !discordLink.isEmpty()) {
            return discordLink;
        }
        DBAlliance live = DBAlliance.get(getAlliance_id());
        return live == null || live == this ? "" : live.getDiscord_link();
    }

    @Override
    public String getWiki_link() {
        String wikiLink = super.getWiki_link();
        if (wikiLink != null && !wikiLink.isEmpty()) {
            return wikiLink;
        }
        DBAlliance live = DBAlliance.get(getAlliance_id());
        return live == null || live == this ? "" : live.getWiki_link();
    }

    @Override
    public GuildDB getGuildDB() {
        throw unsupportedSnapshot("guild resolution");
    }

    @Override
    public List<AllianceChange> getRankChanges() {
        return super.getRankChanges();
    }

    @Override
    public List<AllianceChange> getRankChanges(long timeStart) {
        return super.getRankChanges(timeStart);
    }

    @Override
    public List<AllianceChange> getDepartures(long timeStart) {
        return super.getDepartures(timeStart);
    }

    @Override
    public Set<DBWar> getActiveWars() {
        Long snapshotDate = getSnapshotDate();
        if (snapshotDate == null || getAlliance_id() == 0) {
            return Collections.emptySet();
        }
        long start = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(snapshotDate) - 59);
        Set<DBWar> wars = new ObjectOpenHashSet<>();
        for (DBWar war : Locutus.imp().getWarDb().getWarsByAlliance(getAlliance_id())) {
            if (war.getDate() > start && war.getDate() < snapshotDate) {
                wars.add(war);
            }
        }
        if (wars.isEmpty()) {
            return wars;
        }
        Set<DBWar>[] activeWars = new Set[1];
        DBWar.DBWarKey warKey = new DBWar.DBWarKey(0);
        Locutus.imp().getWarDb().iterateAttackList(wars,
                attackType -> attackType == AttackType.VICTORY || attackType == AttackType.PEACE,
                attack -> {
                    if (attack.getDate() <= snapshotDate) {
                        Set<DBWar> value = activeWars[0];
                        if (value == null) {
                            value = new ObjectOpenHashSet<>(wars);
                            activeWars[0] = value;
                        }
                        value.remove(warKey.set(attack.getWar_id()));
                    }
                    return false;
                }, (war, attacks) -> {
                });
        return activeWars[0] == null ? wars : activeWars[0];
    }

    @Override
    public void deleteMeta(AllianceMeta key) {
        throw unsupportedSnapshot("meta persistence");
    }

    @Override
    public boolean setMetaRaw(int id, byte[] value) {
        throw unsupportedSnapshot("meta persistence");
    }

    @Override
    public void setMeta(AllianceMeta key, byte... value) {
        throw unsupportedSnapshot("meta persistence");
    }

    @Override
    public ByteBuffer getMeta(AllianceMeta key) {
        throw unsupportedSnapshot("meta persistence");
    }

    @Override
    public ApiKeyPool getApiKeys(AlliancePermission... permissions) {
        throw unsupportedSnapshot("alliance api keys");
    }

    @Override
    public Map<DBNation, Map<ResourceType, Double>> getMemberStockpile() throws IOException {
        throw unsupportedSnapshot("member stockpiles");
    }

    @Override
    public Map<DBNation, Map<ResourceType, Double>> getMemberStockpile(Predicate<DBNation> fetchNations)
            throws IOException {
        throw unsupportedSnapshot("member stockpiles");
    }

    @Override
    public PoliticsAndWarV3 getApiOrThrow(boolean preferKeyStore, AlliancePermission... permissions) {
        throw unsupportedSnapshot("alliance api access");
    }

    @Override
    public PoliticsAndWarV3 getApiOrThrow(AlliancePermission... permissions) {
        throw unsupportedSnapshot("alliance api access");
    }

    @Override
    public PoliticsAndWarV3 getApi(AlliancePermission... permissions) {
        throw unsupportedSnapshot("alliance api access");
    }

    @Override
    public Auth getAuth(AlliancePermission... permissions) {
        throw unsupportedSnapshot("alliance auth");
    }

    @Override
    public LootEntry getLoot() {
        Long snapshotDate = getSnapshotDate();
        if (snapshotDate == null || getAlliance_id() == 0) {
            return null;
        }
        if (historicalLootLoaded) {
            return historicalLoot;
        }
        synchronized (this) {
            if (!historicalLootLoaded) {
                historicalLoot = Locutus.imp().getNationDB().getAllianceLootAt(getAlliance_id(), snapshotDate);
                historicalLootLoaded = true;
            }
            return historicalLoot;
        }
    }

    @Override
    public double getLootValue(double score) {
        return ResourceType.convertedTotal(getLootArray(score));
    }

    @Override
    public Map<ResourceType, Double> getLoot(double score) {
        return ResourceType.resourcesToMap(getLootArray(score));
    }

    private double[] getLootArray(double score) {
        LootEntry loot = getLoot();
        if (loot == null) {
            return ResourceType.getBuffer();
        }
        double allianceScore = getScore();
        if (allianceScore <= 0) {
            return ResourceType.getBuffer();
        }
        double percent = WarOutcomeMath.expectedAllianceLootPercent(score, allianceScore);
        return PW.multiply(loot.getTotal_rss().clone(), percent);
    }

    @Override
    public Map<ResourceType, Double> getStockpile(boolean throwError) {
        throw unsupportedSnapshot("alliance bank stockpile");
    }

    @Override
    public void updateCities() throws IOException, ParseException {
        throw unsupportedSnapshot("city updates");
    }

    @Override
    public void updateCities(Predicate<DBNation> fetchNation) throws IOException {
        throw unsupportedSnapshot("city updates");
    }

    @Override
    public DBAlliance getCachedParentOfThisOffshore() {
        throw unsupportedSnapshot("offshore resolution");
    }

    @Override
    public DBAlliance findParentOfThisOffshore() {
        throw unsupportedSnapshot("offshore resolution");
    }

    @Override
    public DBAlliance findParentOfThisOffshore(Set<DBNation> membersOrNull, boolean checkSize) {
        throw unsupportedSnapshot("offshore resolution");
    }

    @Override
    public Set<DBAlliancePosition> getPositions() {
        throw unsupportedSnapshot("alliance positions");
    }

    @Override
    public List<TaxDeposit> updateTaxes(Long afterDate, boolean saveTaxes) {
        throw unsupportedSnapshot("tax updates");
    }

    @Override
    public Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> getResourcesNeeded(
            Collection<DBNation> nations, Map<DBNation, Map<ResourceType, Double>> existing, double daysDefault,
            boolean useExisting, boolean force) throws IOException {
        throw unsupportedSnapshot("resource planning");
    }

    @Override
    public OffshoreInstance getBank() {
        throw unsupportedSnapshot("alliance bank access");
    }

    @Override
    public int updateTaxesLegacy(Long latestDate) {
        throw unsupportedSnapshot("tax updates");
    }

    @Override
    public IShrink toShrinkMarkdown() {
        Set<DBNation> nations = getNations();
        Set<DBNation> members = new java.util.LinkedHashSet<>(nations);
        members.removeIf(nation -> nation.getPosition() <= 1 || nation.getVm_turns() != 0);
        Set<DBNation> applicants = new java.util.LinkedHashSet<>(nations);
        applicants.removeIf(nation -> nation.getPosition() != 1 || nation.getVm_turns() != 0);

        int off = nations.stream().mapToInt(DBNation::getOff).sum();
        int def = nations.stream().mapToInt(DBNation::getDef).sum();
        int cities = members.stream().mapToInt(DBNation::getCities).sum();
        double avgCities = members.isEmpty() ? 0 : cities / (double) members.size();
        double score = getScore(null);

        StringBuilder body = new StringBuilder();
        body.append("`AA:").append(getAlliance_id()).append("` | ").append(getMarkdownUrl());
        if (getAcronym() != null && !getAcronym().isEmpty()) {
            body.append(" / `").append(getAcronym()).append("`");
        }
        int rank = getRank(null);
        if (rank > 0) {
            body.append(" | `#").append(rank).append("`");
        }
        body.append("\n");
        Long snapshotDate = getSnapshotDate();
        if (snapshotDate != null) {
            body.append("Snapshot: ").append(TimeUtil.format(TimeUtil.DD_MM_YYYY, snapshotDate)).append("\n");
        }
        body.append("```\n");
        body.append(members.size()).append(" members");
        if (!applicants.isEmpty()) {
            body.append(" | ").append(applicants.size()).append(" applicants");
        }
        body.append("\n");
        body.append(off).append(" off | ").append(def).append(" def | ")
                .append(cities).append(" cities (avg:").append(MathMan.format(avgCities)).append(") | ")
                .append(MathMan.format(score)).append(" ns | ")
                .append(getColor() == null ? "null" : getColor().name()).append("\n```");
        return IShrink.of(body.toString());
    }

    private UnsupportedOperationException unsupportedSnapshot(String action) {
        return new UnsupportedOperationException("Alliance snapshot does not support " + action + ".");
    }
}