package link.locutus.discord.pnw.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import link.locutus.discord.apiv1.domains.City;

import java.util.ArrayList;
import java.util.Objects;

public class CityBuild {

    @SerializedName("infra_needed")
    private Integer infraNeeded;
    @SerializedName("imp_total")
    private Integer impTotal;
    @SerializedName("imp_coalpower")
    private Integer impCoalpower;
    @SerializedName("imp_oilpower")
    private Integer impOilpower;
    @SerializedName("imp_windpower")
    private Integer impWindpower;
    @SerializedName("imp_nuclearpower")
    private Integer impNuclearpower;
    @SerializedName("imp_coalmine")
    private Integer impCoalmine;
    @SerializedName("imp_oilwell")
    private Integer impOilwell;
    @SerializedName("imp_uramine")
    private Integer impUramine;
    @SerializedName("imp_leadmine")
    private Integer impLeadmine;
    @SerializedName("imp_ironmine")
    private Integer impIronmine;
    @SerializedName("imp_bauxitemine")
    private Integer impBauxitemine;
    @SerializedName("imp_farm")
    private Integer impFarm;
    @SerializedName("imp_gasrefinery")
    private Integer impGasrefinery;
    @SerializedName("imp_aluminumrefinery")
    private Integer impAluminumrefinery;
    @SerializedName("imp_munitionsfactory")
    private Integer impMunitionsfactory;
    @SerializedName("imp_steelmill")
    private Integer impSteelmill;
    @SerializedName("imp_policestation")
    private Integer impPolicestation;
    @SerializedName("imp_hospital")
    private Integer impHospital;
    @SerializedName("imp_recyclingcenter")
    private Integer impRecyclingcenter;
    @SerializedName("imp_subway")
    private Integer impSubway;
    @SerializedName("imp_supermarket")
    private Integer impSupermarket;
    @SerializedName("imp_bank")
    private Integer impBank;
    @SerializedName("imp_mall")
    private Integer impMall;
    @SerializedName("imp_stadium")
    private Integer impStadium;
    @SerializedName("imp_barracks")
    private Integer impBarracks;
    @SerializedName("imp_factory")
    private Integer impFactory;
    @SerializedName("imp_hangars")
    private Integer impHangars;
    @SerializedName("imp_drydock")
    private Integer impDrydock;

    private transient Integer age;
    private transient Double land;
    private transient Integer population;
    private transient Double disease;
    private transient Double crime;
    private transient Integer pollution;
    private transient Double commerce;
    private transient Boolean powered;

    public CityBuild() {
        infraNeeded = 0;
        impTotal = 0;
        impCoalpower = 0;
        impOilpower = 0;
        impWindpower = 0;
        impNuclearpower = 0;
        impCoalmine = 0;
        impOilwell = 0;
        impUramine = 0;
        impLeadmine = 0;
        impIronmine = 0;
        impBauxitemine = 0;
        impFarm = 0;
        impGasrefinery = 0;
        impAluminumrefinery = 0;
        impMunitionsfactory = 0;
        impSteelmill = 0;
        impPolicestation = 0;
        impHospital = 0;
        impRecyclingcenter = 0;
        impSubway = 0;
        impSupermarket = 0;
        impBank = 0;
        impMall = 0;
        impStadium = 0;
        impBarracks = 0;
        impFactory = 0;
        impHangars = 0;
        impDrydock = 0;
    }

    public static CityBuild of(String buildJson) {
        return of(buildJson, false);
    }

    public static String parseShorthand(String json) {
        json = json.replace(" ", "").replace("=", ":");
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        JsonElement mmr = obj.get("mmr");
        if (mmr != null) {
            String mmrStr = mmr.getAsString();
            obj.addProperty("imp_barracks", mmrStr.charAt(0) - '0');
            obj.addProperty("imp_factory", mmrStr.charAt(1) - '0');
            obj.addProperty("imp_hangars", mmrStr.charAt(2) - '0');
            obj.addProperty("imp_drydock", mmrStr.charAt(3) - '0');
            obj.remove("mmr");
        }
        JsonObject emptyBuild = JsonParser.parseString(new CityBuild().toString()).getAsJsonObject();
        for (String key : new ArrayList<>(obj.keySet())) {
            if (emptyBuild.has(key)) continue;
            String impNameBase = "imp_" + key.toLowerCase();
            String impName = impNameBase;
            if (emptyBuild.has(impName)) {
                obj.add(impName, obj.remove(key));
                continue;
            }
            impName = impNameBase + "refinery";
            if (emptyBuild.has(impName)) {
                obj.add(impName, obj.remove(key));
                continue;
            }
            impName = impNameBase + "mine";
            if (emptyBuild.has(impName)) {
                obj.add(impName, obj.remove(key));
                continue;
            }
            impName = impNameBase + "well";
            if (emptyBuild.has(impName)) {
                obj.add(impName, obj.remove(key));
                continue;
            }
            impName = impNameBase + "mill";
            if (emptyBuild.has(impName)) {
                obj.add(impName, obj.remove(key));
                continue;
            }
            impName = impNameBase + "power";
            if (emptyBuild.has(impName)) {
                obj.add(impName, obj.remove(key));
                continue;
            }
        }
        return obj.toString();
    }

