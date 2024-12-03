package link.locutus.discord.db.entities.nation;

import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBNationCache;
import link.locutus.discord.util.TimeUtil;

import java.util.Collection;

public class DBNationData implements DBNationSetter, DBNationGetter{
    private int nation_id;
    private String nation;
    private String leader;
    private int alliance_id;
    private long last_active;
    private double score;
    private int cities;
    private DomesticPolicy domestic_policy;
    private WarPolicy war_policy;
    private int soldiers;
    private int tanks;
    private int aircraft;
    private int ships;
    private int missiles;
    private int nukes;
    private int spies;
    private long entered_vm;
    private long leaving_vm;
    private NationColor color;
    private long date;
    private Rank rank;
    private int alliancePosition;
    private Continent continent;
    private long projects;
    private long cityTimer;
    private long projectTimer;
    private long beigeTimer;
    private long warPolicyTimer;
    private long domesticPolicyTimer;
    private long colorTimer;
    private long espionageFull;
    private int dc_turn = -1;

    private int wars_won;
    private int wars_lost;

    private int tax_id;
    private double gni;

    private transient DBNationCache cache;

    public DBNationData(int nation_id, String nation, String leader, int alliance_id, long last_active, double score,
                    int cities, DomesticPolicy domestic_policy, WarPolicy war_policy, int soldiers,
                    int tanks, int aircraft, int ships, int missiles, int nukes, int spies,
                    long entered_vm, long leaving_vm, NationColor color, long date,
                    Rank rank, int alliancePosition, Continent continent,
                    long projects, long cityTimer, long projectTimer,
                    long beigeTimer, long warPolicyTimer, long domesticPolicyTimer,
                    long colorTimer,
                    long espionageFull, int dc_turn, int wars_won, int wars_lost,
                    int tax_id,
                    double gni,
                    double gdp) {
        this.nation_id = nation_id;
        this.nation = nation;
        this.leader = leader;
        this.alliance_id = alliance_id;
        this.last_active = last_active;
        this.score = score;
        this.cities = cities;
        this.domestic_policy = domestic_policy;
        this.war_policy = war_policy;
        this.soldiers = soldiers;
        this.tanks = tanks;
        this.aircraft = aircraft;
        this.ships = ships;
        this.missiles = missiles;
        this.nukes = nukes;
        this.spies = spies;
        this.entered_vm = entered_vm;
        this.leaving_vm = leaving_vm;
        this.color = color;
        this.date = date;
        this.rank = rank;
        this.alliancePosition = alliancePosition;
        this.continent = continent;
        this.projects = projects;
        this.cityTimer = cityTimer;
        this.projectTimer = projectTimer;
        this.beigeTimer = beigeTimer;
        this.warPolicyTimer = warPolicyTimer;
        this.domesticPolicyTimer = domesticPolicyTimer;
        this.colorTimer = colorTimer;
        this.espionageFull = espionageFull;
        this.dc_turn = dc_turn;
        this.wars_won = wars_won;
        this.wars_lost = wars_lost;
        this.tax_id = tax_id;
        this.gni = gni;
    }

    public DBNationData() {
        projects = -1;
        beigeTimer = 0;
        war_policy = WarPolicy.TURTLE;
        domestic_policy = DomesticPolicy.MANIFEST_DESTINY;
        color = NationColor.BEIGE;
        cities = 1;
        date = System.currentTimeMillis();
        spies = -1;
        rank = Rank.BAN;
        continent = Continent.ANTARCTICA;
//        data().getLeaving_vm() = Long.MAX_VALUE;
    }

