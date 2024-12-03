package link.locutus.discord.db.entities.nation;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.column.IntColumn;
import link.locutus.discord.apiv3.csv.column.ProjectColumn;
import link.locutus.discord.apiv3.csv.file.NationsFile;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.util.TimeUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DBNationSnapshot extends DBNation implements DBNationGetter{
    private SnapshotDataWrapper wrapper;
    private final int offset;
    // cache data??
//    private Object[] cache;

    public DBNationSnapshot(SnapshotDataWrapper wrapper, int offset) {
        this.wrapper = wrapper;
        this.offset = offset;
    }

    public void setSnapshotDate(long snapshotDate) {
        this.wrapper = new SnapshotDataWrapper(snapshotDate, wrapper.header, wrapper.data, wrapper.getCities);
    }

    @Override
    public DBNationGetter data() {
        return this;
    }

    @Override
    public DBNationSetter edit() {
        unsupported();
        return null;
    }

    @Override
    public DBNation copy() {
        unsupported();
        return null;
    }

    private <T> T get(ColumnInfo<DBNation, T> get) {
        try {
            return get.read(this.wrapper.data, get.getOffset() + this.offset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static BiPredicate<Project, NationHeader> hasProject = new BiPredicate<Project, NationHeader>() {
        Function<NationHeader, Boolean>[] byProject;

        private void set(NationHeader header, Function<NationHeader, ProjectColumn> supplier) {
            ProjectColumn col = supplier.apply(header);
            byProject[col.getProject().ordinal()] = f -> supplier.apply(f).get();
        }

        @Override
        public boolean test(Project project, NationHeader header) {
            if (byProject == null) {
                byProject = new Function[Arrays.stream(Projects.values).mapToInt(Project::ordinal).max().orElse(0) + 1];
                set(header, f -> f.ironworks_np);
                set(header, f -> f.bauxiteworks_np);
                set(header, f -> f.arms_stockpile_np);
                set(header, f -> f.emergency_gasoline_reserve_np);
                set(header, f -> f.mass_irrigation_np);
                set(header, f -> f.international_trade_center_np);
                set(header, f -> f.missile_launch_pad_np);
                set(header, f -> f.nuclear_research_facility_np);
                set(header, f -> f.iron_dome_np);
                set(header, f -> f.vital_defense_system_np);
                set(header, f -> f.intelligence_agency_np);
                set(header, f -> f.center_for_civil_engineering_np);
                set(header, f -> f.propaganda_bureau_np);
                set(header, f -> f.uranium_enrichment_program_np);
                set(header, f -> f.urban_planning_np);
                set(header, f -> f.advanced_urban_planning_np);
                set(header, f -> f.space_program_np);
                set(header, f -> f.moon_landing_np);
                set(header, f -> f.spy_satellite_np);
                set(header, f -> f.pirate_economy_np);
                set(header, f -> f.recycling_initiative_np);
                set(header, f -> f.telecommunications_satellite_np);
                set(header, f -> f.green_technologies_np);
                set(header, f -> f.clinical_research_center_np);
                set(header, f -> f.specialized_police_training_program_np);
                set(header, f -> f.arable_land_agency_np);
                set(header, f -> f.advanced_engineering_corps_np);
                set(header, f -> f.government_support_agency_np);
                set(header, f -> f.research_and_development_center_np);
                set(header, f -> f.resource_production_center_np);
                set(header, f -> f.metropolitan_planning_np);
                set(header, f -> f.military_salvage_np);
                set(header, f -> f.fallout_shelter_np);
                set(header, f -> f.advanced_pirate_economy_np);
                set(header, f -> f.bureau_of_domestic_affairs_np);
                set(header, f -> f.mars_landing_np);
                set(header, f -> f.surveillance_network_np);
                set(header, f -> f.guiding_satellite_np);
                set(header, f -> f.nuclear_launch_facility_np);
                hasProject = (a, b) -> byProject[a.ordinal()].apply(b);
            }
            return byProject[project.ordinal()].apply(header);
        }
    };

    @Override
    public boolean hasProject(Project project) {
        return hasProject.test(project, wrapper.header);
    }

    @Override
    public long _projects() {
        long bits = 0;
        for (Project project : Projects.values) {
            if (hasProject(project)) {
                bits |= 1L << project.ordinal();
            }
        }
        return bits;
    }


    @Override
    public int getNumProjects() {
        return get(wrapper.header.projects);
    }

    @Override
    public Long getSnapshot() {
        return wrapper.date;
    }

    public boolean hasCityData() {
        return this.wrapper.getCities != null;
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
        if (wrapper.getCities != null) {
            return wrapper.getCities.apply(getNation_id());
        }
        Map<Integer, DBCity> cities = super._getCitiesV3();
        return cities.entrySet().stream().filter(e -> e.getValue().created <= wrapper.date).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
    public long _lastActiveMs() {
        return lastActiveMs(wrapper.date);
    }

    @Override
    public long _enteredVm() {
        return 0;
    }

    @Override
    public long _leavingVm() {
        int vmTurns = get(wrapper.header.vm_turns);
        if (vmTurns > 0) return TimeUtil.getTurn(wrapper.date) + vmTurns;
        return 0;
    }

    @Override
    public int getVm_turns() {
        return get(wrapper.header.vm_turns);
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

    @Override
    public int _nationId() {
        return get(wrapper.header.nation_id);
    }

    @Override
    public String _nation() {
        return get(wrapper.header.nation_name);
    }

    @Override
    public String _leader() {
        return get(wrapper.header.leader_name);
    }

    @Override
    public int _allianceId() {
        return get(wrapper.header.alliance_id);
    }

    @Override
    public double _score() {
        return get(wrapper.header.score);
    }

    @Override
    public int _cities() {
        return get(wrapper.header.cities);
    }

    @Override
    public DomesticPolicy _domesticPolicy() {
        return get(wrapper.header.domestic_policy);
    }

    @Override
    public WarPolicy _warPolicy() {
        return get(wrapper.header.war_policy);
    }

    @Override
    public int _soldiers() {
        return get(wrapper.header.soldiers);
    }

    @Override
    public int _tanks() {
        return get(wrapper.header.tanks);
    }

    @Override
    public int _aircraft() {
        return get(wrapper.header.aircraft);
    }

    @Override
    public int _ships() {
        return get(wrapper.header.ships);
    }

    @Override
    public int _missiles() {
        return get(wrapper.header.missiles);
    }

    @Override
    public int _nukes() {
        return get(wrapper.header.nukes);
    }

    @Override
    public int _spies() {
        return 0;
    }

    @Override
    public NationColor _color() {
        return get(wrapper.header.color);
    }

    @Override
    public long _date() {
        return get(wrapper.header.date_created);
    }

    @Override
    public int _alliancePosition() {
        return 0;
    }

    @Override
    public Rank _rank() {
        return get(wrapper.header.alliance_position);
    }

    @Override
    public Continent _continent() {
        return get(wrapper.header.continent);
    }

    @Override
    public long _cityTimer() {
        unsupported();
        return 0;
    }

    @Override
    public long _projectTimer() {
        unsupported();
        return 0;
    }

    @Override
    public long _beigeTimer() {
        int turns = get(wrapper.header.beige_turns_remaining);
        return TimeUtil.getTurn(wrapper.date) + turns;
    }

    @Override
    public long _warPolicyTimer() {
        unsupported();
        return 0;
    }

    @Override
    public long _domesticPolicyTimer() {
        unsupported();
        return 0;
    }

    @Override
    public long _colorTimer() {
        unsupported();
        return 0;
    }

    @Override
    public long _espionageFull() {
        unsupported();
        return 0;
    }

    @Override
    public int _dcTurn() {
        unsupported();
        return 0;
    }

    @Override
    public int _warsWon() {
        return get(wrapper.header.wars_won);
    }

    @Override
    public int _warsLost() {
        return get(wrapper.header.wars_lost);
    }

    @Override
    public int _taxId() {
        return 0;
    }

    @Override
    public double _gni() {
        unsupported();
        return 0;
    }

    @Override
    public DBNationCache _cache() {
        return null;
    }
}