    public static CityBuild of(String buildJson, boolean parseShorthand) {
        if (parseShorthand) {
            buildJson = parseShorthand(buildJson);
        }
        CityBuild build = new Gson().fromJson(buildJson, CityBuild.class);
        JsonParser parser = new JsonParser();
        JsonObject json = parser.parse(buildJson).getAsJsonObject();
        JsonElement land = json.get("land");
        if (land != null) {
            build.land = land.getAsDouble();
        }
        JsonElement age = json.get("age");
        if (age != null) {
            build.age = (int) age.getAsDouble();
        }
        JsonElement infra = json.get("infra");
        if (infra != null) {
            build.infraNeeded = (int) infra.getAsDouble();
        }
        return build;
    }

    public int recalcInfraNeeded() {
        return impCoalpower + impOilpower + impWindpower + impNuclearpower + impCoalmine + impOilwell + impUramine + impLeadmine + impIronmine + impBauxitemine + impFarm + impGasrefinery + impAluminumrefinery + impMunitionsfactory + impSteelmill + impPolicestation + impHospital + impRecyclingcenter + impSubway + impSupermarket + impBank + impMall + impStadium + impBarracks + impFactory + impHangars + impDrydock;
    }

    public CityBuild(City city) {
        age = city.getAge();
        infraNeeded = (int) Double.parseDouble(city.getInfrastructure());
        impCoalpower = Integer.parseInt(city.getImpCoalpower());
        impOilpower = Integer.parseInt(city.getImpOilpower());
        impWindpower = Integer.parseInt(city.getImpWindpower());
        impNuclearpower = Integer.parseInt(city.getImpNuclearpower());
        impCoalmine = Integer.parseInt(city.getImpCoalmine());
        impOilwell = Integer.parseInt(city.getImpOilwell());
        impUramine = Integer.parseInt(city.getImpUramine());
        impLeadmine = Integer.parseInt(city.getImpLeadmine());
        impIronmine = Integer.parseInt(city.getImpIronmine());
        impBauxitemine = Integer.parseInt(city.getImpBauxitemine());
        impFarm = Integer.parseInt(city.getImpFarm());
        impGasrefinery = Integer.parseInt(city.getImpGasrefinery());
        impAluminumrefinery = Integer.parseInt(city.getImpAluminumrefinery());
        impMunitionsfactory = Integer.parseInt(city.getImpMunitionsfactory());
        impSteelmill = Integer.parseInt(city.getImpSteelmill());
        impPolicestation = Integer.parseInt(city.getImpPolicestation());
        impHospital = Integer.parseInt(city.getImpHospital());
        impRecyclingcenter = Integer.parseInt(city.getImpRecyclingcenter());
        impSubway = Integer.parseInt(city.getImpSubway());
        impSupermarket = Integer.parseInt(city.getImpSupermarket());
        impBank = Integer.parseInt(city.getImpBank());
        impMall = Integer.parseInt(city.getImpMall());
        impStadium = Integer.parseInt(city.getImpStadium());
        impBarracks = Integer.parseInt(city.getImpBarracks());
        impFactory = Integer.parseInt(city.getImpFactory());
        impHangars = Integer.parseInt(city.getImpHangar());
        impDrydock = Integer.parseInt(city.getImpDrydock());
        impTotal = recalcInfraNeeded();
    }

