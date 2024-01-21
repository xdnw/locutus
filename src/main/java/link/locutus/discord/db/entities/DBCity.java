package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.City;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import javassist.bytecode.ByteArray;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.SCityContainer;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.city.*;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public class DBCity {
    public int id;
    public int nation_id;
    public long created;
    public volatile long fetched;
    public int land_cents;
    public int infra_cents;
    public boolean powered;
    public byte[] buildings3;
    public int nuke_turn;


    public static final ToIntFunction<DBCity> GET_ID = c -> c.id;

    public DBCity(int nation_id) {
        this.nation_id = nation_id;
        this.buildings3 = new byte[Buildings.size()];
    }

    public DBCity(City cityV3) {
        this.buildings3 = new byte[Buildings.size()];
        set(cityV3);
    }

    public DBCity(DBCity toCopy) {
        this.set(toCopy);
    }

    public String getUrl() {
        return PnwUtil.getCityUrl(id);
    }

    public void condense() {
        if (buildings3.length != Buildings.size()) {
            return;
        }
        for (byte b : buildings3) {
            if (b >= 0 && b < 16) continue;
            return;
        }
        byte[] half = new byte[(buildings3.length + 1) >> 1];
        for (int i = 0, j = 0; i < buildings3.length; i += 2, j++) {
            int number1 = buildings3[i] & 0xFF;
            int number2 = (i + 1 < buildings3.length) ? buildings3[i + 1] & 0xFF : 0; // Pad with 0 if uneven length
            byte packedNumbers = (byte)((number1 << 4) | number2);
            half[j] = packedNumbers;
        }
        buildings3 = half;
    }

    public byte[] toFull() {
        if (this.buildings3.length == Buildings.size()) {
            return buildings3;
        }
        // convert to full size
        byte[] full = new byte[Buildings.size()];
        for (int i = 0; i < full.length; i++) {
            full[i] = (byte) get(i);
        }
        return full;

    }

    private byte[] createCopy() {
        byte[] full = toFull();
        return full == buildings3 ? full.clone() : full;
    }

    public int getNumBuildings() {
        int total = 0;
        if (buildings3.length == Buildings.size()) {
            for (byte amt : buildings3) total += amt;
        } else {
            // two buildings per byte
            for (byte amt : buildings3) {
                if (amt == 0) continue;
                total += amt & 0x0F;
                total += (amt >> 4) & 0x0F;
            }
        }
        return total;
    }

    public int get(int ordinal) {
        if (buildings3.length == Buildings.size()) {
            return buildings3[ordinal];
        } else {
            int byteIndex = ordinal >> 1;
            byte pair = buildings3[byteIndex];
            if ((ordinal & 1) == 0) {
                return (pair >> 4) & 0x0F;
            } else {
                return pair & 0x0F;
            }
        }
    }

    public int get(Building building) {
        return get(building.ordinal());
    }

    public void set(SCityContainer container) {
        this.id = Integer.parseInt(container.getCityId());
        this.fetched = System.currentTimeMillis();
        if (created == 0) {
            created = this.fetched;
        }
        this.infra_cents = (int) Math.round(Double.parseDouble(container.getInfrastructure()) * 100);
        this.land_cents = (int) Math.round(Double.parseDouble(container.getLand()) * 100);
    }

    public void update(boolean events) {
        NationDB db = Locutus.imp().getNationDB();
        db.markCityDirty(-1, id, Long.MAX_VALUE);
        if (events) {
            Locutus.imp().runEventsAsync(f -> db.updateDirtyCities(true, f));
        } else {
            db.updateDirtyCities(true, null);
        }
    }

    public void set(DBCity toCopy) {
        this.id = toCopy.id;
        this.created = toCopy.created;
        this.fetched = toCopy.fetched;
        this.land_cents = toCopy.land_cents;
        this.infra_cents = toCopy.infra_cents;
        this.powered = toCopy.powered;;
        this.buildings3 = toCopy.buildings3;
        this.nuke_turn = toCopy.nuke_turn;
        this.nation_id = toCopy.nation_id;
    }

    public boolean set(City cityV3) {
        this.fetched = System.currentTimeMillis();
        this.nation_id = cityV3.getNation_id();
        this.id = cityV3.getId();
        this.created = cityV3.getDate().getTime();
        this.land_cents = (int) Math.round(cityV3.getLand() * 100);
        this.infra_cents = (int) Math.round(cityV3.getInfrastructure() * 100);
        if (cityV3.getPowered() != null) this.powered = cityV3.getPowered();

        if (cityV3.getNuke_date() != null) {
            long nowTurn = TimeUtil.getTurn();
            long cutoff = nowTurn - 132;
            long cityTurn = TimeUtil.getTurn(cityV3.getNuke_date().getTime());
            if (cityTurn > nowTurn) {
                if (this.nuke_turn == 0) nuke_turn = nuke_turn;
            } else if (cityTurn > cutoff) {
                this.nuke_turn = (int) cityTurn;
            }
        }

        byte[] newBuildings = null;
        if (this.get(Buildings.OIL_POWER) != cityV3.getOil_power()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.OIL_POWER.ordinal()] = (byte) (int) cityV3.getOil_power();
        }
        if (this.get(Buildings.WIND_POWER) != cityV3.getWind_power()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.WIND_POWER.ordinal()] = (byte) (int) cityV3.getWind_power();
        }
        if (this.get(Buildings.COAL_POWER) != cityV3.getCoal_power()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.COAL_POWER.ordinal()] = (byte) (int) cityV3.getCoal_power();
        }
        if (this.get(Buildings.NUCLEAR_POWER) != cityV3.getNuclear_power()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.NUCLEAR_POWER.ordinal()] = (byte) (int) cityV3.getNuclear_power();
        }
        if (this.get(Buildings.COAL_MINE) != cityV3.getCoal_mine()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.COAL_MINE.ordinal()] = (byte) (int) cityV3.getCoal_mine();
        }
        if (this.get(Buildings.LEAD_MINE) != cityV3.getLead_mine()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.LEAD_MINE.ordinal()] = (byte) (int) cityV3.getLead_mine();
        }
        if (this.get(Buildings.IRON_MINE) != cityV3.getIron_mine()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.IRON_MINE.ordinal()] = (byte) (int) cityV3.getIron_mine();
        }
        if (this.get(Buildings.BAUXITE_MINE) != cityV3.getBauxite_mine()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.BAUXITE_MINE.ordinal()] = (byte) (int) cityV3.getBauxite_mine();
        }
        if (this.get(Buildings.OIL_WELL) != cityV3.getOil_well()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.OIL_WELL.ordinal()] = (byte) (int) cityV3.getOil_well();
        }
        if (this.get(Buildings.URANIUM_MINE) != cityV3.getUranium_mine()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.URANIUM_MINE.ordinal()] = (byte) (int) cityV3.getUranium_mine();
        }
        if (this.get(Buildings.FARM) != cityV3.getFarm()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.FARM.ordinal()] = (byte) (int) cityV3.getFarm();
        }
        if (this.get(Buildings.POLICE_STATION) != cityV3.getPolice_station()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.POLICE_STATION.ordinal()] = (byte) (int) cityV3.getPolice_station();
        }
        if (this.get(Buildings.HOSPITAL) != cityV3.getHospital()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.HOSPITAL.ordinal()] = (byte) (int) cityV3.getHospital();
        }
        if (this.get(Buildings.RECYCLING_CENTER) != cityV3.getRecycling_center()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.RECYCLING_CENTER.ordinal()] = (byte) (int) cityV3.getRecycling_center();
        }
        if (this.get(Buildings.SUBWAY) != cityV3.getSubway()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.SUBWAY.ordinal()] = (byte) (int) cityV3.getSubway();
        }
        if (this.get(Buildings.SUPERMARKET) != cityV3.getSupermarket()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.SUPERMARKET.ordinal()] = (byte) (int) cityV3.getSupermarket();
        }
        if (this.get(Buildings.BANK) != cityV3.getBank()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.BANK.ordinal()] = (byte) (int) cityV3.getBank();
        }
        if (this.get(Buildings.MALL) != cityV3.getShopping_mall()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.MALL.ordinal()] = (byte) (int) cityV3.getShopping_mall();
        }
        if (this.get(Buildings.STADIUM) != cityV3.getStadium()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.STADIUM.ordinal()] = (byte) (int) cityV3.getStadium();
        }
        if (this.get(Buildings.GAS_REFINERY) != cityV3.getOil_refinery()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.GAS_REFINERY.ordinal()] = (byte) (int) cityV3.getOil_refinery();
        }
        if (this.get(Buildings.ALUMINUM_REFINERY) != cityV3.getAluminum_refinery()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.ALUMINUM_REFINERY.ordinal()] = (byte) (int) cityV3.getAluminum_refinery();
        }
        if (this.get(Buildings.STEEL_MILL) != cityV3.getSteel_mill()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.STEEL_MILL.ordinal()] = (byte) (int) cityV3.getSteel_mill();
        }
        if (this.get(Buildings.MUNITIONS_FACTORY) != cityV3.getMunitions_factory()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.MUNITIONS_FACTORY.ordinal()] = (byte) (int) cityV3.getMunitions_factory();
        }
        if (this.get(Buildings.BARRACKS) != cityV3.getBarracks()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.BARRACKS.ordinal()] = (byte) (int) cityV3.getBarracks();
        }
        if (this.get(Buildings.FACTORY) != cityV3.getFactory()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.FACTORY.ordinal()] = (byte) (int) cityV3.getFactory();
        }
        if (this.get(Buildings.HANGAR) != cityV3.getHangar()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.HANGAR.ordinal()] = (byte) (int) cityV3.getHangar();
        }
        if (this.get(Buildings.DRYDOCK) != cityV3.getDrydock()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.DRYDOCK.ordinal()] = (byte) (int) cityV3.getDrydock();
        }
        if (newBuildings != null) {
            this.buildings3 = newBuildings;
            this.condense();
        }
        return newBuildings != null;
    }

    public boolean runChangeEvents(int nationId, DBCity previous, Consumer<Event> eventConsumer) {
        if (previous == null) {
            if (eventConsumer != null) {
                DBNation nation = DBNation.getById(nationId);
//                if (nation != null && nation.active_m() > 4880) {
//                    new Exception().printStackTrace();
////                    AlertUtil.error("Invalid city create", "city for " + nationId + " | " + this);
//                }
                if (this.created > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) {
                    Locutus.imp().getNationDB().setNationActive(nationId, this.created, eventConsumer);
                    eventConsumer.accept(new CityCreateEvent(nationId, this));
                }
            }
            return true;
        }
        boolean changed = false;

        DBCity previousClone = null;
        if (this.land_cents != previous.land_cents) {
            if (this.land_cents > previous.land_cents) {
                if (eventConsumer != null) {
                    if (previousClone == null) previousClone = new DBCity(previous);
                    eventConsumer.accept(new CityLandBuyEvent(nationId, previousClone, this));
                }
            } else {
                if (eventConsumer != null) {
                    if (previousClone == null) previousClone = new DBCity(previous);
                    eventConsumer.accept(new CityLandSellEvent(nationId, previousClone, this));
                }
            }
            changed = true;
        }

        if (this.nuke_turn != previous.nuke_turn) {
            if (eventConsumer != null) {
                if (previousClone == null) previousClone = new DBCity(previous);
                eventConsumer.accept(new CityNukeEvent(nationId, previousClone, this));
            }
            changed = true;
        }

        if (this.powered != previous.powered) {
            if (eventConsumer != null) {
                if (previousClone == null) previousClone = new DBCity(previous);
                eventConsumer.accept(new CityPowerChangeEvent(nationId, previousClone, this));
            }
            changed = true;
        }

        if (!Arrays.equals(this.toFull(), previous.toFull())) {
            if (eventConsumer != null) {
                if (previousClone == null) previousClone = new DBCity(previous);
                eventConsumer.accept(new CityBuildingChangeEvent(nationId, previousClone, this));
            }
            changed = true;
        }

        if (this.infra_cents != previous.infra_cents) {
            if (previous.infra_cents > 0) {
                if (this.infra_cents > previous.infra_cents + 1 && getNumBuildings() * 5000 <= this.infra_cents) {
                    if (eventConsumer != null && (previous.infra_cents != 0 || this.infra_cents != 1000)) {
                        if (previousClone == null) previousClone = new DBCity(previous);
                        eventConsumer.accept(new CityInfraBuyEvent(nationId, previousClone, this));
                    }
                } else if (this.infra_cents < previous.infra_cents - 1) {
                    if (eventConsumer != null) {
                        if (previousClone == null) previousClone = new DBCity(previous);
                        boolean isAttack = (this.toJavaCity(f -> false).getRequiredInfra() > this.infra_cents * 0.01d);
                        Event event;
                        if (isAttack) {
                            event = new CityInfraDamageEvent(nationId, previousClone, this);
                        } else {
                            event = new CityInfraSellEvent(nationId, previousClone, this);
                        }
                        eventConsumer.accept(event);
                    }
                }
            }
            changed = true;
        }

        return changed;
    }

    public DBCity(ResultSet rs, int nationId) throws SQLException {
        id = rs.getInt("id");
        created = rs.getLong("created");
        infra_cents = rs.getInt("infra");
        land_cents = rs.getInt("land");
        powered = rs.getBoolean("powered");
        buildings3 = rs.getBytes("improvements");
        if (buildings3.length < 14) {
            buildings3 = Arrays.copyOf(buildings3, buildings3.length + 1);
        }
        condense();
        fetched = rs.getLong("update_flag");
        nuke_turn = (int) TimeUtil.getTurn(rs.getLong("nuke_date"));
        this.nation_id = nationId;
    }

    public DBCity(int id, JavaCity city) {
        this(id, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(city.getAge()), city);
    }

    public DBCity(int id, long date, JavaCity city) {
        this.id = id;
        this.created = date;
        this.infra_cents = (int) Math.round(city.getInfra() * 100);
        this.land_cents = (int) Math.round(city.getLand() * 100);
        this.buildings3 = city.getBuildings();
        this.powered = city.getMetrics(f -> false).powered;
    }

    public JavaCity toJavaCity(DBNation nation) {
        return toJavaCity(nation::hasProject);
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DBCity city) {
            return city.id == id;
        }
        if (obj instanceof ArrayUtil.IntKey key) {
            return key.key == id;
        }
        return false;
    }

    public JavaCity toJavaCity(Predicate<Project> hasProject) {
        JavaCity javaCity = new JavaCity(this.createCopy(), infra_cents * 0.01, land_cents * 0.01, created, nuke_turn);
        if (!powered && javaCity.getPoweredInfra() >= infra_cents * 0.01) {
            javaCity.getMetrics(hasProject).powered = false;
        }
        return javaCity;
    }

    public String getMMR() {
        return get(Buildings.BARRACKS) + "" + get(Buildings.FACTORY) + "" + get(Buildings.HANGAR) + "" + get(Buildings.DRYDOCK);
    }

    public int[] getMMRArray() {
        return new int[]{get(Buildings.BARRACKS), get(Buildings.FACTORY), get(Buildings.HANGAR), get(Buildings.DRYDOCK)};
    }

    public double getInfra() {
        return infra_cents * 0.01;
    }

    public double getLand() {
        return land_cents * 0.01;
    }

    public void setInfra(double v) {
        infra_cents = (int) Math.round(v * 100);
    }

    public void setLand(double v) {
        land_cents = (int) Math.round(v * 100);
    }

    public int getNationId() {
        return nation_id;
    }
}
