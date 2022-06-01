package link.locutus.discord.apiv1.domains.subdomains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

public class SAllianceContainer {
  @SerializedName("id")
  @Expose
  private String id;
  @SerializedName("founddate")
  @Expose
  private String founddate;
  @SerializedName("name")
  @Expose
  private String name;
  @SerializedName("acronym")
  @Expose
  private String acronym;
  @SerializedName("color")
  @Expose
  private String color;
  @SerializedName("rank")
  @Expose
  private int rank;
  @SerializedName("members")
  @Expose
  private int members;
  @SerializedName("score")
  @Expose
  private double score;
  @SerializedName("officerids")
  @Expose
  private List<String> officerids = null;
  @SerializedName("leaderids")
  @Expose
  private List<String> leaderids = null;
  @SerializedName("heirids")
  @Expose
  private List<String> heirids = null;
  @SerializedName("avgscore")
  @Expose
  private double avgscore;
  @SerializedName("flagurl")
  @Expose
  private String flagurl;
  @SerializedName("forumurl")
  @Expose
  private String forumurl;
  @SerializedName("ircchan")
  @Expose
  private String ircchan;

  public String getId() {
    return id;
  }

  public String getFounddate() {
    return founddate;
  }

  public String getName() {
    return name;
  }

  public String getAcronym() {
    return acronym;
  }

  public String getColor() {
    return color;
  }

  public int getRank() {
    return rank;
  }

  public int getMembers() {
    return members;
  }

  public double getScore() {
    return score;
  }

  public List<String> getOfficerids() {
    return officerids;
  }

  public List<String> getLeaderids() {
    return leaderids;
  }

  public List<String> getHeirids() {
    return heirids;
  }

  public double getAvgscore() {
    return avgscore;
  }

  public String getFlagurl() {
    return flagurl;
  }

  public String getForumurl() {
    return forumurl;
  }

  public String getIrcchan() {
    return ircchan;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SAllianceContainer that = (SAllianceContainer) o;
    return rank == that.rank &&
        members == that.members &&
        Double.compare(that.score, score) == 0 &&
        Double.compare(that.avgscore, avgscore) == 0 &&
        Objects.equals(id, that.id) &&
        Objects.equals(founddate, that.founddate) &&
        Objects.equals(name, that.name) &&
        Objects.equals(acronym, that.acronym) &&
        Objects.equals(color, that.color) &&
        Objects.equals(officerids, that.officerids) &&
        Objects.equals(leaderids, that.leaderids) &&
        Objects.equals(heirids, that.heirids) &&
        Objects.equals(flagurl, that.flagurl) &&
        Objects.equals(forumurl, that.forumurl) &&
        Objects.equals(ircchan, that.ircchan);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, founddate, name, acronym, color, rank, members, score, officerids, leaderids, heirids, avgscore, flagurl, forumurl, ircchan);
  }

  @Override
  public String toString() {
    return "SAllianceContainer{" +
        "id='" + id + '\'' +
        ", founddate='" + founddate + '\'' +
        ", name='" + name + '\'' +
        ", acronym='" + acronym + '\'' +
        ", color='" + color + '\'' +
        ", rank=" + rank +
        ", members=" + members +
        ", score=" + score +
        ", officerids=" + officerids +
        ", leaderids=" + leaderids +
        ", heirids=" + heirids +
        ", avgscore=" + avgscore +
        ", flagurl='" + flagurl + '\'' +
        ", forumurl='" + forumurl + '\'' +
        ", ircchan='" + ircchan + '\'' +
        '}';
  }
}