    public CityBuild(CityBuild other) {
        infraNeeded = other.infraNeeded;
        impTotal = other.impTotal;
        impCoalpower = other.impCoalpower;
        impOilpower = other.impOilpower;
        impWindpower = other.impWindpower;
        impNuclearpower = other.impNuclearpower;
        impCoalmine = other.impCoalmine;
        impOilwell = other.impOilwell;
        impUramine = other.impUramine;
        impLeadmine = other.impLeadmine;
        impIronmine = other.impIronmine;
        impBauxitemine = other.impBauxitemine;
        impFarm = other.impFarm;
        impGasrefinery = other.impGasrefinery;
        impAluminumrefinery = other.impAluminumrefinery;
        impMunitionsfactory = other.impMunitionsfactory;
        impSteelmill = other.impSteelmill;
        impPolicestation = other.impPolicestation;
        impHospital = other.impHospital;
        impRecyclingcenter = other.impRecyclingcenter;
        impSubway = other.impSubway;
        impSupermarket = other.impSupermarket;
        impBank = other.impBank;
        impMall = other.impMall;
        impStadium = other.impStadium;
        impBarracks = other.impBarracks;
        impFactory = other.impFactory;
        impHangars = other.impHangars;
        impDrydock = other.impDrydock;
    }

    public CityBuild(CityBuild from, CityBuild to) {
        this(to);
        infraNeeded = Math.max(0, to.infraNeeded - from.infraNeeded);
        impTotal = Math.max(0, to.impTotal - from.impTotal);
        impCoalpower = Math.max(0, to.impCoalpower - from.impCoalpower);
        impOilpower = Math.max(0, to.impOilpower - from.impOilpower);
        impWindpower = Math.max(0, to.impWindpower - from.impWindpower);
        impNuclearpower = Math.max(0, to.impNuclearpower - from.impNuclearpower);
        impCoalmine = Math.max(0, to.impCoalmine - from.impCoalmine);
        impOilwell = Math.max(0, to.impOilwell - from.impOilwell);
        impUramine = Math.max(0, to.impUramine - from.impUramine);
        impLeadmine = Math.max(0, to.impLeadmine - from.impLeadmine);
        impIronmine = Math.max(0, to.impIronmine - from.impIronmine);
        impBauxitemine = Math.max(0, to.impBauxitemine - from.impBauxitemine);
        impFarm = Math.max(0, to.impFarm - from.impFarm);
        impGasrefinery = Math.max(0, to.impGasrefinery - from.impGasrefinery);
        impAluminumrefinery = Math.max(0, to.impAluminumrefinery - from.impAluminumrefinery);
        impMunitionsfactory = Math.max(0, to.impMunitionsfactory - from.impMunitionsfactory);
        impSteelmill = Math.max(0, to.impSteelmill - from.impSteelmill);
        impPolicestation = Math.max(0, to.impPolicestation - from.impPolicestation);
        impHospital = Math.max(0, to.impHospital - from.impHospital);
        impRecyclingcenter = Math.max(0, to.impRecyclingcenter - from.impRecyclingcenter);
        impSubway = Math.max(0, to.impSubway - from.impSubway);
        impSupermarket = Math.max(0, impSupermarket - from.impSupermarket);
        impBank = Math.max(0, to.impBank - from.impBank);
        impMall = Math.max(0, to.impMall - from.impMall);
        impStadium = Math.max(0, to.impStadium - from.impStadium);
        impBarracks = Math.max(0, to.impBarracks - from.impBarracks);
        impFactory = Math.max(0, to.impFactory - from.impFactory);
        impHangars = Math.max(0, to.impHangars - from.impHangars);
        impDrydock = Math.max(0, to.impDrydock - from.impDrydock);
    }

    @SerializedName("infra_needed")
    public Integer getInfraNeeded() {
        return infraNeeded;
    }

    @SerializedName("infra_needed")
    public void setInfraNeeded(Integer infraNeeded) {
        this.infraNeeded = infraNeeded;
    }

    @SerializedName("imp_total")
    public Integer getImpTotal() {
        return impTotal;
    }

    @SerializedName("imp_total")
    public void setImpTotal(Integer impTotal) {
        this.impTotal = impTotal;
    }

    @SerializedName("imp_coalpower")
    public Integer getImpCoalpower() {
        return impCoalpower;
    }

    @SerializedName("imp_coalpower")
    public void setImpCoalpower(Integer impCoalpower) {
        this.impCoalpower = impCoalpower;
    }

