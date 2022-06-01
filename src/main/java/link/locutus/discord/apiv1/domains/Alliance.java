package link.locutus.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

public class Alliance extends Entity {
  @SerializedName("leaderids")
  @Expose
  private List<Integer> leaderIds = null;
  @SerializedName("success")
  @Expose
  private boolean success;
  @SerializedName("allianceid")
  @Expose
  private String allianceId;
  @SerializedName("name")
  @Expose
  private String name;
  @SerializedName("acronym")
  @Expose
  private String acronym;
  @SerializedName("score")
  @Expose
  private String score;
  @SerializedName("color")
  @Expose
  private String color;
  @SerializedName("members")
  @Expose
  private int members;
  @SerializedName("member_id_list")
  @Expose
  private List<Integer> memberIdList = null;
  @SerializedName("vmodemembers")
  @Expose
  private int vmModeMembers;
  @SerializedName("accepting members")
  @Expose
  private String acceptingMembers;
  @SerializedName("applicants")
  @Expose
  private int applicants;
  @SerializedName("flagurl")
  @Expose
  private String flagurl;
  @SerializedName("forumurl")
  @Expose
  private String forumurl;
  @SerializedName("irc")
  @Expose
  private String irc;
  @SerializedName("gdp")
  @Expose
  private double gdp;
  @SerializedName("cities")
  @Expose
  private int cities;
  @SerializedName("soldiers")
  @Expose
  private int soldiers;
  @SerializedName("tanks")
  @Expose
  private int tanks;
  @SerializedName("aircraft")
  @Expose
  private int aircraft;
  @SerializedName("ships")
  @Expose
  private int ships;
  @SerializedName("missiles")
  @Expose
  private int missiles;
  @SerializedName("nukes")
  @Expose
  private int nukes;
  @SerializedName("treasures")
  @Expose
  private int treasures;

  public List<Integer> getLeaderIds() {
    return leaderIds;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getAllianceId() {
    return allianceId;
  }

  public String getName() {
    return name;
  }

  public String getAcronym() {
    return acronym;
  }

  public String getScore() {
    return score;
  }

  public String getColor() {
    return color;
  }

  public int getMembers() {
    return members;
  }

  public List<Integer> getMemberIdList() {
    return memberIdList;
  }

  public int getVmModeMembers() {
    return vmModeMembers;
  }

  public String getAcceptingMembers() {
    return acceptingMembers;
  }

  public int getApplicants() {
    return applicants;
  }

  public String getFlagurl() {
    return flagurl;
  }

  public String getForumurl() {
    return forumurl;
  }

  public String getIrc() {
    return irc;
  }

  public double getGdp() {
    return gdp;
  }

  public int getCities() {
    return cities;
  }

  public int getSoldiers() {
    return soldiers;
  }

  public int getTanks() {
    return tanks;
  }

  public int getAircraft() {
    return aircraft;
  }

  public int getShips() {
    return ships;
  }

  public int getMissiles() {
    return missiles;
  }

  public int getNukes() {
    return nukes;
  }

  public int getTreasures() {
    return treasures;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Alliance alliance = (Alliance) o;
    return success == alliance.success &&
        members == alliance.members &&
        vmModeMembers == alliance.vmModeMembers &&
        applicants == alliance.applicants &&
        Double.compare(alliance.gdp, gdp) == 0 &&
        cities == alliance.cities &&
        soldiers == alliance.soldiers &&
        tanks == alliance.tanks &&
        aircraft == alliance.aircraft &&
        ships == alliance.ships &&
        missiles == alliance.missiles &&
        nukes == alliance.nukes &&
        treasures == alliance.treasures &&
        Objects.equals(leaderIds, alliance.leaderIds) &&
        Objects.equals(allianceId, alliance.allianceId) &&
        Objects.equals(name, alliance.name) &&
        Objects.equals(acronym, alliance.acronym) &&
        Objects.equals(score, alliance.score) &&
        Objects.equals(color, alliance.color) &&
        Objects.equals(memberIdList, alliance.memberIdList) &&
        Objects.equals(acceptingMembers, alliance.acceptingMembers) &&
        Objects.equals(flagurl, alliance.flagurl) &&
        Objects.equals(forumurl, alliance.forumurl) &&
        Objects.equals(irc, alliance.irc);
  }

  @Override
  public int hashCode() {
    return Objects.hash(leaderIds, success, allianceId, name, acronym, score, color, members, memberIdList, vmModeMembers, acceptingMembers, applicants, flagurl, forumurl, irc, gdp, cities, soldiers, tanks, aircraft, ships, missiles, nukes, treasures);
  }

  @Override
  public String toString() {
    return "Alliance{" +
        "leaderIds=" + leaderIds +
        ", success=" + success +
        ", allianceId='" + allianceId + '\'' +
        ", name='" + name + '\'' +
        ", acronym='" + acronym + '\'' +
        ", score='" + score + '\'' +
        ", color='" + color + '\'' +
        ", members=" + members +
        ", memberIdList=" + memberIdList +
        ", vmModeMembers=" + vmModeMembers +
        ", acceptingMembers='" + acceptingMembers + '\'' +
        ", applicants=" + applicants +
        ", flagurl='" + flagurl + '\'' +
        ", forumurl='" + forumurl + '\'' +
        ", irc='" + irc + '\'' +
        ", gdp=" + gdp +
        ", cities=" + cities +
        ", soldiers=" + soldiers +
        ", tanks=" + tanks +
        ", aircraft=" + aircraft +
        ", ships=" + ships +
        ", missiles=" + missiles +
        ", nukes=" + nukes +
        ", treasures=" + treasures +
        '}';
  }
}
