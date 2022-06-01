package link.locutus.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class City extends Entity {
  @SerializedName("success")
  @Expose
  private boolean success;
  @SerializedName("cityid")
  @Expose
  private String cityid;
  @SerializedName("url")
  @Expose
  public String url;
  @SerializedName("nationid")
  @Expose
  private String nationid;
  @SerializedName("name")
  @Expose
  private String name;
  @SerializedName("nation")
  @Expose
  private String nation;
  @SerializedName("leader")
  @Expose
  private String leader;
  @SerializedName("continent")
  @Expose
  private String continent;
  @SerializedName("founded")
  @Expose
  private String founded;
  @SerializedName("age")
  @Expose
  private int age;
  @SerializedName("powered")
  @Expose
  private String powered;
  @SerializedName("infrastructure")
  @Expose
  private String infrastructure;
  @SerializedName("land")
  @Expose
  private String land;
  @SerializedName("population")
  @Expose
  private double population;
  @SerializedName("popdensity")
  @Expose
  private double popdensity;
  @SerializedName("crime")
  @Expose
  private double crime;
  @SerializedName("disease")
  @Expose
  private double disease;
  @SerializedName("commerce")
  @Expose
  private double commerce;
  @SerializedName("avgincome")
  @Expose
  private double avgincome;
  @SerializedName("pollution")
  @Expose
  private double pollution;
  @SerializedName("nuclearpollution")
  @Expose
  private double nuclearpollution;
  @SerializedName("basepop")
  @Expose
  private double basepop;
  @SerializedName("basepopdensity")
  @Expose
  private double basepopdensity;
  @SerializedName("minimumwage")
  @Expose
  private double minimumwage;
  @SerializedName("poplostdisease")
  @Expose
  private double poplostdisease;
  @SerializedName("poplostcrime")
  @Expose
  private double poplostcrime;
  @SerializedName("imp_coalpower")
  @Expose
  private String impCoalpower;
  @SerializedName("imp_oilpower")
  @Expose
  private String impOilpower;
  @SerializedName("imp_nuclearpower")
  @Expose
  private String impNuclearpower;
  @SerializedName("imp_windpower")
  @Expose
  private String impWindpower;
  @SerializedName("imp_coalmine")
  @Expose
  private String impCoalmine;
  @SerializedName("imp_oilwell")
  @Expose
  private String impOilwell;
  @SerializedName("imp_ironmine")
  @Expose
  private String impIronmine;
  @SerializedName("imp_bauxitemine")
  @Expose
  private String impBauxitemine;
  @SerializedName("imp_leadmine")
  @Expose
  private String impLeadmine;
  @SerializedName("imp_uramine")
  @Expose
  private String impUramine;
  @SerializedName("imp_farm")
  @Expose
  private String impFarm;
  @SerializedName("imp_gasrefinery")
  @Expose
  private String impGasrefinery;
  @SerializedName("imp_steelmill")
  @Expose
  private String impSteelmill;
  @SerializedName("imp_aluminumrefinery")
  @Expose
  private String impAluminumrefinery;
  @SerializedName("imp_munitionsfactory")
  @Expose
  private String impMunitionsfactory;
  @SerializedName("imp_policestation")
  @Expose
  private String impPolicestation;
  @SerializedName("imp_hospital")
  @Expose
  private String impHospital;
  @SerializedName("imp_recyclingcenter")
  @Expose
  private String impRecyclingcenter;
  @SerializedName("imp_subway")
  @Expose
  private String impSubway;
  @SerializedName("imp_supermarket")
  @Expose
  private String impSupermarket;
  @SerializedName("imp_bank")
  @Expose
  private String impBank;
  @SerializedName("imp_mall")
  @Expose
  private String impMall;
  @SerializedName("imp_stadium")
  @Expose
  private String impStadium;
  @SerializedName("imp_barracks")
  @Expose
  private String impBarracks;
  @SerializedName("imp_factory")
  @Expose
  private String impFactory;
  @SerializedName("imp_hangar")
  @Expose
  private String impHangar;
  @SerializedName("imp_drydock")
  @Expose
  private String impDrydock;

  public boolean isSuccess() {
    return success;
  }

  public String getCityid() {
    return cityid;
  }

  public String getUrl() {
    return url;
  }

  public String getNationid() {
    return nationid;
  }

  public String getName() {
    return name;
  }

  public String getNation() {
    return nation;
  }

  public String getLeader() {
    return leader;
  }

  public String getContinent() {
    return continent;
  }

  public String getFounded() {
    return founded;
  }

  public int getAge() {
    return age;
  }

  public String getPowered() {
    return powered;
  }

  public String getInfrastructure() {
    return infrastructure;
  }

  public String getLand() {
    return land;
  }

  public double getPopulation() {
    return population;
  }

  public double getPopdensity() {
    return popdensity;
  }

  public double getCrime() {
    return crime;
  }

  public double getDisease() {
    return disease;
  }

  public double getCommerce() {
    return commerce;
  }

  public double getAvgincome() {
    return avgincome;
  }

  public double getPollution() {
    return pollution;
  }

  public double getNuclearpollution() {
    return nuclearpollution;
  }

  public double getBasepop() {
    return basepop;
  }

  public double getBasepopdensity() {
    return basepopdensity;
  }

  public double getMinimumwage() {
    return minimumwage;
  }

  public double getPoplostdisease() {
    return poplostdisease;
  }

  public double getPoplostcrime() {
    return poplostcrime;
  }

  public String getImpCoalpower() {
    return impCoalpower;
  }

  public String getImpOilpower() {
    return impOilpower;
  }

  public String getImpNuclearpower() {
    return impNuclearpower;
  }

  public String getImpWindpower() {
    return impWindpower;
  }

  public String getImpCoalmine() {
    return impCoalmine;
  }

  public String getImpOilwell() {
    return impOilwell;
  }

  public String getImpIronmine() {
    return impIronmine;
  }

  public String getImpBauxitemine() {
    return impBauxitemine;
  }

  public String getImpLeadmine() {
    return impLeadmine;
  }

  public String getImpUramine() {
    return impUramine;
  }

  public String getImpFarm() {
    return impFarm;
  }

  public String getImpGasrefinery() {
    return impGasrefinery;
  }

  public String getImpSteelmill() {
    return impSteelmill;
  }

  public String getImpAluminumrefinery() {
    return impAluminumrefinery;
  }

  public String getImpMunitionsfactory() {
    return impMunitionsfactory;
  }

  public String getImpPolicestation() {
    return impPolicestation;
  }

  public String getImpHospital() {
    return impHospital;
  }

  public String getImpRecyclingcenter() {
    return impRecyclingcenter;
  }

  public String getImpSubway() {
    return impSubway;
  }

  public String getImpSupermarket() {
    return impSupermarket;
  }

  public String getImpBank() {
    return impBank;
  }

  public String getImpMall() {
    return impMall;
  }

  public String getImpStadium() {
    return impStadium;
  }

  public String getImpBarracks() {
    return impBarracks;
  }

  public String getImpFactory() {
    return impFactory;
  }

  public String getImpHangar() {
    return impHangar;
  }

  public String getImpDrydock() {
    return impDrydock;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    City city = (City) o;
    return success == city.success &&
        age == city.age &&
        Double.compare(city.population, population) == 0 &&
        Double.compare(city.popdensity, popdensity) == 0 &&
        Double.compare(city.crime, crime) == 0 &&
        Double.compare(city.disease, disease) == 0 &&
        Double.compare(city.commerce, commerce) == 0 &&
        Double.compare(city.avgincome, avgincome) == 0 &&
        Double.compare(city.pollution, pollution) == 0 &&
        Double.compare(city.nuclearpollution, nuclearpollution) == 0 &&
        Double.compare(city.basepop, basepop) == 0 &&
        Double.compare(city.basepopdensity, basepopdensity) == 0 &&
        Double.compare(city.minimumwage, minimumwage) == 0 &&
        Double.compare(city.poplostdisease, poplostdisease) == 0 &&
        Double.compare(city.poplostcrime, poplostcrime) == 0 &&
        Objects.equals(cityid, city.cityid) &&
        Objects.equals(url, city.url) &&
        Objects.equals(nationid, city.nationid) &&
        Objects.equals(name, city.name) &&
        Objects.equals(nation, city.nation) &&
        Objects.equals(leader, city.leader) &&
        Objects.equals(continent, city.continent) &&
        Objects.equals(founded, city.founded) &&
        Objects.equals(powered, city.powered) &&
        Objects.equals(infrastructure, city.infrastructure) &&
        Objects.equals(land, city.land) &&
        Objects.equals(impCoalpower, city.impCoalpower) &&
        Objects.equals(impOilpower, city.impOilpower) &&
        Objects.equals(impNuclearpower, city.impNuclearpower) &&
        Objects.equals(impWindpower, city.impWindpower) &&
        Objects.equals(impCoalmine, city.impCoalmine) &&
        Objects.equals(impOilwell, city.impOilwell) &&
        Objects.equals(impIronmine, city.impIronmine) &&
        Objects.equals(impBauxitemine, city.impBauxitemine) &&
        Objects.equals(impLeadmine, city.impLeadmine) &&
        Objects.equals(impUramine, city.impUramine) &&
        Objects.equals(impFarm, city.impFarm) &&
        Objects.equals(impGasrefinery, city.impGasrefinery) &&
        Objects.equals(impSteelmill, city.impSteelmill) &&
        Objects.equals(impAluminumrefinery, city.impAluminumrefinery) &&
        Objects.equals(impMunitionsfactory, city.impMunitionsfactory) &&
        Objects.equals(impPolicestation, city.impPolicestation) &&
        Objects.equals(impHospital, city.impHospital) &&
        Objects.equals(impRecyclingcenter, city.impRecyclingcenter) &&
        Objects.equals(impSubway, city.impSubway) &&
        Objects.equals(impSupermarket, city.impSupermarket) &&
        Objects.equals(impBank, city.impBank) &&
        Objects.equals(impMall, city.impMall) &&
        Objects.equals(impStadium, city.impStadium) &&
        Objects.equals(impBarracks, city.impBarracks) &&
        Objects.equals(impFactory, city.impFactory) &&
        Objects.equals(impHangar, city.impHangar) &&
        Objects.equals(impDrydock, city.impDrydock);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, cityid, url, nationid, name, nation, leader, continent, founded, age, powered, infrastructure, land, population, popdensity, crime, disease, commerce, avgincome, pollution, nuclearpollution, basepop, basepopdensity, minimumwage, poplostdisease, poplostcrime, impCoalpower, impOilpower, impNuclearpower, impWindpower, impCoalmine, impOilwell, impIronmine, impBauxitemine, impLeadmine, impUramine, impFarm, impGasrefinery, impSteelmill, impAluminumrefinery, impMunitionsfactory, impPolicestation, impHospital, impRecyclingcenter, impSubway, impSupermarket, impBank, impMall, impStadium, impBarracks, impFactory, impHangar, impDrydock);
  }

  @Override
  public String toString() {
    return "City{" +
        "success=" + success +
        ", cityid='" + cityid + '\'' +
        ", url='" + url + '\'' +
        ", nationid='" + nationid + '\'' +
        ", name='" + name + '\'' +
        ", nation='" + nation + '\'' +
        ", leader='" + leader + '\'' +
        ", continent='" + continent + '\'' +
        ", founded='" + founded + '\'' +
        ", age=" + age +
        ", powered='" + powered + '\'' +
        ", infrastructure='" + infrastructure + '\'' +
        ", land='" + land + '\'' +
        ", population=" + population +
        ", popdensity=" + popdensity +
        ", crime=" + crime +
        ", disease=" + disease +
        ", commerce=" + commerce +
        ", avgincome=" + avgincome +
        ", pollution=" + pollution +
        ", nuclearpollution=" + nuclearpollution +
        ", basepop=" + basepop +
        ", basepopdensity=" + basepopdensity +
        ", minimumwage=" + minimumwage +
        ", poplostdisease=" + poplostdisease +
        ", poplostcrime=" + poplostcrime +
        ", impCoalpower='" + impCoalpower + '\'' +
        ", impOilpower='" + impOilpower + '\'' +
        ", impNuclearpower='" + impNuclearpower + '\'' +
        ", impWindpower='" + impWindpower + '\'' +
        ", impCoalmine='" + impCoalmine + '\'' +
        ", impOilwell='" + impOilwell + '\'' +
        ", impIronmine='" + impIronmine + '\'' +
        ", impBauxitemine='" + impBauxitemine + '\'' +
        ", impLeadmine='" + impLeadmine + '\'' +
        ", impUramine='" + impUramine + '\'' +
        ", impFarm='" + impFarm + '\'' +
        ", impGasrefinery='" + impGasrefinery + '\'' +
        ", impSteelmill='" + impSteelmill + '\'' +
        ", impAluminumrefinery='" + impAluminumrefinery + '\'' +
        ", impMunitionsfactory='" + impMunitionsfactory + '\'' +
        ", impPolicestation='" + impPolicestation + '\'' +
        ", impHospital='" + impHospital + '\'' +
        ", impRecyclingcenter='" + impRecyclingcenter + '\'' +
        ", impSubway='" + impSubway + '\'' +
        ", impSupermarket='" + impSupermarket + '\'' +
        ", impBank='" + impBank + '\'' +
        ", impMall='" + impMall + '\'' +
        ", impStadium='" + impStadium + '\'' +
        ", impBarracks='" + impBarracks + '\'' +
        ", impFactory='" + impFactory + '\'' +
        ", impHangar='" + impHangar + '\'' +
        ", impDrydock='" + impDrydock + '\'' +
        '}';
  }
}