    @SerializedName("imp_oilpower")
    public Integer getImpOilpower() {
        return impOilpower;
    }

    @SerializedName("imp_oilpower")
    public void setImpOilpower(Integer impOilpower) {
        this.impOilpower = impOilpower;
    }

    @SerializedName("imp_windpower")
    public Integer getImpWindpower() {
        return impWindpower;
    }

    @SerializedName("imp_windpower")
    public void setImpWindpower(Integer impWindpower) {
        this.impWindpower = impWindpower;
    }

    @SerializedName("imp_nuclearpower")
    public Integer getImpNuclearpower() {
        return impNuclearpower;
    }

    @SerializedName("imp_nuclearpower")
    public void setImpNuclearpower(Integer impNuclearpower) {
        this.impNuclearpower = impNuclearpower;
    }

    @SerializedName("imp_coalmine")
    public Integer getImpCoalmine() {
        return impCoalmine;
    }

    @SerializedName("imp_coalmine")
    public void setImpCoalmine(Integer impCoalmine) {
        this.impCoalmine = impCoalmine;
    }

    @SerializedName("imp_oilwell")
    public Integer getImpOilwell() {
        return impOilwell;
    }

    @SerializedName("imp_oilwell")
    public void setImpOilwell(Integer impOilwell) {
        this.impOilwell = impOilwell;
    }

    @SerializedName("imp_uramine")
    public Integer getImpUramine() {
        return impUramine;
    }

    @SerializedName("imp_uramine")
    public void setImpUramine(Integer impUramine) {
        this.impUramine = impUramine;
    }

    @SerializedName("imp_leadmine")
    public Integer getImpLeadmine() {
        return impLeadmine;
    }

    @SerializedName("imp_leadmine")
    public void setImpLeadmine(Integer impLeadmine) {
        this.impLeadmine = impLeadmine;
    }

    @SerializedName("imp_ironmine")
    public Integer getImpIronmine() {
        return impIronmine;
    }

    @SerializedName("imp_ironmine")
    public void setImpIronmine(Integer impIronmine) {
        this.impIronmine = impIronmine;
    }

    @SerializedName("imp_bauxitemine")
    public Integer getImpBauxitemine() {
        return impBauxitemine;
    }

    @SerializedName("imp_bauxitemine")
    public void setImpBauxitemine(Integer impBauxitemine) {
        this.impBauxitemine = impBauxitemine;
    }

    @SerializedName("imp_farm")
    public Integer getImpFarm() {
        return impFarm;
    }

    @SerializedName("imp_farm")
    public void setImpFarm(Integer impFarm) {
        this.impFarm = impFarm;
    }

    @SerializedName("imp_gasrefinery")
    public Integer getImpGasrefinery() {
        return impGasrefinery;
    }

    @SerializedName("imp_gasrefinery")
    public void setImpGasrefinery(Integer impGasrefinery) {
        this.impGasrefinery = impGasrefinery;
    }

    @SerializedName("imp_aluminumrefinery")
    public Integer getImpAluminumrefinery() {
        return impAluminumrefinery;
    }

    @SerializedName("imp_aluminumrefinery")
    public void setImpAluminumrefinery(Integer impAluminumrefinery) {
        this.impAluminumrefinery = impAluminumrefinery;
    }

    @SerializedName("imp_munitionsfactory")
    public Integer getImpMunitionsfactory() {
        return impMunitionsfactory;
    }

    @SerializedName("imp_munitionsfactory")
    public void setImpMunitionsfactory(Integer impMunitionsfactory) {
        this.impMunitionsfactory = impMunitionsfactory;
    }

    @SerializedName("imp_steelmill")
    public Integer getImpSteelmill() {
        return impSteelmill;
    }

    @SerializedName("imp_steelmill")
    public void setImpSteelmill(Integer impSteelmill) {
        this.impSteelmill = impSteelmill;
    }

    @SerializedName("imp_policestation")
    public Integer getImpPolicestation() {
        return impPolicestation;
    }

    @SerializedName("imp_policestation")
    public void setImpPolicestation(Integer impPolicestation) {
        this.impPolicestation = impPolicestation;
    }

    @SerializedName("imp_hospital")
    public Integer getImpHospital() {
        return impHospital;
    }

    @SerializedName("imp_hospital")
    public void setImpHospital(Integer impHospital) {
        this.impHospital = impHospital;
    }

