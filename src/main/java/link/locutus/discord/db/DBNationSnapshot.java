package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DBNationSnapshot extends DBNation {
    private long snapshotDate;
    private Map<Integer, DBCity> cityMap;

    public DBNationSnapshot(long date) {
        this.snapshotDate = date;
        this.beigeTimer = 0;
        this.projects = 0;
    }

    public void setSnapshotDate(long snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    @Override
    public long getBeigeAbsoluteTurn() {
        throw unsupported();
    }

    @Override
    public Long getSnapshot() {
        return snapshotDate;
    }

    public void setCityMap(Map<Integer, DBCity> cityMap) {
        this.cityMap = cityMap;
    }

    public void addCity(DBCity city) {
        if (cityMap == null) cityMap = new Int2ObjectOpenHashMap<>(this.getCities());
        cityMap.put(city.id, city);
    }

    public boolean hasCityData() {
        return cityMap != null;
    }

    private UnsupportedOperationException unsupported() {
        String parentMethodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        return new UnsupportedOperationException("The method " + parentMethodName + " is not yet supported for snapshots. Data may be available, please contact the developer.");
    }

    @Override
    public DBAlliance getAlliance() {
        throw unsupported();
    }

    @Override
    public double lootTotal() {
        throw unsupported();
    }

    @Override
    public Set<Integer> getBlockadedBy() {
        throw unsupported();
    }

    @Override
    public Set<Integer> getBlockading() {
        throw unsupported();
    }

    @Override
    public Map<Integer, DBCity> _getCitiesV3() {
        if (cityMap != null) return cityMap;
        Map<Integer, DBCity> cities = super._getCitiesV3();
        return cities.entrySet().stream().filter(e -> e.getValue().created <= snapshotDate).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public double getTreasureBonusPct() {
        return 0;
    }

    @Override
    public Set<DBBounty> getBounties() {
        throw unsupported();
    }

    @Override
    public long lastActiveMs() {
        return lastActiveMs(snapshotDate);
    }

    @Override
    public long getColorAbsoluteTurn() {
        throw unsupported();
    }

    // getDomesticPolicyAbsoluteTurn
    @Override
    public long getDomesticPolicyAbsoluteTurn() {
        throw unsupported();
    }
    // getWarPolicyAbsoluteTurn
    @Override
    public long getWarPolicyAbsoluteTurn() {
        throw unsupported();
    }
    // getEspionageFullTurn
    @Override
    public long getEspionageFullTurn() {
        throw unsupported();
    }
    // getCityTimerAbsoluteTurn
    @Override
    public long getCityTimerAbsoluteTurn() {
        throw unsupported();
    }
    // getCityTimerAbsoluteTurn

    @Override
    public Long getProjectAbsoluteTurn() {
        throw unsupported();
    }
}
