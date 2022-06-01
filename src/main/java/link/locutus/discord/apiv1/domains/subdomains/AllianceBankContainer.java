package link.locutus.discord.apiv1.domains.subdomains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class AllianceBankContainer {
  @SerializedName("alliance_id")
  @Expose
  private int allianceId;
  @SerializedName("name")
  @Expose
  private String name;
  @SerializedName("taxrate")
  @Expose
  private int taxrate;
  @SerializedName("resource_taxrate")
  @Expose
  private int resourceTaxrate;
  @SerializedName("money")
  @Expose
  private double money;
  @SerializedName("food")
  @Expose
  private double food;
  @SerializedName("coal")
  @Expose
  private double coal;
  @SerializedName("oil")
  @Expose
  private double oil;
  @SerializedName("uranium")
  @Expose
  private double uranium;
  @SerializedName("iron")
  @Expose
  private double iron;
  @SerializedName("bauxite")
  @Expose
  private double bauxite;
  @SerializedName("lead")
  @Expose
  private double lead;
  @SerializedName("gasoline")
  @Expose
  private double gasoline;
  @SerializedName("munitions")
  @Expose
  private double munitions;
  @SerializedName("steel")
  @Expose
  private double steel;
  @SerializedName("aluminum")
  @Expose
  private double aluminum;

  public int getAllianceId() {
    return allianceId;
  }

  public String getName() {
    return name;
  }

  public int getTaxrate() {
    return taxrate;
  }

  public int getResourceTaxrate() {
    return resourceTaxrate;
  }

  public double getMoney() {
    return money;
  }

  public double getFood() {
    return food;
  }

  public double getCoal() {
    return coal;
  }

  public double getOil() {
    return oil;
  }

  public double getUranium() {
    return uranium;
  }

  public double getIron() {
    return iron;
  }

  public double getBauxite() {
    return bauxite;
  }

  public double getLead() {
    return lead;
  }

  public double getGasoline() {
    return gasoline;
  }

  public double getMunitions() {
    return munitions;
  }

  public double getSteel() {
    return steel;
  }

  public double getAluminum() {
    return aluminum;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AllianceBankContainer that = (AllianceBankContainer) o;
    return allianceId == that.allianceId &&
        taxrate == that.taxrate &&
        resourceTaxrate == that.resourceTaxrate &&
        Double.compare(that.money, money) == 0 &&
        Double.compare(that.food, food) == 0 &&
        Double.compare(that.coal, coal) == 0 &&
        Double.compare(that.oil, oil) == 0 &&
        Double.compare(that.uranium, uranium) == 0 &&
        Double.compare(that.iron, iron) == 0 &&
        Double.compare(that.bauxite, bauxite) == 0 &&
        Double.compare(that.lead, lead) == 0 &&
        Double.compare(that.gasoline, gasoline) == 0 &&
        Double.compare(that.munitions, munitions) == 0 &&
        Double.compare(that.steel, steel) == 0 &&
        Double.compare(that.aluminum, aluminum) == 0 &&
        Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(allianceId, name, taxrate, resourceTaxrate, money, food, coal, oil, uranium, iron, bauxite, lead, gasoline, munitions, steel, aluminum);
  }

  @Override
  public String toString() {
    return "AllianceBankContainer{" +
        "allianceId=" + allianceId +
        ", name='" + name + '\'' +
        ", taxrate=" + taxrate +
        ", resourceTaxrate=" + resourceTaxrate +
        ", money=" + money +
        ", food=" + food +
        ", coal=" + coal +
        ", oil=" + oil +
        ", uranium=" + uranium +
        ", iron=" + iron +
        ", bauxite=" + bauxite +
        ", lead=" + lead +
        ", gasoline=" + gasoline +
        ", munitions=" + munitions +
        ", steel=" + steel +
        ", aluminum=" + aluminum +
        '}';
  }
}
