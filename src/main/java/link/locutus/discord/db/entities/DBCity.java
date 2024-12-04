package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.City;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.SCityContainer;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.city.SimpleDBCity;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.city.*;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public abstract class DBCity implements ICity {
    public static final ToIntFunction<SimpleDBCity> GET_ID = c -> c.getId();

    public void setBuilding(Building building, int amt) {
        if (getBuildings3().length != PW.City.Building.SIZE) {
            setBuildings3(this.toFull());
        }
        getBuildings3()[building.ordinal()] = (byte) amt;
    }



    public void setDateCreated(long date_created) {
        this.setCreated(date_created);
    }



    @Command(desc = "Url of this city")
    public String getUrl() {
        return PW.City.getCityUrl(getId());
    }

    public void condense() {
        if (getBuildings3().length != PW.City.Building.SIZE) {
            return;
        }
        for (byte b : getBuildings3()) {
            if (b >= 0 && b < 16) continue;
            return;
        }
        byte[] half = new byte[(getBuildings3().length + 1) >> 1];
        for (int i = 0, j = 0; i < getBuildings3().length; i += 2, j++) {
            int number1 = getBuildings3()[i] & 0xFF;
            int number2 = (i + 1 < getBuildings3().length) ? getBuildings3()[i + 1] & 0xFF : 0; // Pad with 0 if uneven length
            byte packedNumbers = (byte)((number1 << 4) | number2);
            half[j] = packedNumbers;
        }
        setBuildings3(half);
    }

    public byte[] toFull() {
        if (this.getBuildings3().length == PW.City.Building.SIZE) {
            return getBuildings3();
        }
        // convert to full size
        byte[] full = new byte[PW.City.Building.SIZE];
        for (int i = 0; i < full.length; i++) {
            full[i] = (byte) getBuildingOrdinal(i);
        }
        return full;

    }

    private byte[] createCopy() {
        byte[] full = toFull();
        return full == getBuildings3() ? full.clone() : full;
    }

    @Command(desc = "Number of buildings in this city")
    public int getNumBuildings() {
        int total = 0;
        if (getBuildings3().length == PW.City.Building.SIZE) {
            for (byte amt : getBuildings3()) total += amt;
        } else {
            // two buildings per byte
            for (byte amt : getBuildings3()) {
                if (amt == 0) continue;
                total += amt & 0x0F;
                total += (amt >> 4) & 0x0F;
            }
        }
        return total;
    }

    public int getBuildingOrdinal(int ordinal) {
        if (getBuildings3().length == PW.City.Building.SIZE) {
            return getBuildings3()[ordinal];
        } else {
            int byteIndex = ordinal >> 1;
            byte pair = getBuildings3()[byteIndex];
            if ((ordinal & 1) == 0) {
                return (pair >> 4) & 0x0F;
            } else {
                return pair & 0x0F;
            }
        }
    }

    @Command(desc = "Get building amount")
    public int getBuilding(Building building) {
        return getBuildingOrdinal(building.ordinal());
    }

    public void set(SCityContainer container) {
        this.setId(Integer.parseInt(container.getCityId()));
        this.setFetched(System.currentTimeMillis());
        if (getCreated() == 0) {
            setCreated(this.getFetched());
        }
        this.setInfra_cents((int) Math.round(Double.parseDouble(container.getInfrastructure()) * 100));
        this.setLand_cents((int) Math.round(Double.parseDouble(container.getLand()) * 100));
    }

    public void update(boolean events) {
        NationDB db = Locutus.imp().getNationDB();
        db.markCityDirty(-1, getId(), Long.MAX_VALUE);
        if (events) {
            Locutus.imp().runEventsAsync(f -> db.updateDirtyCities(true, f));
        } else {
            db.updateDirtyCities(true, null);
        }
    }

    public void set(DBCity toCopy) {
        this.setId(toCopy.getId());
        this.setCreated(toCopy.getCreated());
        this.setFetched(toCopy.getFetched());
        this.setLand_cents(toCopy.getLand_cents());
        this.setInfra_cents(toCopy.getInfra_cents());
        this.setPowered(toCopy.isPowered());;
        this.setBuildings3(toCopy.getBuildings3());
        this.setNuke_turn(toCopy.getNuke_turn());
        this.setNation_id(toCopy.getNation_id());
    }

    public void setNuke_turn(long nuke_turn) {
        this.setNuke_turn((int) nuke_turn);
    }

    public boolean set(City cityV3) {
        this.setFetched(System.currentTimeMillis());
        this.setNation_id(cityV3.getNation_id());
        this.setId(cityV3.getId());
        this.setCreated(cityV3.getDate().getTime());
        this.setLand_cents((int) Math.round(cityV3.getLand() * 100));
        this.setInfra_cents((int) Math.round(cityV3.getInfrastructure() * 100));
        if (cityV3.getPowered() != null) this.setPowered(cityV3.getPowered());

        if (cityV3.getNuke_date() != null) {
            long nowTurn = TimeUtil.getTurn();
            long cutoff = nowTurn - 132;
            long cityTurn = TimeUtil.getTurn(cityV3.getNuke_date().getTime());
            if (cityTurn > nowTurn) {
                if (this.getNuke_turn() == 0) setNuke_turn(getNuke_turn());
            } else if (cityTurn > cutoff) {
                this.setNuke_turn((int) cityTurn);
            }
        }

        byte[] newBuildings = null;
        if (this.getBuilding(Buildings.OIL_POWER) != cityV3.getOil_power()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.OIL_POWER.ordinal()] = (byte) (int) cityV3.getOil_power();
        }
        if (this.getBuilding(Buildings.WIND_POWER) != cityV3.getWind_power()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.WIND_POWER.ordinal()] = (byte) (int) cityV3.getWind_power();
        }
        if (this.getBuilding(Buildings.COAL_POWER) != cityV3.getCoal_power()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.COAL_POWER.ordinal()] = (byte) (int) cityV3.getCoal_power();
        }
        if (this.getBuilding(Buildings.NUCLEAR_POWER) != cityV3.getNuclear_power()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.NUCLEAR_POWER.ordinal()] = (byte) (int) cityV3.getNuclear_power();
        }
        if (this.getBuilding(Buildings.COAL_MINE) != cityV3.getCoal_mine()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.COAL_MINE.ordinal()] = (byte) (int) cityV3.getCoal_mine();
        }
        if (this.getBuilding(Buildings.LEAD_MINE) != cityV3.getLead_mine()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.LEAD_MINE.ordinal()] = (byte) (int) cityV3.getLead_mine();
        }
        if (this.getBuilding(Buildings.IRON_MINE) != cityV3.getIron_mine()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.IRON_MINE.ordinal()] = (byte) (int) cityV3.getIron_mine();
        }
        if (this.getBuilding(Buildings.BAUXITE_MINE) != cityV3.getBauxite_mine()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.BAUXITE_MINE.ordinal()] = (byte) (int) cityV3.getBauxite_mine();
        }
        if (this.getBuilding(Buildings.OIL_WELL) != cityV3.getOil_well()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.OIL_WELL.ordinal()] = (byte) (int) cityV3.getOil_well();
        }
        if (this.getBuilding(Buildings.URANIUM_MINE) != cityV3.getUranium_mine()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.URANIUM_MINE.ordinal()] = (byte) (int) cityV3.getUranium_mine();
        }
        if (this.getBuilding(Buildings.FARM) != cityV3.getFarm()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.FARM.ordinal()] = (byte) (int) cityV3.getFarm();
        }
        if (this.getBuilding(Buildings.POLICE_STATION) != cityV3.getPolice_station()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.POLICE_STATION.ordinal()] = (byte) (int) cityV3.getPolice_station();
        }
        if (this.getBuilding(Buildings.HOSPITAL) != cityV3.getHospital()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.HOSPITAL.ordinal()] = (byte) (int) cityV3.getHospital();
        }
        if (this.getBuilding(Buildings.RECYCLING_CENTER) != cityV3.getRecycling_center()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.RECYCLING_CENTER.ordinal()] = (byte) (int) cityV3.getRecycling_center();
        }
        if (this.getBuilding(Buildings.SUBWAY) != cityV3.getSubway()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.SUBWAY.ordinal()] = (byte) (int) cityV3.getSubway();
        }
        if (this.getBuilding(Buildings.SUPERMARKET) != cityV3.getSupermarket()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.SUPERMARKET.ordinal()] = (byte) (int) cityV3.getSupermarket();
        }
        if (this.getBuilding(Buildings.BANK) != cityV3.getBank()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.BANK.ordinal()] = (byte) (int) cityV3.getBank();
        }
        if (this.getBuilding(Buildings.MALL) != cityV3.getShopping_mall()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.MALL.ordinal()] = (byte) (int) cityV3.getShopping_mall();
        }
        if (this.getBuilding(Buildings.STADIUM) != cityV3.getStadium()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.STADIUM.ordinal()] = (byte) (int) cityV3.getStadium();
        }
        if (this.getBuilding(Buildings.GAS_REFINERY) != cityV3.getOil_refinery()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.GAS_REFINERY.ordinal()] = (byte) (int) cityV3.getOil_refinery();
        }
        if (this.getBuilding(Buildings.ALUMINUM_REFINERY) != cityV3.getAluminum_refinery()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.ALUMINUM_REFINERY.ordinal()] = (byte) (int) cityV3.getAluminum_refinery();
        }
        if (this.getBuilding(Buildings.STEEL_MILL) != cityV3.getSteel_mill()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.STEEL_MILL.ordinal()] = (byte) (int) cityV3.getSteel_mill();
        }
        if (this.getBuilding(Buildings.MUNITIONS_FACTORY) != cityV3.getMunitions_factory()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.MUNITIONS_FACTORY.ordinal()] = (byte) (int) cityV3.getMunitions_factory();
        }
        if (this.getBuilding(Buildings.BARRACKS) != cityV3.getBarracks()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.BARRACKS.ordinal()] = (byte) (int) cityV3.getBarracks();
        }
        if (this.getBuilding(Buildings.FACTORY) != cityV3.getFactory()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.FACTORY.ordinal()] = (byte) (int) cityV3.getFactory();
        }
        if (this.getBuilding(Buildings.HANGAR) != cityV3.getHangar()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.HANGAR.ordinal()] = (byte) (int) cityV3.getHangar();
        }
        if (this.getBuilding(Buildings.DRYDOCK) != cityV3.getDrydock()) {
            if (newBuildings == null) newBuildings = createCopy();
            newBuildings[Buildings.DRYDOCK.ordinal()] = (byte) (int) cityV3.getDrydock();
        }
        if (newBuildings != null) {
            this.setBuildings3(newBuildings);
            this.condense();
        }
        return newBuildings != null;
    }

    public boolean runChangeEvents(int nationId, DBCity previous, Consumer<Event> eventConsumer) {
        if (previous == null) {
            if (eventConsumer != null) {
//                DBNation nation = DBNation.getById(nationId);
////                if (nation != null && nation.active_m() > 4880) {
////                    new Exception().printStackTrace();
//////                    AlertUtil.error("Invalid city create", "city for " + nationId + " | " + this);
////                }
                if (this.getCreated() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) {
                    Locutus.imp().getNationDB().setNationActive(nationId, this.getCreated(), eventConsumer);
                    eventConsumer.accept(new CityCreateEvent(nationId, this));
                }
            }
            return true;
        }
        boolean changed = false;

        DBCity previousClone = null;
        if (this.getLand_cents() != previous.getLand_cents()) {
            if (this.getLand_cents() > previous.getLand_cents()) {
                if (eventConsumer != null) {
                    if (previousClone == null) previousClone = new SimpleDBCity(previous);
                    eventConsumer.accept(new CityLandBuyEvent(nationId, previousClone, this));
                }
            } else {
                if (eventConsumer != null) {
                    if (previousClone == null) previousClone = new SimpleDBCity(previous);
                    eventConsumer.accept(new CityLandSellEvent(nationId, previousClone, this));
                }
            }
            changed = true;
        }

        if (this.getNuke_turn() != previous.getNuke_turn()) {
            if (eventConsumer != null) {
                if (previousClone == null) previousClone = new SimpleDBCity(previous);
                eventConsumer.accept(new CityNukeEvent(nationId, previousClone, this));
            }
            changed = true;
        }

        if (this.isPowered() != previous.isPowered()) {
            if (eventConsumer != null) {
                if (previousClone == null) previousClone = new SimpleDBCity(previous);
                eventConsumer.accept(new CityPowerChangeEvent(nationId, previousClone, this));
            }
            changed = true;
        }

        if (!Arrays.equals(this.toFull(), previous.toFull())) {
            if (eventConsumer != null) {
                if (previousClone == null) previousClone = new SimpleDBCity(previous);
                eventConsumer.accept(new CityBuildingChangeEvent(nationId, previousClone, this));
            }
            changed = true;
        }

        if (this.getInfra_cents() != previous.getInfra_cents()) {
            if (previous.getInfra_cents() > 0) {
                if (this.getInfra_cents() > previous.getInfra_cents() + 1 && getNumBuildings() * 5000 <= this.getInfra_cents()) {
                    if (eventConsumer != null && (previous.getInfra_cents() != 0 || this.getInfra_cents() != 1000)) {
                        if (previousClone == null) previousClone = new SimpleDBCity(previous);
                        eventConsumer.accept(new CityInfraBuyEvent(nationId, previousClone, this));
                    }
                } else if (this.getInfra_cents() < previous.getInfra_cents() - 1) {
                    if (eventConsumer != null) {
                        if (previousClone == null) previousClone = new SimpleDBCity(previous);
                        boolean isAttack = (this.toJavaCity(f -> false).getRequiredInfra() > this.getInfra_cents() * 0.01d);
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

    public JavaCity toJavaCity(DBNation nation) {
        return toJavaCity(nation::hasProject);
    }

    @Override
    public int hashCode() {
        return getId();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DBCity city) {
            return city.getId() == getId();
        }
        if (obj instanceof ArrayUtil.IntKey key) {
            return key.key == getId();
        }
        if (obj instanceof Integer) {
            return (int) obj == getId();
        }
        return false;
    }

    public JavaCity toJavaCity(Predicate<Project> hasProject) {
        JavaCity javaCity = new JavaCity(this.createCopy(), getInfra_cents() * 0.01, getLand_cents() * 0.01, getCreated(), getNuke_turn());
        if (!isPowered() && javaCity.getPoweredInfra() >= getInfra_cents() * 0.01) {
            javaCity.getMetrics(hasProject).powered = false;
        }
        return javaCity;
    }

    @Command(desc = "Get city mmr")
    public String getMMR() {
        return getBuilding(Buildings.BARRACKS) + "" + getBuilding(Buildings.FACTORY) + "" + getBuilding(Buildings.HANGAR) + "" + getBuilding(Buildings.DRYDOCK);
    }

    public int[] getMMRArray() {
        return new int[]{getBuilding(Buildings.BARRACKS), getBuilding(Buildings.FACTORY), getBuilding(Buildings.HANGAR), getBuilding(Buildings.DRYDOCK)};
    }

    @Command(desc = "Get city infrastructure")
    public double getInfra() {
        return getInfra_cents() * 0.01;
    }

    @Command(desc = "Get city land")
    public double getLand() {
        return getLand_cents() * 0.01;
    }

    public void setInfra(double v) {
        setInfra_cents((int) Math.round(v * 100));
    }

    public void setLand(double v) {
        setLand_cents((int) Math.round(v * 100));
    }

    @Command(desc = "Get nation id of this city")
    public int getNationId() {
        return getNation_id();
    }

    @Command(desc = "Get nation of this city")
    public DBNation getNation() {
        return DBNation.getById(getNation_id());
    }

//    public long created;
    @Command(desc = "Get city created date")
    public long getCreatedMillis() {
        return getCreated();
    }

    @Command(desc = "Get city age in milliseconds")
    public long getAgeMillis() {
        return System.currentTimeMillis() - getCreated();
    }

    @Command(desc = "Get city age in days")
    public int getAgeDays() {
        if (getCreated() <= 0) return 1;
        if (getCreated() == Long.MAX_VALUE) return 1;
        return (int) Math.max(1, TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - getCreated()));
    }
    @Command(desc = "Is city powered")
    @Override
    public Boolean getPowered() {
        return isPowered();
    }

    @Command(desc = "The turns since epoch this city was last nuked, or 0")
    public int getNukeTurnEpoch() {
        return getNuke_turn();
    }

    @Command(desc = "Get the turns since last nuked, or 0")
    public int getNukeTurn() {
        return (int) TimeUtil.getTurn() - getNuke_turn();
    }

    private Predicate<Project> getProjectPredicate() {
        return getNation() == null ? f -> false : getNation()::hasProject;
    }

    @Command(desc = "Get city pollution")
    public int getPollution() {
        return calcPollution(getProjectPredicate());
    }

    @Override
    public int calcPollution(Predicate<Project> hasProject) {
        return PW.City.getPollution(hasProject, this::getBuilding, PW.City.getNukePollution(getNuke_turn()));
    }

    @Command(desc = "Get city commerce")
    public int getCommerce() {
        return calcCommerce(getProjectPredicate());
    }

    @Override
    public int calcCommerce(Predicate<Project> hasProject) {
        return PW.City.getCommerce(hasProject, this::getBuilding);
    }

    @Command(desc = "Get the required infrastructure level for the number of buildings")
    public int getRequiredInfra() {
        return getNumBuildings() * 50;
    }
    @Command(desc = "Get the resource cost of buildings in the city")
    public Map<ResourceType, Double> getBuildingCost() {
        double[] costArr = ResourceType.getBuffer();
        for (Building building : Buildings.values()) {
            int amt = getBuilding(building);
            if (amt == 0) continue;
            building.cost(costArr, amt);
        }
        return ResourceType.resourcesToMap(costArr);
    }

    @Command(desc = "Get the market cost of buildings in the city")
    public double getBuildingMarketCost() {
        double cost = 0;
        for (Building building : Buildings.values()) {
            int amt = getBuilding(building);
            if (amt == 0) continue;
            cost += building.getNMarketCost(amt);
        }
        return cost;
    }

    @Command(desc = "Get the crime in the city")
    public double getCrime() {
        return calcCrime(getProjectPredicate());
    }

    @Override
    public double calcCrime(Predicate<Project> hasProject) {
        int commerce = PW.City.getCommerce(hasProject, this::getBuilding);
        return PW.City.getCrime(hasProject, this::getBuilding, getInfra_cents(), commerce);
    }

    @Command(desc = "Get the number of free infrastructure points in the city")
    public double getFreeInfra() {
        return getInfra() - getRequiredInfra();
    }
    @Command(desc = "Get the number of free building slots in the city")
    public int getFreeSlots() {
        return (int) (getFreeInfra() / 50);
    }

    @Command(desc = "Get the disease in the city")
    public double getDisease() {
        return calcDisease(getProjectPredicate());
    }

    @Override
    public double calcDisease(Predicate<Project> hasProject) {
        double pollution = PW.City.getPollution(hasProject, this::getBuilding, PW.City.getNukePollution(getNuke_turn()));
        return PW.City.getDisease(hasProject, this::getBuilding, getInfra_cents(), getLand_cents(), pollution);
    }

    @Command(desc = "Get the population of the city")
    public int getPopulation() {
        return calcPopulation(getProjectPredicate());
    }

    @Override
    public int calcPopulation(Predicate<Project> hasProject) {
        int pollution = PW.City.getPollution(hasProject, this::getBuilding, PW.City.getNukePollution(getNuke_turn()));
        double disease = PW.City.getDisease(hasProject, this::getBuilding, getInfra_cents(), getLand_cents(), pollution);
        int commerce = PW.City.getCommerce(hasProject, this::getBuilding);
        double crime = PW.City.getCrime(hasProject, this::getBuilding, getInfra_cents(), commerce);
        int ageDays = getAgeDays();
        return PW.City.getPopulation(getInfra_cents(), crime, disease, ageDays);
    }

    @Command(desc = "Get the amount of infrastructure powered by buildings")
    public int getPoweredInfra() {
        int powered = 0;
        powered += getBuilding(Buildings.WIND_POWER) * Buildings.WIND_POWER.getInfraMax();
        powered += getBuilding(Buildings.COAL_POWER) * Buildings.COAL_POWER.getInfraMax();
        powered += getBuilding(Buildings.OIL_POWER) * Buildings.OIL_POWER.getInfraMax();
        powered += getBuilding(Buildings.NUCLEAR_POWER) * Buildings.NUCLEAR_POWER.getInfraMax();

        return powered;
    }

    @Command(desc = "Get the profit of the city")
    public Map<ResourceType, Double> getRevenue() {
        double[] profitBuffer = ResourceType.getBuffer();

        DBNation nation = getNation();

        Continent continent = nation == null ? Continent.NORTH_AMERICA : nation.getContinent();
        double rads = nation == null ? Locutus.imp().getTradeManager().getGlobalRadiation() : nation.getRads();
        long date = System.currentTimeMillis();
        Predicate<Project> hasProject = nation == null ? f -> false : nation::hasProject;
        int numCities = nation == null ? 21 : nation.getCities();
        double grossModifier = nation == null ? 1 : nation.getGrossModifier();
        boolean forceUnpowered = false;
        int turns = 12;
        double[] profit = PW.City.profit(continent, rads, date, hasProject, profitBuffer, numCities, grossModifier, forceUnpowered, turns, this);
        return ResourceType.resourcesToMap(profit);
    }

    @Command(desc = "Get the monetary value of the cities income")
    public double getRevenueValue() {
        DBNation nation = getNation();
        Continent continent = nation == null ? Continent.NORTH_AMERICA : nation.getContinent();
        double rads = nation == null ? Locutus.imp().getTradeManager().getGlobalRadiation() : nation.getRads();
        Predicate<Project> hasProject = nation == null ? f -> false : nation::hasProject;
        int numCities = nation == null ? 21 : nation.getCities();
        double grossModifier = nation == null ? 1 : nation.getGrossModifier();
        return PW.City.profitConverted(continent, rads, hasProject, numCities, grossModifier, this);
    }

    public abstract void setPowered(boolean powered);

    public abstract void setId(int id);

    public abstract void setNation_id(int nation_id);

    @Command(desc = "Get city ID")
    public abstract int getId();

    public abstract int getNation_id();

    public abstract long getCreated();

    public abstract void setCreated(long created);

    public abstract long getFetched();

    public abstract void setFetched(long fetched);

    public abstract int getLand_cents();

    public abstract void setLand_cents(int land_cents);

    public abstract int getInfra_cents();

    public abstract void setInfra_cents(int infra_cents);

    public abstract boolean isPowered();

    public abstract byte[] getBuildings3();

    public abstract void setBuildings3(byte[] buildings3);

    public abstract int getNuke_turn();

    public abstract void setNuke_turn(int nuke_turn);
}
