package link.locutus.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

public class Nation extends Entity {
  @SerializedName("cityids")
  @Expose
  private List<String> cityids = null;
  @SerializedName("cityprojecttimerturns")
  @Expose
  private int cityprojecttimerturns;


  @SerializedName("turns_since_last_city")
  @Expose
  private int turns_since_last_city;

  @SerializedName("turns_since_last_project")
  @Expose
  private int turns_since_last_project;

  @SerializedName("success")
  @Expose
  private boolean success;
  @SerializedName("nationid")
  @Expose
  private String nationid;
  @SerializedName("name")
  @Expose
  private String name;
  @SerializedName("prename")
  @Expose
  private String prename;
  @SerializedName("continent")
  @Expose
  private String continent;
  @SerializedName("socialpolicy")
  @Expose
  private String socialpolicy;
  @SerializedName("color")
  @Expose
  private String color;
  @SerializedName("minutessinceactive")
  @Expose
  private int minutessinceactive;
  @SerializedName("uniqueid")
  @Expose
  private String uniqueid;
  @SerializedName("government")
  @Expose
  private String government;
  @SerializedName("domestic_policy")
  @Expose
  private String domesticPolicy;
  @SerializedName("war_policy")
  @Expose
  private String warPolicy;
  @SerializedName("founded")
  @Expose
  private String founded;
  @SerializedName("daysold")
  @Expose
  private int daysold;
  @SerializedName("alliance")
  @Expose
  private String alliance;
  @SerializedName("allianceposition")
  @Expose
  private String allianceposition;
  @SerializedName("allianceid")
  @Expose
  private String allianceid;
  @SerializedName("flagurl")
  @Expose
  private String flagurl;
  @SerializedName("leadername")
  @Expose
  private String leadername;
  @SerializedName("title")
  @Expose
  private String title;
  @SerializedName("ecopolicy")
  @Expose
  private String ecopolicy;
  @SerializedName("approvalrating")
  @Expose
  private String approvalrating;
  @SerializedName("nationrank")
  @Expose
  private String nationrank;
  @SerializedName("nationrankstrings")
  @Expose
  private String nationrankstrings;
  @SerializedName("nrtotal")
  @Expose
  private int nrtotal;
  @SerializedName("cities")
  @Expose
  private int cities;
  @SerializedName("latitude")
  @Expose
  private String latitude;
  @SerializedName("longitude")
  @Expose
  private String longitude;
  @SerializedName("score")
  @Expose
  private String score;
  @SerializedName("population")
  @Expose
  private String population;
  @SerializedName("gdp")
  @Expose
  private String gdp;
  @SerializedName("totalinfrastructure")
  @Expose
  private double totalinfrastructure;
  @SerializedName("landarea")
  @Expose
  private double landarea;
  @SerializedName("soldiers")
  @Expose
  private String soldiers;
  @SerializedName("soldiercasualties")
  @Expose
  private String soldiercasualties;
  @SerializedName("soldierskilled")
  @Expose
  private String soldierskilled;
  @SerializedName("tanks")
  @Expose
  private String tanks;
  @SerializedName("tankcasualties")
  @Expose
  private String tankcasualties;
  @SerializedName("tankskilled")
  @Expose
  private String tankskilled;
  @SerializedName("aircraft")
  @Expose
  private String aircraft;
  @SerializedName("aircraftcasualties")
  @Expose
  private String aircraftcasualties;
  @SerializedName("aircraftkilled")
  @Expose
  private String aircraftkilled;
  @SerializedName("ships")
  @Expose
  private String ships;
  @SerializedName("shipcasualties")
  @Expose
  private String shipcasualties;
  @SerializedName("shipskilled")
  @Expose
  private String shipskilled;
  @SerializedName("missiles")
  @Expose
  private String missiles;
  @SerializedName("missilelaunched")
  @Expose
  private String missilelaunched;
  @SerializedName("missileseaten")
  @Expose
  private String missileseaten;
  @SerializedName("nukes")
  @Expose
  private String nukes;
  @SerializedName("nukeslaunched")
  @Expose
  private String nukeslaunched;
  @SerializedName("nukeseaten")
  @Expose
  private String nukeseaten;
  @SerializedName("infdesttot")
  @Expose
  private String infdesttot;
  @SerializedName("infraLost")
  @Expose
  private String infraLost;
  @SerializedName("moneyLooted")
  @Expose
  private String moneyLooted;
  @SerializedName("ironworks")
  @Expose
  private String ironworks;
  @SerializedName("bauxiteworks")
  @Expose
  private String bauxiteworks;
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
  @SerializedName("missilelpad")
  @Expose
  private String missilelpad;
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
  @SerializedName("city_planning")
  @Expose
  private String city_planning;
  @SerializedName("adv_city_planning")
  @Expose
  private String adv_city_planning;
  @SerializedName("space_program")
  @Expose
  private String space_program;
  @SerializedName("spy_satellite")
  @Expose
  private String spy_satellite;

