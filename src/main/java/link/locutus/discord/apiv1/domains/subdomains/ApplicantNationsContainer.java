package link.locutus.discord.apiv1.domains.subdomains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class ApplicantNationsContainer {
  @SerializedName("nationid")
  @Expose
  private int nationid;
  @SerializedName("nation")
  @Expose
  private String nation;
  @SerializedName("leader")
  @Expose
  private String leader;
  @SerializedName("continent")
  @Expose
  private String continent;
  @SerializedName("cities")
  @Expose
  private int cities;
  @SerializedName("score")
  @Expose
  private int score;

  public int getNationid() {
    return nationid;
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

  public int getCities() {
    return cities;
  }

  public int getScore() {
    return score;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ApplicantNationsContainer that = (ApplicantNationsContainer) o;
    return nationid == that.nationid &&
        cities == that.cities &&
        score == that.score &&
        Objects.equals(nation, that.nation) &&
        Objects.equals(leader, that.leader) &&
        Objects.equals(continent, that.continent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nationid, nation, leader, continent, cities, score);
  }

  @Override
  public String toString() {
    return "ApplicantNationsContainer{" +
        "nationid=" + nationid +
        ", nation='" + nation + '\'' +
        ", leader='" + leader + '\'' +
        ", continent='" + continent + '\'' +
        ", cities=" + cities +
        ", score=" + score +
        '}';
  }
}
