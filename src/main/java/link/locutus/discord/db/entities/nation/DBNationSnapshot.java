package link.locutus.discord.db.entities.nation;

import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.column.ProjectColumn;
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

import static link.locutus.discord.apiv1.core.Utility.unsupported;

public class DBNationSnapshot extends DBNation implements DBNationGetter {
    private DataWrapper<NationHeader> wrapper;
    private final int offset;
    // cache data??
//    private Object[] cache;

    public DBNationSnapshot(DataWrapper<NationHeader> wrapper, int offset) {
        this.wrapper = wrapper;
        this.offset = offset;
    }

    public int getOffset() {
        return offset;
    }

    public DataWrapper<NationHeader> getWrapper() {
        return wrapper;
    }

    @Override
    public long _enteredVm() {
        return 0;
    }

    @Override
    public long _leavingVm() {
        int vmTurns = wrapper.get(wrapper.header.vm_turns, offset);
        if (vmTurns > 0) return TimeUtil.getTurn(wrapper.date) + vmTurns;
        return 0;
    }

    @Override
    public DBNationGetter data() {
        return this;
    }

    @Override
    public DBNationSetter edit() {
        throw unsupported();
    }

    @Override
    public DBNation copy() {
        throw unsupported();
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
        return wrapper.get(wrapper.header.projects, offset);
    }

    @Override
    public Long getSnapshot() {
        return wrapper.date;
    }

    public boolean hasCityData() {
        return this.wrapper.getGetCities() != null;
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
        Function<Integer, Map<Integer, DBCity>> getCities = wrapper.getGetCities();
        if (getCities != null) {
            return getCities.apply(getNation_id());
        }
        Map<Integer, DBCity> cities = super._getCitiesV3();
        return cities.entrySet().stream().filter(e -> e.getValue().getCreated() <= wrapper.date).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
    public int getVm_turns() {
        return wrapper.get(wrapper.header.vm_turns, offset);
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
        return wrapper.get(wrapper.header.nation_id, offset);
    }

    @Override
    public String _nation() {
        return wrapper.get(wrapper.header.nation_name, offset);
    }

    @Override
    public String _leader() {
        return wrapper.get(wrapper.header.leader_name, offset);
    }

    @Override
    public int _allianceId() {
        return wrapper.get(wrapper.header.alliance_id, offset);
    }

    @Override
    public double _score() {
        return wrapper.get(wrapper.header.score, offset);
    }

    @Override
    public int _cities() {
        return wrapper.get(wrapper.header.cities, offset);
    }

    @Override
    public DomesticPolicy _domesticPolicy() {
        return wrapper.get(wrapper.header.domestic_policy, offset);
    }

    @Override
    public WarPolicy _warPolicy() {
        return wrapper.get(wrapper.header.war_policy, offset);
    }

    @Override
    public int _soldiers() {
        return wrapper.get(wrapper.header.soldiers, offset);
    }

    @Override
    public int _tanks() {
        return wrapper.get(wrapper.header.tanks, offset);
    }

    @Override
    public int _aircraft() {
        return wrapper.get(wrapper.header.aircraft, offset);
    }

    @Override
    public int _ships() {
        return wrapper.get(wrapper.header.ships, offset);
    }

    @Override
    public int _missiles() {
        return wrapper.get(wrapper.header.missiles, offset);
    }

    @Override
    public int _nukes() {
        return wrapper.get(wrapper.header.nukes, offset);
    }

    @Override
    public int _spies() {
        return 0;
    }

    @Override
    public NationColor _color() {
        return wrapper.get(wrapper.header.color, offset);
    }

    @Override
    public long _date() {
        return wrapper.get(wrapper.header.date_created, offset);
    }

    @Override
    public int _alliancePosition() {
        return 0;
    }

    @Override
    public Rank _rank() {
        return wrapper.get(wrapper.header.alliance_position, offset);
    }

    @Override
    public Continent _continent() {
        return wrapper.get(wrapper.header.continent, offset);
    }

    @Override
    public long _cityTimer() {
        throw unsupported();
    }

    @Override
    public long _projectTimer() {
        throw unsupported();
    }

    @Override
    public long _beigeTimer() {
        int turns = wrapper.get(wrapper.header.beige_turns_remaining, offset);
        return TimeUtil.getTurn(wrapper.date) + turns;
    }

    @Override
    public long _warPolicyTimer() {
        throw unsupported();
    }

    @Override
    public long _domesticPolicyTimer() {
        throw unsupported();
    }

    @Override
    public long _colorTimer() {
        throw unsupported();
    }

    @Override
    public long _espionageFull() {
        throw unsupported();
    }

    @Override
    public int _dcTurn() {
        throw unsupported();
    }

    @Override
    public int _warsWon() {
        return wrapper.get(wrapper.header.wars_won, offset);
    }

    @Override
    public int _warsLost() {
        return wrapper.get(wrapper.header.wars_lost, offset);
    }

    @Override
    public int _taxId() {
        return 0;
    }

    @Override
    public double _gni() {
        throw unsupported();
    }

    @Override
    public DBNationCache _cache() {
        return null;
    }
}
