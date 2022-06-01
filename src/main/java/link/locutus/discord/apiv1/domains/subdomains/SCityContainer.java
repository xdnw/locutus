package link.locutus.discord.apiv1.domains.subdomains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class SCityContainer {
  @SerializedName("nation_id")
  @Expose
  private String nationId;
  @SerializedName("city_id")
  @Expose
  private String cityId;
  @SerializedName("city_name")
  @Expose
  private String cityName;
  @SerializedName("capital")
  @Expose
  private String capital;
  @SerializedName("infrastructure")
  @Expose
  private String infrastructure;
  @SerializedName("maxinfra")
  @Expose
  private String maxinfra;
  @SerializedName("land")
  @Expose
  private String land;

  public String getNationId() {
    return nationId;
  }

  public String getCityId() {
    return cityId;
  }

  public String getCityName() {
    return cityName;
  }

  public String getCapital() {
    return capital;
  }

  public String getInfrastructure() {
    return infrastructure;
  }

  public String getMaxinfra() {
    return maxinfra;
  }

  public String getLand() {
    return land;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SCityContainer that = (SCityContainer) o;
    return Objects.equals(nationId, that.nationId) &&
        Objects.equals(cityId, that.cityId) &&
        Objects.equals(cityName, that.cityName) &&
        Objects.equals(capital, that.capital) &&
        Objects.equals(infrastructure, that.infrastructure) &&
        Objects.equals(maxinfra, that.maxinfra) &&
        Objects.equals(land, that.land);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nationId, cityId, cityName, capital, infrastructure, maxinfra, land);
  }

  @Override
  public String toString() {
    return "SCityContainer{" +
        "nationId='" + nationId + '\'' +
        ", cityId='" + cityId + '\'' +
        ", cityName='" + cityName + '\'' +
        ", capital='" + capital + '\'' +
        ", infrastructure='" + infrastructure + '\'' +
        ", maxinfra='" + maxinfra + '\'' +
        ", land='" + land + '\'' +
        '}';
  }
}