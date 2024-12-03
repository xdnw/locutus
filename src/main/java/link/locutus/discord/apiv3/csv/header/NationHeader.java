package link.locutus.discord.apiv3.csv.header;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.csv.column.*;
import link.locutus.discord.apiv3.csv.file.Dictionary;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;

import java.text.ParseException;
import java.util.function.Predicate;

public class NationHeader extends DataHeader<DBNation> {


    public final IntColumn<DBNation> nation_id = new IntColumn<>(this, DBNation::setNation_id);
    public final StringColumn<DBNation> nation_name = new StringColumn<>(this, DBNation::setNation);
    public final StringColumn<DBNation> leader_name = new StringColumn<>(this, DBNation::setLeader);
    public final LongColumn<DBNation> date_created = new LongColumn<>(this, DBNation::setDate) {
        @Override
        public Long read(String string) {
            try {
                return TimeUtil.YYYY_MM_DD_HH_MM_SS.parse(string).toInstant().toEpochMilli();
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
    };
//    public int continent;
    public final EnumColumn<DBNation, Continent> continent = new EnumColumn<>(this, Continent.class, DBNation::setContinent, Continent::parseV3);
//    public int latitude;
    public final DoubleColumn<DBNation> latitude = new DoubleColumn<>(this, null);
//    public int longitude;
    public final DoubleColumn<DBNation> longitude = new DoubleColumn<>(this, null);
//    public int leader_title;
    public final StringColumn<DBNation> leader_title = new StringColumn<>(this, null);
//    public int nation_title;
    public final StringColumn<DBNation> nation_title = new StringColumn<>(this, null);
//    public int score;
    public final DoubleColumn<DBNation> score = new DoubleColumn<>(this, DBNation::setScore);
//    public int population;
    public final IntColumn<DBNation> population = new IntColumn<>(this, null);
//    public int flag_url;
    public final StringColumn<DBNation> flag_url = new StringColumn<>(this, null);
//    public int color; // enum NationColor
    public final EnumColumn<DBNation, NationColor> color = new EnumColumn<>(this, NationColor.class, DBNation::setColor);
//    public int beige_turns_remaining; // int
    public final IntColumn<DBNation> beige_turns_remaining = new IntColumn<>(this, (nation, turns) -> {
        if (turns > 0) {
            long turn = TimeUtil.getTurn(nation.getSnapshot());
            nation.setBeigeTimer(turn + turns);
        }
});
//    public int portrait_url; // string ignore
    public final StringColumn<DBNation> portrait_url = new StringColumn<>(this, null);
//    public int cities; // int
    public final IntColumn<DBNation> cities = new IntColumn<>(this, DBNation::setCities);
//    public int gdp; // long ignore
    public final LongColumn<DBNation> gdp = new LongColumn<>(this, null);
//    public int currency; // string ignore
    public final StringColumn<DBNation> currency = new StringColumn<>(this, null);
//    public int wars_won; // int
    public final IntColumn<DBNation> wars_won = new IntColumn<>(this, DBNation::setWars_won);
//    public int wars_lost; // int
    public final IntColumn<DBNation> wars_lost = new IntColumn<>(this, DBNation::setWars_lost);
//    public int alliance; // string
    public final StringColumn<DBNation> alliance = new StringColumn<>(this, null);
//    public int alliance_id; // int
    public final IntColumn<DBNation> alliance_id = new IntColumn<>(this, DBNation::setAlliance_id);
//    public int alliance_position; // int
    public final EnumColumn<DBNation, Rank> alliance_position = new EnumColumn<>(this, Rank.class, DBNation::setPosition, f -> Rank.byId(Integer.parseInt(f)));
//    public int soldiers; // int
    public final IntColumn<DBNation> soldiers = new IntColumn<>(this, DBNation::setSoldiers);
//    public int tanks; // int
    public final IntColumn<DBNation> tanks = new IntColumn<>(this, DBNation::setTanks);
//    public int aircraft; // int
    public final IntColumn<DBNation> aircraft = new IntColumn<>(this, DBNation::setAircraft);
//    public int ships; // int
    public final IntColumn<DBNation> ships = new IntColumn<>(this, DBNation::setShips);
//    public int missiles; // int
    public final IntColumn<DBNation> missiles = new IntColumn<>(this, DBNation::setMissiles);
//    public int nukes; // int
    public final IntColumn<DBNation> nukes = new IntColumn<>(this, DBNation::setNukes);
//    public int domestic_policy; // enum DomesticPolicy
    public final EnumColumn<DBNation, DomesticPolicy> domestic_policy = new EnumColumn<>(this, DomesticPolicy.class, DBNation::setDomesticPolicy, DomesticPolicy::parse);
//    public int war_policy; // enum WarPolicy
    public final EnumColumn<DBNation, WarPolicy> war_policy = new EnumColumn<>(this, WarPolicy.class, DBNation::setWarPolicy, WarPolicy::parse);
//    public int projects; // int
    public final IntColumn<DBNation> projects = new IntColumn<>(this, null);
    //    public int vm_turns;
    public final IntColumn<DBNation> vm_turns = new IntColumn<>(this, (nation, turns) -> {
        if (turns > 0) {
            long turn = TimeUtil.getTurn(nation.getSnapshot());
            nation.setLeaving_vm(turn + turns);
        }
    });
    public final ProjectColumn ironworks_np = new ProjectColumn(this, Projects.IRON_WORKS);
    public final ProjectColumn bauxiteworks_np = new ProjectColumn(this, Projects.BAUXITEWORKS);
    public final ProjectColumn arms_stockpile_np = new ProjectColumn(this, Projects.ARMS_STOCKPILE);
    public final ProjectColumn emergency_gasoline_reserve_np = new ProjectColumn(this, Projects.EMERGENCY_GASOLINE_RESERVE);
    public final ProjectColumn mass_irrigation_np = new ProjectColumn(this, Projects.MASS_IRRIGATION);
    public final ProjectColumn international_trade_center_np = new ProjectColumn(this, Projects.INTERNATIONAL_TRADE_CENTER);
    public final ProjectColumn missile_launch_pad_np = new ProjectColumn(this, Projects.MISSILE_LAUNCH_PAD);
    public final ProjectColumn nuclear_research_facility_np = new ProjectColumn(this, Projects.NUCLEAR_RESEARCH_FACILITY);
    public final ProjectColumn iron_dome_np = new ProjectColumn(this, Projects.IRON_DOME);
    public final ProjectColumn vital_defense_system_np = new ProjectColumn(this, Projects.VITAL_DEFENSE_SYSTEM);
    public final ProjectColumn intelligence_agency_np = new ProjectColumn(this, Projects.INTELLIGENCE_AGENCY);
    public final ProjectColumn center_for_civil_engineering_np = new ProjectColumn(this, Projects.CENTER_FOR_CIVIL_ENGINEERING);
    public final ProjectColumn propaganda_bureau_np = new ProjectColumn(this, Projects.PROPAGANDA_BUREAU);
    public final ProjectColumn uranium_enrichment_program_np = new ProjectColumn(this, Projects.URANIUM_ENRICHMENT_PROGRAM);
    public final ProjectColumn urban_planning_np = new ProjectColumn(this, Projects.URBAN_PLANNING);
    public final ProjectColumn advanced_urban_planning_np = new ProjectColumn(this, Projects.ADVANCED_URBAN_PLANNING);
    public final ProjectColumn space_program_np = new ProjectColumn(this, Projects.SPACE_PROGRAM);
    public final ProjectColumn moon_landing_np = new ProjectColumn(this, Projects.MOON_LANDING);
    public final ProjectColumn spy_satellite_np = new ProjectColumn(this, Projects.SPY_SATELLITE);
    public final ProjectColumn pirate_economy_np = new ProjectColumn(this, Projects.PIRATE_ECONOMY);
    public final ProjectColumn recycling_initiative_np = new ProjectColumn(this, Projects.RECYCLING_INITIATIVE);
    public final ProjectColumn telecommunications_satellite_np = new ProjectColumn(this, Projects.TELECOMMUNICATIONS_SATELLITE);
    public final ProjectColumn green_technologies_np = new ProjectColumn(this, Projects.GREEN_TECHNOLOGIES);
    public final ProjectColumn clinical_research_center_np = new ProjectColumn(this, Projects.CLINICAL_RESEARCH_CENTER);
    public final ProjectColumn specialized_police_training_program_np = new ProjectColumn(this, Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM);
    public final ProjectColumn arable_land_agency_np = new ProjectColumn(this, Projects.ARABLE_LAND_AGENCY);
    public final ProjectColumn advanced_engineering_corps_np = new ProjectColumn(this, Projects.ADVANCED_ENGINEERING_CORPS);
    public final ProjectColumn government_support_agency_np = new ProjectColumn(this, Projects.GOVERNMENT_SUPPORT_AGENCY);
    public final ProjectColumn research_and_development_center_np = new ProjectColumn(this, Projects.RESEARCH_AND_DEVELOPMENT_CENTER);
    public final ProjectColumn resource_production_center_np = new ProjectColumn(this, Projects.ACTIVITY_CENTER);
    public final ProjectColumn metropolitan_planning_np = new ProjectColumn(this, Projects.METROPOLITAN_PLANNING);
    public final ProjectColumn military_salvage_np = new ProjectColumn(this, Projects.MILITARY_SALVAGE);
    public final ProjectColumn fallout_shelter_np = new ProjectColumn(this, Projects.FALLOUT_SHELTER);
    public final ProjectColumn advanced_pirate_economy_np = new ProjectColumn(this, Projects.ADVANCED_PIRATE_ECONOMY);
    public final ProjectColumn bureau_of_domestic_affairs_np = new ProjectColumn(this, Projects.BUREAU_OF_DOMESTIC_AFFAIRS);
    public final ProjectColumn mars_landing_np = new ProjectColumn(this, Projects.MARS_LANDING);
    public final ProjectColumn surveillance_network_np = new ProjectColumn(this, Projects.SURVEILLANCE_NETWORK);
    public final ProjectColumn guiding_satellite_np = new ProjectColumn(this, Projects.GUIDING_SATELLITE);
    public final ProjectColumn nuclear_launch_facility_np = new ProjectColumn(this, Projects.NUCLEAR_LAUNCH_FACILITY);

    private DBNationSnapshot cached;
    private int nationLoaded;
    private static final int LOADED = 1;
    private static final int ALLOW_VM = 2;
    private static final int ALLOW_DELETED = 4;
    private static final Predicate<Integer> ALLOW_ALL = b -> true;

    public NationHeader(Dictionary dict) {
        super(dict);
    }

    @Override
    public void clear() {
        cached = null;
        nationLoaded = 0;
    }

    public DBNationSnapshot getNation(boolean allowVm, boolean allowDeleted) {
        int nationId = this.nation_id.get();
        if (cached != null) {
            if (cached.getId() == nationId) {
                if (!allowVm && (nationLoaded & ALLOW_VM) != 0 && cached.getVm_turns() > 0) return null;
                if (!allowDeleted && (nationLoaded & ALLOW_DELETED) != 0 && !cached.isValid()) return null;
                return cached;
            }
            cached = null;
            nationLoaded = 0;
        }
        if ((nationLoaded & LOADED) != 0 && (!allowVm || (nationLoaded & ALLOW_VM) == 0) && (!allowDeleted || (nationLoaded & ALLOW_DELETED) == 0)) return null;
        nationLoaded |= LOADED | (allowVm ? ALLOW_VM : 0) | (allowDeleted ? ALLOW_DELETED : 0);
        return cached = loadNationUnchecked(nationId, ALLOW_ALL, ALLOW_ALL, allowVm, allowVm, allowDeleted);
    }

    public DBNationSnapshot getNation(Predicate<Integer> allowedNationIds, Predicate<Integer> allowedAllianceIds, boolean allowVm, boolean allowNoVmCol, boolean allowDeleted) {
        int nationId = this.nation_id.get();
        if (cached != null) {
            if (cached.getId() == nationId) {
                if (!allowVm && (nationLoaded & ALLOW_VM) != 0 && cached.getVm_turns() > 0) return null;
                if (!allowDeleted && (nationLoaded & ALLOW_DELETED) != 0 && !cached.isValid()) return null;
                if (!allowedNationIds.test(nationId)) return null;
                if (!allowedAllianceIds.test(cached.getAlliance_id())) return null;
                return cached;
            }
            cached = null;
            nationLoaded = 0;
        }
        if ((nationLoaded & LOADED) != 0 && (!allowVm || (nationLoaded & ALLOW_VM) == 0) && (!allowDeleted || (nationLoaded & ALLOW_DELETED) == 0)) return null;
        nationLoaded = LOADED | (allowVm ? ALLOW_VM : 0) | (allowDeleted ? ALLOW_DELETED : 0);
        return cached = loadNationUnchecked(nationId, allowedNationIds, allowedAllianceIds, allowVm, allowNoVmCol, allowDeleted);
    }

    private DBNationSnapshot loadNationUnchecked(int nationId, Predicate<Integer> allowedNationIds, Predicate<Integer> allowedAllianceIds, boolean allowVm, boolean allowNoVmCol, boolean allowDeleted) {
        if (!allowedNationIds.test(nationId)) return null;
        Integer vm_turns = this.vm_turns.get();
        if (vm_turns != null) {
            if (vm_turns > 0 && !allowVm) {
                return null;
            }
        } else {
            if (!allowNoVmCol) {
                return null;
            }
            vm_turns = 0;
        }
        int aaId = this.alliance_id.get();
        if (!allowedAllianceIds.test(aaId)) return null;
        if (!allowDeleted) {
            DBNation existing = DBNation.getById(nationId);
            if (existing == null) {
                return null;
            }
        }
        DBNationSnapshot nation = new DBNationSnapshot(getDate());
        this.date_created.set(nation);
        if (vm_turns > 0) {
            nation.setLeaving_vm(TimeUtil.getTurn(getDate()) + vm_turns);
        }
        nation.setAlliance_id(aaId);
        this.nation_id.set(nation);
        this.nation_name.set(nation);
        this.continent.set(nation);
        this.color.set(nation);
        this.alliance_position.set(nation);
        this.soldiers.set(nation);
        this.tanks.set(nation);
        this.aircraft.set(nation);
        this.ships.set(nation);
        this.missiles.set(nation);
        this.nukes.set(nation);
        this.domestic_policy.set(nation);
        this.war_policy.set(nation);
        this.cities.set(nation);
        this.score.set(nation);
        this.wars_won.set(nation);
        this.wars_lost.set(nation);

        setProject(nation, this.ironworks_np);
        setProject(nation, this.bauxiteworks_np);
        setProject(nation, this.arms_stockpile_np);
        setProject(nation, this.emergency_gasoline_reserve_np);
        setProject(nation, this.mass_irrigation_np);
        setProject(nation, this.international_trade_center_np);
        setProject(nation, this.missile_launch_pad_np);
        setProject(nation, this.nuclear_research_facility_np);
        setProject(nation, this.iron_dome_np);
        setProject(nation, this.vital_defense_system_np);
        setProject(nation, this.intelligence_agency_np);
        setProject(nation, this.center_for_civil_engineering_np);
        setProject(nation, this.propaganda_bureau_np);
        setProject(nation, this.uranium_enrichment_program_np);
        setProject(nation, this.urban_planning_np);
        setProject(nation, this.advanced_urban_planning_np);
        setProject(nation, this.space_program_np);
        setProject(nation, this.moon_landing_np);
        setProject(nation, this.spy_satellite_np);
        setProject(nation, this.pirate_economy_np);
        setProject(nation, this.recycling_initiative_np);
        setProject(nation, this.telecommunications_satellite_np);
        setProject(nation, this.green_technologies_np);
        setProject(nation, this.clinical_research_center_np);
        setProject(nation, this.specialized_police_training_program_np);
        setProject(nation, this.arable_land_agency_np);
        setProject(nation, this.advanced_engineering_corps_np);
        setProject(nation, this.government_support_agency_np);
        setProject(nation, this.research_and_development_center_np);
        setProject(nation, this.resource_production_center_np);
        setProject(nation, this.metropolitan_planning_np);
        setProject(nation, this.military_salvage_np);
        setProject(nation, this.fallout_shelter_np);
        setProject(nation, this.advanced_pirate_economy_np);
        setProject(nation, this.bureau_of_domestic_affairs_np);
        setProject(nation, this.mars_landing_np);
        setProject(nation, this.surveillance_network_np);
        setProject(nation, this.guiding_satellite_np);
        setProject(nation, this.nuclear_launch_facility_np);
        return nation;
    }

    private void setProject(DBNation nation, BooleanColumn<DBNation> col) {
        if (col.get() == Boolean.TRUE) col.set(nation);
    }
}
