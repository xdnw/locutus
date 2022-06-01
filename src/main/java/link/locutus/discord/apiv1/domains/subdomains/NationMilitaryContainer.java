package link.locutus.discord.apiv1.domains.subdomains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class NationMilitaryContainer {
  @SerializedName("nation_id")
  @Expose
  private Integer nationId;
  @SerializedName("vm_indicator")
  @Expose
  private Integer vmIndicator;
  @SerializedName("score")
  @Expose
  private Double score;
  @SerializedName("soldiers")
  @Expose
  private Integer soldiers;
  @SerializedName("tanks")
  @Expose
  private Integer tanks;
  @SerializedName("aircraft")
  @Expose
  private Integer aircraft;
  @SerializedName("ships")
  @Expose
  private Integer ships;
  @SerializedName("missiles")
  @Expose
  private Integer missiles;
  @SerializedName("nukes")
  @Expose
  private Integer nukes;
  @SerializedName("alliance")
  @Expose
  private String alliance;
  @SerializedName("alliance_id")
  @Expose
  private Integer allianceId;
  @SerializedName("alliance_position")
  @Expose
  private Integer alliancePosition;

  public Integer getNationId() {
    return nationId;
  }

  public Integer getVmIndicator() {
    return vmIndicator;
  }

  public Double getScore() {
    return score;
  }

  public Integer getSoldiers() {
    return soldiers;
  }

  public Integer getTanks() {
    return tanks;
  }

  public Integer getAircraft() {
    return aircraft;
  }

  public Integer getShips() {
    return ships;
  }

  public Integer getMissiles() {
    return missiles;
  }

  public Integer getNukes() {
    return nukes;
  }

  public String getAlliance() {
    return alliance;
  }

  public Integer getAllianceId() {
    return allianceId;
  }

  public Integer getAlliancePosition() {
    return alliancePosition;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NationMilitaryContainer that = (NationMilitaryContainer) o;
    return Objects.equals(nationId, that.nationId) &&
        Objects.equals(vmIndicator, that.vmIndicator) &&
        Objects.equals(score, that.score) &&
        Objects.equals(soldiers, that.soldiers) &&
        Objects.equals(tanks, that.tanks) &&
        Objects.equals(aircraft, that.aircraft) &&
        Objects.equals(ships, that.ships) &&
        Objects.equals(missiles, that.missiles) &&
        Objects.equals(nukes, that.nukes) &&
        Objects.equals(alliance, that.alliance) &&
        Objects.equals(allianceId, that.allianceId) &&
        Objects.equals(alliancePosition, that.alliancePosition);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nationId, vmIndicator, score, soldiers, tanks, aircraft, ships, missiles, nukes, alliance, allianceId, alliancePosition);
  }

  @Override
  public String toString() {
    return "NationMilitaryContainer{" +
        "nationId=" + nationId +
        ", vmIndicator=" + vmIndicator +
        ", score=" + score +
        ", soldiers=" + soldiers +
        ", tanks=" + tanks +
        ", aircraft=" + aircraft +
        ", ships=" + ships +
        ", missiles=" + missiles +
        ", nukes=" + nukes +
        ", alliance='" + alliance + '\'' +
        ", allianceId=" + allianceId +
        ", alliancePosition=" + alliancePosition +
        '}';
  }
}