    public DBNationData(DBNationGetter other) {
        this.nation_id = other._nationId();
        this.nation = other._nation();
        this.leader = other._leader();
        this.alliance_id = other._allianceId();
        this.last_active = other._lastActiveMs();
        this.score = other._score();
        this.cities = other._cities();
        this.domestic_policy = other._domesticPolicy();
        this.war_policy = other._warPolicy();
        this.soldiers = other._soldiers();
        this.tanks = other._tanks();
        this.aircraft = other._aircraft();
        this.ships = other._ships();
        this.missiles = other._missiles();
        this.nukes = other._nukes();
        this.spies = other._spies();
        this.entered_vm = other._enteredVm();
        this.leaving_vm = other._leavingVm();
        this.color = other._color();
        this.date = other._date();
        this.rank = other._rank();
        this.alliancePosition = other._alliancePosition();
        this.continent = other._continent();
        this.projects = other._projects();
        this.cityTimer = other._cityTimer();
        this.projectTimer = other._projectTimer();
        this.beigeTimer = other._beigeTimer();
        this.warPolicyTimer = other._warPolicyTimer();
        this.domesticPolicyTimer = other._domesticPolicyTimer();
        this.colorTimer = other._colorTimer();
        this.espionageFull = other._espionageFull();
        this.dc_turn = other._dcTurn();
        this.wars_won = other._warsWon();
        this.wars_lost = other._warsLost();
        this.tax_id = other._taxId();
        this.gni = other._gni();
//        this.gdp = other.gdp;
    }

    public DBNationData(String coalition, Collection<DBNation> nations, boolean average) {
        this.nation_id = -1;
        this.nation = coalition;
        this.leader = null;
        this.alliance_id = 0;

        this.soldiers = 0;
        this.tanks = 0;
        this.aircraft = 0;
        this.ships = 0;
        this.missiles = 0;
        this.nukes = 0;
        this.spies = 0;
        this.date = 0L;

        int numDate = 0;

        for (DBNation other : nations) {
            this.projects |= other.getProjectBitMask();
            this.wars_won += other.getWars_won();
            this.wars_lost += other.getWars_lost();
            this.last_active += cast(other.lastActiveMs()).longValue();
            this.score += cast(other.getScore()).doubleValue();
            this.cities += cast(other.getCities()).intValue();
            this.soldiers += cast(other.getSoldiers()).intValue();
            this.tanks += cast(other.getTanks()).intValue();
            this.aircraft += cast(other.getAircraft()).intValue();
            this.ships += cast(other.getShips()).intValue();
            this.missiles += cast(other.getMissiles()).intValue();
            this.nukes += cast(other.getNukes()).intValue();
            if (other.getVm_turns() > 0) {
                setLeaving_vm(TimeUtil.getTurn() + other.getVm_turns());
            }
            this.spies += cast(other.getSpies()).intValue();
            this.wars_won += other.getWars_won();
            this.wars_lost += other.getWars_lost();
            if (other.getDate() != 0) {
                numDate++;
                this.date += cast(other.getDate()).longValue();
            }
        }
        if (average) {
            this.last_active /= nations.size();
            this.score /= nations.size();
            this.cities /= nations.size();
            this.soldiers /= nations.size();
            this.tanks /= nations.size();
            this.aircraft /= nations.size();
            this.ships /= nations.size();
            this.missiles /= nations.size();
            this.nukes /= nations.size();
//            this.money /= nations.size();
            this.spies /= nations.size();
            this.date /= numDate;
            this.wars_won /= nations.size();
            this.wars_lost /= nations.size();

        } else {
            long diffAvg = this.last_active / nations.size();
            last_active = System.currentTimeMillis() - ((System.currentTimeMillis() - diffAvg) * nations.size());
        }
    }

    private Number cast(Number t) {
        return t == null ? (Number) 0 : t;
    }

    // Getters and setters

    public int _nationId() {
        return nation_id;
    }

    public void setNation_id(int nation_id) {
        this.nation_id = nation_id;
    }

    public String _nation() {
        return nation;
    }

    public void setNation(String nation) {
        this.nation = nation;
    }

    public String _leader() {
        return leader;
    }

    public void setLeader(String leader) {
        this.leader = leader;
    }

    public int _allianceId() {
        return alliance_id;
    }

    public void setAlliance_id(int alliance_id) {
        this.alliance_id = alliance_id;
    }

    public long _lastActiveMs() {
        return last_active;
    }

    public void setLast_active(long last_active) {
        this.last_active = last_active;
    }

    public double _score() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public int _cities() {
        return cities;
    }

    public void setCities(int cities) {
        this.cities = cities;
    }

    public DomesticPolicy _domesticPolicy() {
        return domestic_policy;
    }