    @SerializedName("imp_recyclingcenter")
    public Integer getImpRecyclingcenter() {
        return impRecyclingcenter;
    }

    @SerializedName("imp_recyclingcenter")
    public void setImpRecyclingcenter(Integer impRecyclingcenter) {
        this.impRecyclingcenter = impRecyclingcenter;
    }

    @SerializedName("imp_subway")
    public Integer getImpSubway() {
        return impSubway;
    }

    @SerializedName("imp_subway")
    public void setImpSubway(Integer impSubway) {
        this.impSubway = impSubway;
    }

    @SerializedName("imp_supermarket")
    public Integer getImpSupermarket() {
        return impSupermarket;
    }

    @SerializedName("imp_supermarket")
    public void setImpSupermarket(Integer impSupermarket) {
        this.impSupermarket = impSupermarket;
    }

    @SerializedName("imp_bank")
    public Integer getImpBank() {
        return impBank;
    }

    @SerializedName("imp_bank")
    public void setImpBank(Integer impBank) {
        this.impBank = impBank;
    }

    @SerializedName("imp_mall")
    public Integer getImpMall() {
        return impMall;
    }

    @SerializedName("imp_mall")
    public void setImpMall(Integer impMall) {
        this.impMall = impMall;
    }

    @SerializedName("imp_stadium")
    public Integer getImpStadium() {
        return impStadium;
    }

    @SerializedName("imp_stadium")
    public void setImpStadium(Integer impStadium) {
        this.impStadium = impStadium;
    }

    @SerializedName("imp_barracks")
    public Integer getImpBarracks() {
        return impBarracks;
    }

    @SerializedName("imp_barracks")
    public void setImpBarracks(Integer impBarracks) {
        this.impBarracks = impBarracks;
    }

    @SerializedName("imp_factory")
    public Integer getImpFactory() {
        return impFactory;
    }

    @SerializedName("imp_factory")
    public void setImpFactory(Integer impFactory) {
        this.impFactory = impFactory;
    }

    @SerializedName("imp_hangars")
    public Integer getImpHangars() {
        return impHangars;
    }

    @SerializedName("imp_hangars")
    public void setImpHangars(Integer impHangars) {
        this.impHangars = impHangars;
    }

    @SerializedName("imp_drydock")
    public Integer getImpDrydock() {
        return impDrydock;
    }

    @SerializedName("imp_drydock")
    public void setImpDrydock(Integer impDrydock) {
        this.impDrydock = impDrydock;
    }

    public void setLand(Double land) {
        this.land = land;
    }

    public Double getLand() {
        return land;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Integer getAge() {
        return age;
    }

    public Integer getPopulation() {
        return population;
    }

    public void setPopulation(Integer population) {
        this.population = population;
    }

    public Double getDisease() {
        return disease;
    }

    public void setDisease(Double disease) {
        this.disease = disease;
    }

    public Double getCrime() {
        return crime;
    }

    public void setCrime(Double crime) {
        this.crime = crime;
    }

    public Integer getPollution() {
        return pollution;
    }

    public void setPollution(Integer pollution) {
        this.pollution = pollution;
    }

    public Double getCommerce() {
        return commerce;
    }

    public void setCommerce(Double commerce) {
        this.commerce = commerce;
    }

    public Boolean getPowered() {
        return powered;
    }

    public void setPowered(Boolean powered) {
        this.powered = powered;
    }

    @Override
    public String toString() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }

