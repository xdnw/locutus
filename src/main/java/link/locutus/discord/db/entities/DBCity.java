package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.City;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.city.*;
import views.grant.city;
import views.grant.nation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DBCity {
    public int id;
    public long created;
    public double land;
    public double infra;
    public boolean powered;
    public byte[] buildings = new byte[Buildings.size()];

    public volatile long fetched;

    public DBCity() {

    }

    public DBCity(City cityV3) {
        set(cityV3);
    }

    public DBCity(DBCity toCopy) {
        this.set(toCopy);
    }

    public void set(DBCity toCopy) {
        this.id = toCopy.id;
        this.created = toCopy.created;
        this.fetched = toCopy.fetched;
        this.land = toCopy.land;
        this.infra = toCopy.infra;
        this.powered = toCopy.powered;;
        for (int i = 0; i < buildings.length; i++) {
            buildings[i] = toCopy.buildings[i];
        }
    }

    public DBCity set(City cityV3) {
        this.fetched = System.currentTimeMillis();

        this.id = cityV3.getId();
        this.created = cityV3.getDate().getTime();
        this.land = cityV3.getLand();
        this.infra = cityV3.getInfrastructure();
        this.powered = cityV3.getPowered();

        buildings[Buildings.OIL_POWER.ordinal()] += cityV3.getOil_power();
        buildings[Buildings.WIND_POWER.ordinal()] += cityV3.getWind_power();
        buildings[Buildings.COAL_POWER.ordinal()] += cityV3.getCoal_power();
        buildings[Buildings.NUCLEAR_POWER.ordinal()] += cityV3.getNuclear_power();
        buildings[Buildings.COAL_MINE.ordinal()] += cityV3.getCoal_mine();
        buildings[Buildings.LEAD_MINE.ordinal()] += cityV3.getLead_mine();
        buildings[Buildings.IRON_MINE.ordinal()] += cityV3.getIron_mine();
        buildings[Buildings.BAUXITE_MINE.ordinal()] += cityV3.getBauxite_mine();
        buildings[Buildings.OIL_WELL.ordinal()] += cityV3.getOil_well();
        buildings[Buildings.URANIUM_MINE.ordinal()] += cityV3.getUranium_mine();
        buildings[Buildings.FARM.ordinal()] += cityV3.getFarm();
        buildings[Buildings.POLICE_STATION.ordinal()] += cityV3.getPolice_station();
        buildings[Buildings.HOSPITAL.ordinal()] += cityV3.getHospital();
        buildings[Buildings.RECYCLING_CENTER.ordinal()] += cityV3.getRecycling_center();
        buildings[Buildings.SUBWAY.ordinal()] += cityV3.getSubway();
        buildings[Buildings.SUPERMARKET.ordinal()] += cityV3.getSupermarket();
        buildings[Buildings.BANK.ordinal()] += cityV3.getBank();
        buildings[Buildings.MALL.ordinal()] += cityV3.getShopping_mall();
        buildings[Buildings.STADIUM.ordinal()] += cityV3.getStadium();
        buildings[Buildings.GAS_REFINERY.ordinal()] += cityV3.getOil_refinery();
        buildings[Buildings.ALUMINUM_REFINERY.ordinal()] += cityV3.getAluminum_refinery();
        buildings[Buildings.STEEL_MILL.ordinal()] += cityV3.getSteel_mill();
        buildings[Buildings.MUNITIONS_FACTORY.ordinal()] += cityV3.getMunitions_factory();
        buildings[Buildings.BARRACKS.ordinal()] += cityV3.getBarracks();
        buildings[Buildings.FACTORY.ordinal()] += cityV3.getFactory();
        buildings[Buildings.HANGAR.ordinal()] += cityV3.getHangar();
        buildings[Buildings.DRYDOCK.ordinal()] += cityV3.getDrydock();
        return this;
    }

    public boolean runChangeEvents(int nationId, DBCity previous, Consumer<Event> eventConsumer) {
        if (previous == null) {
            if (eventConsumer != null) {
                Locutus.imp().getNationDB().setNationActive(nationId, fetched);
                eventConsumer.accept(new CityCreateEvent(nationId, this));
            }
            return true;
        }
        boolean changed = false;

        DBCity previousClone = null;
        if (this.land != previous.land) {
            if (this.land > previous.land) {
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

        if (this.powered != previous.powered) {
            if (eventConsumer != null) {
                if (previousClone == null) previousClone = new DBCity(previous);
                eventConsumer.accept(new CityPowerChangeEvent(nationId, previousClone, this));
            }
            changed = true;
        }

        if (!Arrays.equals(this.buildings, previous.buildings)) {
            if (eventConsumer != null) {
                if (previousClone == null) previousClone = new DBCity(previous);
                eventConsumer.accept(new CityBuildingChangeEvent(nationId, previousClone, this));
            }
            changed = true;
        }

        if (this.infra != previous.infra) {
            if (this.infra > previous.infra) {
                if (eventConsumer != null) {
                    if (previousClone == null) previousClone = new DBCity(previous);
                    eventConsumer.accept(new CityInfraBuyEvent(nationId, previousClone, this));
                }
            } else {
                if (eventConsumer != null) {
                    if (previousClone == null) previousClone = new DBCity(previous);
                    boolean isAttack = (this.toJavaCity(f -> false).getRequiredInfra() > this.infra);
                    Event event;
                    if (isAttack) {
                        event = new CityInfraDamageEvent(nationId, previousClone, this);
                    } else {
                        event = new CityInfraSellEvent(nationId, previousClone, this);
                    }
                    eventConsumer.accept(event);
                }
            }
            changed = true;
        }

        return changed;
    }

    public DBCity(ResultSet rs) throws SQLException {
        id = rs.getInt("id");
        created = rs.getLong("created");
        infra = rs.getInt("infra") / 100d;
        land = rs.getInt("land") / 100d;
        powered = rs.getBoolean("powered");
        buildings = rs.getBytes("improvements");
        fetched = rs.getLong("update_flag");
    }

    public DBCity(int id, JavaCity city) {
        this(id, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(city.getAge()), city);
    }

    public DBCity(int id, long date, JavaCity city) {
        this.id = id;
        this.created = date;
        this.infra = city.getInfra();
        this.land = city.getLand();
        this.buildings = city.getBuildings();
        this.powered = city.getMetrics(f -> false).powered;
    }

    public JavaCity toJavaCity(DBNation nation) {
        return toJavaCity(nation::hasProject);
    }

    public JavaCity toJavaCity(Predicate<Project> hasProject) {
        JavaCity javaCity = new JavaCity(buildings, infra, land, created);
        if (!powered && javaCity.getPoweredInfra() >= infra) {
            javaCity.getMetrics(hasProject).powered = false;
        }
        return javaCity;
    }
}