  @SerializedName("telecommunications_satellite")
  @Expose
  private String telecommunications_satellite;

  @SerializedName("moon_landing")
  @Expose
  private String moon_landing;
  @SerializedName("vmode")
  @Expose
  private String vmode;
  @SerializedName("offensivewars")
  @Expose
  private int offensivewars;
  @SerializedName("defensivewars")
  @Expose
  private int defensivewars;
  @SerializedName("offensivewar_ids")
  @Expose
  private List<Object> offensivewarIds = null;
  @SerializedName("defensivewar_ids")
  @Expose
  private List<String> defensivewarIds = null;
  @SerializedName("beige_turns_left")
  @Expose
  private int beigeTurnsLeft;
  @SerializedName("radiation_index")
  @Expose
  private double radiationIndex;
  @SerializedName("season")
  @Expose
  private String season;

  public List<String> getCityids() {
    return cityids;
  }

  public int getTurns_since_last_city() {
    return turns_since_last_city;
  }

  public int getTurns_since_last_project() {
    return turns_since_last_project;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getNationid() {
    return nationid;
  }

  public String getName() {
    return name;
  }

  public String getPrename() {
    return prename;
  }

  public String getContinent() {
    return continent;
  }

  public String getSocialpolicy() {
    return socialpolicy;
  }

  public String getColor() {
    return color;
  }

  public int getMinutessinceactive() {
    return minutessinceactive;
  }

  public String getUniqueid() {
    return uniqueid;
  }

  public String getGovernment() {
    return government;
  }

  public String getDomesticPolicy() {
    return domesticPolicy;
  }

  public String getWarPolicy() {
    return warPolicy;
  }

  public String getFounded() {
    return founded;
  }

  public int getDaysold() {
    return daysold;
  }

  public String getAlliance() {
    return alliance;
  }

  public String getAllianceposition() {
    return allianceposition;
  }

  public String getAllianceid() {
    return allianceid;
  }

  public String getFlagurl() {
    return flagurl;
  }

  public String getLeadername() {
    return leadername;
  }

  public String getTitle() {
    return title;
  }

  public String getEcopolicy() {
    return ecopolicy;
  }

  public String getApprovalrating() {
    return approvalrating;
  }

  public String getNationrank() {
    return nationrank;
  }

  public String getNationrankstrings() {
    return nationrankstrings;
  }

  public int getNrtotal() {
    return nrtotal;
  }

  public int getCities() {
    return cities;
  }

  public String getLatitude() {
    return latitude;
  }

  public String getLongitude() {
    return longitude;
  }

  public String getScore() {
    return score;
  }

  public String getPopulation() {
    return population;
  }

  public String getGdp() {
    return gdp;
  }

  public double getTotalinfrastructure() {
    return totalinfrastructure;
  }

  public double getLandarea() {
    return landarea;
  }

  public String getSoldiers() {
    return soldiers;
  }

  public String getSoldiercasualties() {
    return soldiercasualties;
  }

  public String getSoldierskilled() {
    return soldierskilled;
  }

  public String getTanks() {
    return tanks;
  }

  public String getTankcasualties() {
    return tankcasualties;
  }

  public String getTankskilled() {
    return tankskilled;
  }

  public String getAircraft() {
    return aircraft;
  }

  public String getAircraftcasualties() {
    return aircraftcasualties;
  }

  public String getAircraftkilled() {
    return aircraftkilled;
  }

  public String getShips() {
    return ships;
  }

  public String getShipcasualties() {
    return shipcasualties;
  }

  public String getShipskilled() {
    return shipskilled;
  }

  public String getMissiles() {
    return missiles;
  }

  public String getMissilelaunched() {
    return missilelaunched;
  }

  public String getMissileseaten() {
    return missileseaten;
  }

  public String getNukes() {
    return nukes;
  }

  public String getNukeslaunched() {
    return nukeslaunched;
  }

  public String getNukeseaten() {
    return nukeseaten;
  }

  public String getInfdesttot() {
    return infdesttot;
  }

  public String getInfraLost() {
    return infraLost;
  }

  public String getMoneyLooted() {
    return moneyLooted;
  }

  public String getIronworks() {
    return ironworks;
  }

  public String getBauxiteworks() {
    return bauxiteworks;
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

  public String getMissilelpad() {
    return missilelpad;
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

  public String getCity_planning() {
    return city_planning;
  }

  public String getAdv_city_planning() {
    return adv_city_planning;
  }

  public String getSpace_program() {
    return space_program;
  }

  public String getSpy_satellite() {
    return spy_satellite;
  }

  public String getMoon_landing() {
    return moon_landing;
  }

  public String getVmode() {
    return vmode;
  }

  public int getOffensivewars() {
    return offensivewars;
  }

  public int getDefensivewars() {
    return defensivewars;
  }

  public List<Object> getOffensivewarIds() {
    return offensivewarIds;
  }

  public List<String> getDefensivewarIds() {
    return defensivewarIds;
  }

  public String getTelecommunications_satellite() {
    return telecommunications_satellite;
  }

  public int getBeigeTurnsLeft() {
    return beigeTurnsLeft;
  }

  public double getRadiationIndex() {
    return radiationIndex;
  }

  public String getSeason() {
    return season;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Nation nation = (Nation) o;
    return cityprojecttimerturns == nation.cityprojecttimerturns &&
        success == nation.success &&
        minutessinceactive == nation.minutessinceactive &&
        daysold == nation.daysold &&
        nrtotal == nation.nrtotal &&
        cities == nation.cities &&
        Double.compare(nation.totalinfrastructure, totalinfrastructure) == 0 &&
        Double.compare(nation.landarea, landarea) == 0 &&
        offensivewars == nation.offensivewars &&
        defensivewars == nation.defensivewars &&
        beigeTurnsLeft == nation.beigeTurnsLeft &&
        Double.compare(nation.radiationIndex, radiationIndex) == 0 &&
        Objects.equals(cityids, nation.cityids) &&
        Objects.equals(nationid, nation.nationid) &&
        Objects.equals(name, nation.name) &&
        Objects.equals(prename, nation.prename) &&
        Objects.equals(continent, nation.continent) &&
        Objects.equals(socialpolicy, nation.socialpolicy) &&
        Objects.equals(color, nation.color) &&
        Objects.equals(uniqueid, nation.uniqueid) &&
        Objects.equals(government, nation.government) &&
        Objects.equals(domesticPolicy, nation.domesticPolicy) &&
        Objects.equals(warPolicy, nation.warPolicy) &&
        Objects.equals(founded, nation.founded) &&
        Objects.equals(alliance, nation.alliance) &&
        Objects.equals(allianceposition, nation.allianceposition) &&
        Objects.equals(allianceid, nation.allianceid) &&
        Objects.equals(flagurl, nation.flagurl) &&
        Objects.equals(leadername, nation.leadername) &&
        Objects.equals(title, nation.title) &&
        Objects.equals(ecopolicy, nation.ecopolicy) &&
        Objects.equals(approvalrating, nation.approvalrating) &&
        Objects.equals(nationrank, nation.nationrank) &&
        Objects.equals(nationrankstrings, nation.nationrankstrings) &&
        Objects.equals(latitude, nation.latitude) &&
        Objects.equals(longitude, nation.longitude) &&
        Objects.equals(score, nation.score) &&
        Objects.equals(population, nation.population) &&
        Objects.equals(gdp, nation.gdp) &&
        Objects.equals(soldiers, nation.soldiers) &&
        Objects.equals(soldiercasualties, nation.soldiercasualties) &&
        Objects.equals(soldierskilled, nation.soldierskilled) &&
        Objects.equals(tanks, nation.tanks) &&
        Objects.equals(tankcasualties, nation.tankcasualties) &&
        Objects.equals(tankskilled, nation.tankskilled) &&
        Objects.equals(aircraft, nation.aircraft) &&
        Objects.equals(aircraftcasualties, nation.aircraftcasualties) &&
        Objects.equals(aircraftkilled, nation.aircraftkilled) &&
        Objects.equals(ships, nation.ships) &&
        Objects.equals(shipcasualties, nation.shipcasualties) &&
        Objects.equals(shipskilled, nation.shipskilled) &&
        Objects.equals(missiles, nation.missiles) &&
        Objects.equals(missilelaunched, nation.missilelaunched) &&
        Objects.equals(missileseaten, nation.missileseaten) &&
        Objects.equals(nukes, nation.nukes) &&
        Objects.equals(nukeslaunched, nation.nukeslaunched) &&
        Objects.equals(nukeseaten, nation.nukeseaten) &&
        Objects.equals(infdesttot, nation.infdesttot) &&
        Objects.equals(infraLost, nation.infraLost) &&
        Objects.equals(moneyLooted, nation.moneyLooted) &&
        Objects.equals(ironworks, nation.ironworks) &&
        Objects.equals(bauxiteworks, nation.bauxiteworks) &&
        Objects.equals(armsstockpile, nation.armsstockpile) &&
        Objects.equals(emgasreserve, nation.emgasreserve) &&
        Objects.equals(massirrigation, nation.massirrigation) &&
        Objects.equals(inttradecenter, nation.inttradecenter) &&
        Objects.equals(missilelpad, nation.missilelpad) &&
        Objects.equals(nuclearresfac, nation.nuclearresfac) &&
        Objects.equals(irondome, nation.irondome) &&
        Objects.equals(vitaldefsys, nation.vitaldefsys) &&
        Objects.equals(intagncy, nation.intagncy) &&
        Objects.equals(uraniumenrich, nation.uraniumenrich) &&
        Objects.equals(propbureau, nation.propbureau) &&
        Objects.equals(cenciveng, nation.cenciveng) &&
        Objects.equals(vmode, nation.vmode) &&
        Objects.equals(offensivewarIds, nation.offensivewarIds) &&
        Objects.equals(defensivewarIds, nation.defensivewarIds) &&
        Objects.equals(season, nation.season);
  }

  @Override
  public int hashCode() {
    return Objects.hash(cityids, cityprojecttimerturns, success, nationid, name, prename, continent, socialpolicy, color, minutessinceactive, uniqueid, government, domesticPolicy, warPolicy, founded, daysold, alliance, allianceposition, allianceid, flagurl, leadername, title, ecopolicy, approvalrating, nationrank, nationrankstrings, nrtotal, cities, latitude, longitude, score, population, gdp, totalinfrastructure, landarea, soldiers, soldiercasualties, soldierskilled, tanks, tankcasualties, tankskilled, aircraft, aircraftcasualties, aircraftkilled, ships, shipcasualties, shipskilled, missiles, missilelaunched, missileseaten, nukes, nukeslaunched, nukeseaten, infdesttot, infraLost, moneyLooted, ironworks, bauxiteworks, armsstockpile, emgasreserve, massirrigation, inttradecenter, missilelpad, nuclearresfac, irondome, vitaldefsys, intagncy, uraniumenrich, propbureau, cenciveng, vmode, offensivewars, defensivewars, offensivewarIds, defensivewarIds, beigeTurnsLeft, radiationIndex, season);
  }

  @Override
  public String toString() {
    return "Nation{" +
        "cityids=" + cityids +
        ", cityprojecttimerturns=" + cityprojecttimerturns +
        ", success=" + success +
        ", nationid='" + nationid + '\'' +
        ", name='" + name + '\'' +
        ", prename='" + prename + '\'' +
        ", continent='" + continent + '\'' +
        ", socialpolicy='" + socialpolicy + '\'' +
        ", color='" + color + '\'' +
        ", minutessinceactive=" + minutessinceactive +
        ", uniqueid='" + uniqueid + '\'' +
        ", government='" + government + '\'' +
        ", domesticPolicy='" + domesticPolicy + '\'' +
        ", warPolicy='" + warPolicy + '\'' +
        ", founded='" + founded + '\'' +
        ", daysold=" + daysold +
        ", alliance='" + alliance + '\'' +
        ", allianceposition='" + allianceposition + '\'' +
        ", allianceid='" + allianceid + '\'' +
        ", flagurl='" + flagurl + '\'' +
        ", leadername='" + leadername + '\'' +
        ", title='" + title + '\'' +
        ", ecopolicy='" + ecopolicy + '\'' +
        ", approvalrating='" + approvalrating + '\'' +
        ", nationrank='" + nationrank + '\'' +
        ", nationrankstrings='" + nationrankstrings + '\'' +
        ", nrtotal=" + nrtotal +
        ", cities=" + cities +
        ", latitude='" + latitude + '\'' +
        ", longitude='" + longitude + '\'' +
        ", score='" + score + '\'' +
        ", population='" + population + '\'' +
        ", gdp='" + gdp + '\'' +
        ", totalinfrastructure=" + totalinfrastructure +
        ", landarea=" + landarea +
        ", soldiers='" + soldiers + '\'' +
        ", soldiercasualties='" + soldiercasualties + '\'' +
        ", soldierskilled='" + soldierskilled + '\'' +
        ", tanks='" + tanks + '\'' +
        ", tankcasualties='" + tankcasualties + '\'' +
        ", tankskilled='" + tankskilled + '\'' +
        ", aircraft='" + aircraft + '\'' +
        ", aircraftcasualties='" + aircraftcasualties + '\'' +
        ", aircraftkilled='" + aircraftkilled + '\'' +
        ", ships='" + ships + '\'' +
        ", shipcasualties='" + shipcasualties + '\'' +
        ", shipskilled='" + shipskilled + '\'' +
        ", missiles='" + missiles + '\'' +
        ", missilelaunched='" + missilelaunched + '\'' +
        ", missileseaten='" + missileseaten + '\'' +
        ", nukes='" + nukes + '\'' +
        ", nukeslaunched='" + nukeslaunched + '\'' +
        ", nukeseaten='" + nukeseaten + '\'' +
        ", infdesttot='" + infdesttot + '\'' +
        ", infraLost='" + infraLost + '\'' +
        ", moneyLooted='" + moneyLooted + '\'' +
        ", ironworks='" + ironworks + '\'' +
        ", bauxiteworks='" + bauxiteworks + '\'' +
        ", armsstockpile='" + armsstockpile + '\'' +
        ", emgasreserve='" + emgasreserve + '\'' +
        ", massirrigation='" + massirrigation + '\'' +
        ", inttradecenter='" + inttradecenter + '\'' +
        ", missilelpad='" + missilelpad + '\'' +
        ", nuclearresfac='" + nuclearresfac + '\'' +
        ", irondome='" + irondome + '\'' +
        ", vitaldefsys='" + vitaldefsys + '\'' +
        ", intagncy='" + intagncy + '\'' +
        ", uraniumenrich='" + uraniumenrich + '\'' +
        ", propbureau='" + propbureau + '\'' +
        ", cenciveng='" + cenciveng + '\'' +
        ", vmode='" + vmode + '\'' +
        ", offensivewars=" + offensivewars +
        ", defensivewars=" + defensivewars +
        ", offensivewarIds=" + offensivewarIds +
        ", defensivewarIds=" + defensivewarIds +
        ", beigeTurnsLeft=" + beigeTurnsLeft +
        ", radiationIndex=" + radiationIndex +
        ", season='" + season + '\'' +
        '}';
  }
}