    public String toCompressedString() {
        return new Gson().toJson(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CityBuild cityBuild = (CityBuild) o;

        if (!Objects.equals(infraNeeded, cityBuild.infraNeeded))
            return false;
        if (!Objects.equals(impTotal, cityBuild.impTotal)) return false;
        if (!Objects.equals(impCoalpower, cityBuild.impCoalpower))
            return false;
        if (!Objects.equals(impOilpower, cityBuild.impOilpower))
            return false;
        if (!Objects.equals(impWindpower, cityBuild.impWindpower))
            return false;
        if (!Objects.equals(impNuclearpower, cityBuild.impNuclearpower))
            return false;
        if (!Objects.equals(impCoalmine, cityBuild.impCoalmine))
            return false;
        if (!Objects.equals(impOilwell, cityBuild.impOilwell)) return false;
        if (!Objects.equals(impUramine, cityBuild.impUramine)) return false;
        if (!Objects.equals(impLeadmine, cityBuild.impLeadmine))
            return false;
        if (!Objects.equals(impIronmine, cityBuild.impIronmine))
            return false;
        if (!Objects.equals(impBauxitemine, cityBuild.impBauxitemine))
            return false;
        if (!Objects.equals(impFarm, cityBuild.impFarm)) return false;
        if (!Objects.equals(impGasrefinery, cityBuild.impGasrefinery))
            return false;
        if (!Objects.equals(impAluminumrefinery, cityBuild.impAluminumrefinery))
            return false;
        if (!Objects.equals(impMunitionsfactory, cityBuild.impMunitionsfactory))
            return false;
        if (!Objects.equals(impSteelmill, cityBuild.impSteelmill))
            return false;
        if (!Objects.equals(impPolicestation, cityBuild.impPolicestation))
            return false;
        if (!Objects.equals(impHospital, cityBuild.impHospital))
            return false;
        if (!Objects.equals(impRecyclingcenter, cityBuild.impRecyclingcenter))
            return false;
        if (!Objects.equals(impSubway, cityBuild.impSubway)) return false;
        if (!Objects.equals(impSupermarket, cityBuild.impSupermarket))
            return false;
        if (!Objects.equals(impBank, cityBuild.impBank)) return false;
        if (!Objects.equals(impMall, cityBuild.impMall)) return false;
        if (!Objects.equals(impStadium, cityBuild.impStadium)) return false;
        if (!Objects.equals(impBarracks, cityBuild.impBarracks))
            return false;
        if (!Objects.equals(impFactory, cityBuild.impFactory)) return false;
        if (!Objects.equals(impHangars, cityBuild.impHangars)) return false;
        return Objects.equals(impDrydock, cityBuild.impDrydock);
    }

    @Override
    public int hashCode() {
        int result = infraNeeded != null ? infraNeeded.hashCode() : 0;
        result = 31 * result + (impTotal != null ? impTotal.hashCode() : 0);
        result = 31 * result + (impCoalpower != null ? impCoalpower.hashCode() : 0);
        result = 31 * result + (impOilpower != null ? impOilpower.hashCode() : 0);
        result = 31 * result + (impWindpower != null ? impWindpower.hashCode() : 0);
        result = 31 * result + (impNuclearpower != null ? impNuclearpower.hashCode() : 0);
        result = 31 * result + (impCoalmine != null ? impCoalmine.hashCode() : 0);
        result = 31 * result + (impOilwell != null ? impOilwell.hashCode() : 0);
        result = 31 * result + (impUramine != null ? impUramine.hashCode() : 0);
        result = 31 * result + (impLeadmine != null ? impLeadmine.hashCode() : 0);
        result = 31 * result + (impIronmine != null ? impIronmine.hashCode() : 0);
        result = 31 * result + (impBauxitemine != null ? impBauxitemine.hashCode() : 0);
        result = 31 * result + (impFarm != null ? impFarm.hashCode() : 0);
        result = 31 * result + (impGasrefinery != null ? impGasrefinery.hashCode() : 0);
        result = 31 * result + (impAluminumrefinery != null ? impAluminumrefinery.hashCode() : 0);
        result = 31 * result + (impMunitionsfactory != null ? impMunitionsfactory.hashCode() : 0);
        result = 31 * result + (impSteelmill != null ? impSteelmill.hashCode() : 0);
        result = 31 * result + (impPolicestation != null ? impPolicestation.hashCode() : 0);
        result = 31 * result + (impHospital != null ? impHospital.hashCode() : 0);
        result = 31 * result + (impRecyclingcenter != null ? impRecyclingcenter.hashCode() : 0);
        result = 31 * result + (impSubway != null ? impSubway.hashCode() : 0);
        result = 31 * result + (impSupermarket != null ? impSupermarket.hashCode() : 0);
        result = 31 * result + (impBank != null ? impBank.hashCode() : 0);
        result = 31 * result + (impMall != null ? impMall.hashCode() : 0);
        result = 31 * result + (impStadium != null ? impStadium.hashCode() : 0);
        result = 31 * result + (impBarracks != null ? impBarracks.hashCode() : 0);
        result = 31 * result + (impFactory != null ? impFactory.hashCode() : 0);
        result = 31 * result + (impHangars != null ? impHangars.hashCode() : 0);
        result = 31 * result + (impDrydock != null ? impDrydock.hashCode() : 0);
        return result;
    }
}