    public void setDomestic_policy(DomesticPolicy domestic_policy) {
        this.domestic_policy = domestic_policy;
    }

    public WarPolicy _warPolicy() {
        return war_policy;
    }

    public void setWar_policy(WarPolicy war_policy) {
        this.war_policy = war_policy;
    }

    public int _soldiers() {
        return soldiers;
    }

    public void setSoldiers(int soldiers) {
        this.soldiers = soldiers;
    }

    public int _tanks() {
        return tanks;
    }

    public void setTanks(int tanks) {
        this.tanks = tanks;
    }

    public int _aircraft() {
        return aircraft;
    }

    public void setAircraft(int aircraft) {
        this.aircraft = aircraft;
    }

    public int _ships() {
        return ships;
    }

    public void setShips(int ships) {
        this.ships = ships;
    }

    public int _missiles() {
        return missiles;
    }

    public void setMissiles(int missiles) {
        this.missiles = missiles;
    }

    public int _nukes() {
        return nukes;
    }

    public void setNukes(int nukes) {
        this.nukes = nukes;
    }

    public int _spies() {
        return spies;
    }

    public void setSpies(int spies) {
        this.spies = spies;
    }

    public long _enteredVm() {
        return entered_vm;
    }

    public void setEntered_vm(long entered_vm) {
        this.entered_vm = entered_vm;
    }

    public long _leavingVm() {
        return leaving_vm;
    }

    public void setLeaving_vm(long leaving_vm) {
        this.leaving_vm = leaving_vm;
    }

    public NationColor _color() {
        return color;
    }

    public void setColor(NationColor color) {
        this.color = color;
    }

    public long _date() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public Rank _rank() {
        return rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public int _alliancePosition() {
        return alliancePosition;
    }

    public void setAlliancePosition(int alliancePosition) {
        this.alliancePosition = alliancePosition;
    }

    public Continent _continent() {
        return continent;
    }

    public void setContinent(Continent continent) {
        this.continent = continent;
    }

    public long _projects() {
        return projects;
    }

    public void setProjects(long projects) {
        this.projects = projects;
    }

    public long _cityTimer() {
        return cityTimer;
    }

    public void setCityTimer(long cityTimer) {
        this.cityTimer = cityTimer;
    }

    public long _projectTimer() {
        return projectTimer;
    }

    public void setProjectTimer(long projectTimer) {
        this.projectTimer = projectTimer;
    }

    public long _beigeTimer() {
        return beigeTimer;
    }

    public void setBeigeTimer(long beigeTimer) {
        this.beigeTimer = beigeTimer;
    }

    public long _warPolicyTimer() {
        return warPolicyTimer;
    }

    public void setWarPolicyTimer(long warPolicyTimer) {
        this.warPolicyTimer = warPolicyTimer;
    }

    public long _domesticPolicyTimer() {
        return domesticPolicyTimer;
    }

    public void setDomesticPolicyTimer(long domesticPolicyTimer) {
        this.domesticPolicyTimer = domesticPolicyTimer;
    }

    public long _colorTimer() {
        return colorTimer;
    }

    public void setColorTimer(long colorTimer) {
        this.colorTimer = colorTimer;
    }

    public long _espionageFull() {
        return espionageFull;
    }

    public void setEspionageFull(long espionageFull) {
        this.espionageFull = espionageFull;
    }

    public int _dcTurn() {
        return dc_turn;
    }

    public void setDc_turn(int dc_turn) {
        this.dc_turn = dc_turn;
    }

    public int _warsWon() {
        return wars_won;
    }

    public void setWars_won(int wars_won) {
        this.wars_won = wars_won;
    }

    public int _warsLost() {
        return wars_lost;
    }

    public void setWars_lost(int wars_lost) {
        this.wars_lost = wars_lost;
    }

    public int _taxId() {
        return tax_id;
    }

    public void setTax_id(int tax_id) {
        this.tax_id = tax_id;
    }

    public double _gni() {
        return gni;
    }

    public void setGni(double gni) {
        this.gni = gni;
    }

    public DBNationCache _cache() {
        return cache;
    }

    public void setCache(DBNationCache cache) {
        this.cache = cache;
    }
}
