package com.boydti.discord.apiv1.domains.subdomains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class AllianceMembersContainer {

    @SerializedName("nationid")
    @Expose
    private Integer nationId;
    @SerializedName("nation")
    @Expose
    private String nation;
    @SerializedName("leader")
    @Expose
    private String leader;
    @SerializedName("war_policy")
    @Expose
    private String warPolicy;
    @SerializedName("color")
    @Expose
    private String color;
    @SerializedName("alliance")
    @Expose
    private String alliance;
    @SerializedName("allianceid")
    @Expose
    private Integer allianceId;
    @SerializedName("allianceposition")
    @Expose
    private Integer allianceposition;
    @SerializedName("cities")
    @Expose
    private Integer cities;
    @SerializedName("offensivewars")
    @Expose
    private Integer offensivewars;
    @SerializedName("defensivewars")
    @Expose
    private Integer defensivewars;
    @SerializedName("score")
    @Expose
    private String score;
    @SerializedName("vacmode")
    @Expose
    private String vacmode;
    @SerializedName("minutessinceactive")
    @Expose
    private Integer minutessinceactive;
    @SerializedName("infrastructure")
    @Expose
    private String infrastructure;
    @SerializedName("cityprojecttimerturns")
    @Expose
    private Integer cityprojecttimerturns;
    @SerializedName("bauxiteworks")
    @Expose
    private String bauxiteworks;
    @SerializedName("ironworks")
    @Expose
    private String ironworks;
    @SerializedName("armsstockpile")
    @Expose
    private String armsstockpile;
    @SerializedName("emgasreserve")
    @Expose
    private String emgasreserve;
    @SerializedName("massirrigation")
    @Expose
    private String massirrigation;
    @SerializedName("inttradecenter")
    @Expose
    private String inttradecenter;
    @SerializedName("missilepad")
    @Expose
    private String missilepad;
    @SerializedName("nuclearresfac")
    @Expose
    private String nuclearresfac;
    @SerializedName("irondome")
    @Expose
    private String irondome;
    @SerializedName("vitaldefsys")
    @Expose
    private String vitaldefsys;
    @SerializedName("intagncy")
    @Expose
    private String intagncy;
    @SerializedName("uraniumenrich")
    @Expose
    private String uraniumenrich;
    @SerializedName("propbureau")
    @Expose
    private String propbureau;
    @SerializedName("cenciveng")
    @Expose
    private String cenciveng;
    @SerializedName("money")
    @Expose
    private String money;
    @SerializedName("food")
    @Expose
    private String food;
    @SerializedName("coal")
    @Expose
    private String coal;
    @SerializedName("oil")
    @Expose
    private String oil;
    @SerializedName("uranium")
    @Expose
    private String uranium;
    @SerializedName("bauxite")
    @Expose
    private String bauxite;
    @SerializedName("iron")
    @Expose
    private String iron;
    @SerializedName("lead")
    @Expose
    private String lead;
    @SerializedName("gasoline")
    @Expose
    private String gasoline;
    @SerializedName("munitions")
    @Expose
    private String munitions;
    @SerializedName("aluminum")
    @Expose
    private String aluminum;
    @SerializedName("steel")
    @Expose
    private String steel;
    @SerializedName("credits")
    @Expose
    private String credits;
    @SerializedName("soldiers")
    @Expose
    private String soldiers;
    @SerializedName("tanks")
    @Expose
    private String tanks;
    @SerializedName("aircraft")
    @Expose
    private String aircraft;
    @SerializedName("ships")
    @Expose
    private String ships;
    @SerializedName("missiles")
    @Expose
    private String missiles;
    @SerializedName("nukes")
    @Expose
    private String nukes;
    @SerializedName("spies")
    @Expose
    private String spies;

    public Integer getNationId() {
        return nationId;
    }

    public String getNation() {
        return nation;
    }

    public String getLeader() {
        return leader;
    }

    public String getWarPolicy() {
        return warPolicy;
    }

    public String getColor() {
        return color;
    }

    public String getAlliance() {
        return alliance;
    }

    public Integer getAllianceId() {
        return allianceId;
    }

    public Integer getAllianceposition() {
        return allianceposition;
    }

    public Integer getCities() {
        return cities;
    }

    public Integer getOffensivewars() {
        return offensivewars;
    }

    public Integer getDefensivewars() {
        return defensivewars;
    }

    public String getScore() {
        return score;
    }

    public String getVacmode() {
        return vacmode;
    }

    public Integer getMinutessinceactive() {
        return minutessinceactive;
    }

    public String getInfrastructure() {
        return infrastructure;
    }

    public Integer getCityprojecttimerturns() {
        return cityprojecttimerturns;
    }

    public String getBauxiteworks() {
        return bauxiteworks;
    }

    public String getIronworks() {
        return ironworks;
    }

    public String getArmsstockpile() {
        return armsstockpile;
    }

    public String getEmgasreserve() {
        return emgasreserve;
    }

    public String getMassirrigation() {
        return massirrigation;
    }

    public String getInttradecenter() {
        return inttradecenter;
    }

    public String getMissilepad() {
        return missilepad;
    }

    public String getNuclearresfac() {
        return nuclearresfac;
    }

    public String getIrondome() {
        return irondome;
    }

    public String getVitaldefsys() {
        return vitaldefsys;
    }

    public String getIntagncy() {
        return intagncy;
    }

    public String getUraniumenrich() {
        return uraniumenrich;
    }

    public String getPropbureau() {
        return propbureau;
    }

    public String getCenciveng() {
        return cenciveng;
    }

    public String getMoney() {
        return money;
    }

    public String getFood() {
        return food;
    }

    public String getCoal() {
        return coal;
    }

    public String getOil() {
        return oil;
    }

    public String getUranium() {
        return uranium;
    }

    public String getBauxite() {
        return bauxite;
    }

    public String getIron() {
        return iron;
    }

    public String getLead() {
        return lead;
    }

    public String getGasoline() {
        return gasoline;
    }

    public String getMunitions() {
        return munitions;
    }

    public String getAluminum() {
        return aluminum;
    }

    public String getSteel() {
        return steel;
    }

    public String getCredits() {
        return credits;
    }

    public String getSoldiers() {
        return soldiers;
    }

    public String getTanks() {
        return tanks;
    }

    public String getAircraft() {
        return aircraft;
    }

    public String getShips() {
        return ships;
    }

    public String getMissiles() {
        return missiles;
    }

    public String getNukes() {
        return nukes;
    }

    public String getSpies() {
        return spies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AllianceMembersContainer that = (AllianceMembersContainer) o;
        return Objects.equals(nationId, that.nationId) &&
                Objects.equals(nation, that.nation) &&
                Objects.equals(leader, that.leader) &&
                Objects.equals(warPolicy, that.warPolicy) &&
                Objects.equals(color, that.color) &&
                Objects.equals(alliance, that.alliance) &&
                Objects.equals(allianceId, that.allianceId) &&
                Objects.equals(allianceposition, that.allianceposition) &&
                Objects.equals(cities, that.cities) &&
                Objects.equals(offensivewars, that.offensivewars) &&
                Objects.equals(defensivewars, that.defensivewars) &&
                Objects.equals(score, that.score) &&
                Objects.equals(vacmode, that.vacmode) &&
                Objects.equals(minutessinceactive, that.minutessinceactive) &&
                Objects.equals(infrastructure, that.infrastructure) &&
                Objects.equals(cityprojecttimerturns, that.cityprojecttimerturns) &&
                Objects.equals(bauxiteworks, that.bauxiteworks) &&
                Objects.equals(ironworks, that.ironworks) &&
                Objects.equals(armsstockpile, that.armsstockpile) &&
                Objects.equals(emgasreserve, that.emgasreserve) &&
                Objects.equals(massirrigation, that.massirrigation) &&
                Objects.equals(inttradecenter, that.inttradecenter) &&
                Objects.equals(missilepad, that.missilepad) &&
                Objects.equals(nuclearresfac, that.nuclearresfac) &&
                Objects.equals(irondome, that.irondome) &&
                Objects.equals(vitaldefsys, that.vitaldefsys) &&
                Objects.equals(intagncy, that.intagncy) &&
                Objects.equals(uraniumenrich, that.uraniumenrich) &&
                Objects.equals(propbureau, that.propbureau) &&
                Objects.equals(cenciveng, that.cenciveng) &&
                Objects.equals(money, that.money) &&
                Objects.equals(food, that.food) &&
                Objects.equals(coal, that.coal) &&
                Objects.equals(oil, that.oil) &&
                Objects.equals(uranium, that.uranium) &&
                Objects.equals(bauxite, that.bauxite) &&
                Objects.equals(iron, that.iron) &&
                Objects.equals(lead, that.lead) &&
                Objects.equals(gasoline, that.gasoline) &&
                Objects.equals(munitions, that.munitions) &&
                Objects.equals(aluminum, that.aluminum) &&
                Objects.equals(steel, that.steel) &&
                Objects.equals(credits, that.credits) &&
                Objects.equals(soldiers, that.soldiers) &&
                Objects.equals(tanks, that.tanks) &&
                Objects.equals(aircraft, that.aircraft) &&
                Objects.equals(ships, that.ships) &&
                Objects.equals(missiles, that.missiles) &&
                Objects.equals(nukes, that.nukes) &&
                Objects.equals(spies, that.spies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nationId, nation, leader, warPolicy, color, alliance, allianceId, allianceposition, cities, offensivewars, defensivewars, score, vacmode, minutessinceactive, infrastructure, cityprojecttimerturns, bauxiteworks, ironworks, armsstockpile, emgasreserve, massirrigation, inttradecenter, missilepad, nuclearresfac, irondome, vitaldefsys, intagncy, uraniumenrich, propbureau, cenciveng, money, food, coal, oil, uranium, bauxite, iron, lead, gasoline, munitions, aluminum, steel, credits, soldiers, tanks, aircraft, ships, missiles, nukes, spies);
    }

    @Override
    public String toString() {
        return "AllianceMembersContainer{" +
                "nationid=" + nationId +
                ", nation='" + nation + '\'' +
                ", leader='" + leader + '\'' +
                ", warPolicy='" + warPolicy + '\'' +
                ", color='" + color + '\'' +
                ", alliance='" + alliance + '\'' +
                ", allianceid=" + allianceId +
                ", allianceposition=" + allianceposition +
                ", cities=" + cities +
                ", offensivewars=" + offensivewars +
                ", defensivewars=" + defensivewars +
                ", score='" + score + '\'' +
                ", vacmode='" + vacmode + '\'' +
                ", minutessinceactive=" + minutessinceactive +
                ", infrastructure='" + infrastructure + '\'' +
                ", cityprojecttimerturns=" + cityprojecttimerturns +
                ", bauxiteworks='" + bauxiteworks + '\'' +
                ", ironworks='" + ironworks + '\'' +
                ", armsstockpile='" + armsstockpile + '\'' +
                ", emgasreserve='" + emgasreserve + '\'' +
                ", massirrigation='" + massirrigation + '\'' +
                ", inttradecenter='" + inttradecenter + '\'' +
                ", missilepad='" + missilepad + '\'' +
                ", nuclearresfac='" + nuclearresfac + '\'' +
                ", irondome='" + irondome + '\'' +
                ", vitaldefsys='" + vitaldefsys + '\'' +
                ", intagncy='" + intagncy + '\'' +
                ", uraniumenrich='" + uraniumenrich + '\'' +
                ", propbureau='" + propbureau + '\'' +
                ", cenciveng='" + cenciveng + '\'' +
                ", money='" + money + '\'' +
                ", food='" + food + '\'' +
                ", coal='" + coal + '\'' +
                ", oil='" + oil + '\'' +
                ", uranium='" + uranium + '\'' +
                ", bauxite='" + bauxite + '\'' +
                ", iron='" + iron + '\'' +
                ", lead='" + lead + '\'' +
                ", gasoline='" + gasoline + '\'' +
                ", munitions='" + munitions + '\'' +
                ", aluminum='" + aluminum + '\'' +
                ", steel='" + steel + '\'' +
                ", credits='" + credits + '\'' +
                ", soldiers='" + soldiers + '\'' +
                ", tanks='" + tanks + '\'' +
                ", aircraft='" + aircraft + '\'' +
                ", ships='" + ships + '\'' +
                ", missiles='" + missiles + '\'' +
                ", nukes='" + nukes + '\'' +
                ", spies='" + spies + '\'' +
                '}';
    }
}
