package link.locutus.discord.db.entities.city;

import com.google.common.base.Predicates;
import com.politicsandwar.graphql.model.City;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class SimpleDBCity extends DBCity {
    private int id;
    private int nation_id;
    private long created;
    private volatile long fetched;
    private int land_cents;
    private int infra_cents;
    private boolean powered;
    private byte[] buildings3;
    private int nuke_turn;

    public SimpleDBCity(int nation_id) {
        this.setNation_id(nation_id);
        this.setBuildings3(new byte[PW.City.Building.SIZE]);
    }

    public SimpleDBCity(City cityV3) {
        this.setBuildings3(new byte[PW.City.Building.SIZE]);
        set(cityV3);
    }

    public SimpleDBCity(DBCity toCopy) {
        this.set(toCopy, true);
    }

    public SimpleDBCity(ICity city) {
        this.buildings3 = new byte[PW.City.Building.SIZE];
        this.setCreated(city.getCreated());
        this.setLand(city.getLand());
        this.setInfra(city.getInfra());
        this.setPowered(city.getPowered() != Boolean.FALSE);
        for (Building building : Buildings.values()) {
            int amt = city.getBuilding(building);
            if (amt > 0) {
                this.setBuilding(building, amt);
            }
        }
        this.setNuke_turn(city.getNuke_turn());
    }

    public SimpleDBCity(ResultSet rs, int nationId) throws SQLException {
        setId(rs.getInt("id"));
        setCreated(rs.getLong("created"));
        setInfra_cents(rs.getInt("infra"));
        setLand_cents(rs.getInt("land"));
        setPowered(rs.getBoolean("powered"));
        setBuildings3(rs.getBytes("improvements"));
        if (getBuildings3().length < 14) {
            setBuildings3(Arrays.copyOf(getBuildings3(), getBuildings3().length + 1));
        }
        condense();
        setFetched(rs.getLong("update_flag"));
        setNuke_turn((int) TimeUtil.getTurn(rs.getLong("nuke_date")));
        this.setNation_id(nationId);
    }

    public SimpleDBCity(int id, JavaCity city) {
        this(id, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(city.getAgeDays()), city);
    }

    public SimpleDBCity(int id, long date, JavaCity city) {
        this.setId(id);
        this.setCreated(date);
        this.setInfra_cents((int) Math.round(city.getInfra() * 100));
        this.setLand_cents((int) Math.round(city.getLand() * 100));
        this.setBuildings3(city.getBuildings().clone());
        this.setPowered(city.getMetrics(Predicates.alwaysFalse()).powered);
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setNation_id(int nation_id) {
        this.nation_id = nation_id;
    }

    public int getId() {
        return id;
    }

    public int getNation_id() {
        return nation_id;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public long getFetched() {
        return fetched;
    }

    public void setFetched(long fetched) {
        this.fetched = fetched;
    }

    public int getLand_cents() {
        return land_cents;
    }

    public void setLand_cents(int land_cents) {
        this.land_cents = land_cents;
    }

    public int getInfra_cents() {
        return infra_cents;
    }

    public void setInfra_cents(int infra_cents) {
        this.infra_cents = infra_cents;
    }

    public boolean isPowered() {
        return powered;
    }

    public byte[] getBuildings3() {
        return buildings3;
    }

    public void setBuildings3(byte[] buildings3) {
        this.buildings3 = buildings3;
    }

    public int getNuke_turn() {
        return nuke_turn;
    }

    public void setNuke_turn(int nuke_turn) {
        this.nuke_turn = nuke_turn;
    }
}
