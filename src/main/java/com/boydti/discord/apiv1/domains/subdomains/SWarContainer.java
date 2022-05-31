package com.boydti.discord.apiv1.domains.subdomains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class SWarContainer {
  @SerializedName("warID")
  @Expose
  private int warID;
  @SerializedName("attackerID")
  @Expose
  private int attackerID;
  @SerializedName("defenderID")
  @Expose
  private int defenderID;
  @SerializedName("attackerAA")
  @Expose
  private String attackerAA;
  @SerializedName("defenderAA")
  @Expose
  private String defenderAA;
  @SerializedName("war_type")
  @Expose
  private String warType;
  @SerializedName("status")
  @Expose
  private String status;
  @SerializedName("date")
  @Expose
  private String date;

  public int getWarID() {
    return warID;
  }

  public int getAttackerID() {
    return attackerID;
  }

  public int getDefenderID() {
    return defenderID;
  }

  public String getAttackerAA() {
    return attackerAA;
  }

  public String getDefenderAA() {
    return defenderAA;
  }

  public String getWarType() {
    return warType;
  }

  public String getStatus() {
    return status;
  }

  public String getDate() {
    return date;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SWarContainer that = (SWarContainer) o;
    return warID == that.warID &&
        attackerID == that.attackerID &&
        defenderID == that.defenderID &&
        Objects.equals(attackerAA, that.attackerAA) &&
        Objects.equals(defenderAA, that.defenderAA) &&
        Objects.equals(warType, that.warType) &&
        Objects.equals(status, that.status) &&
        Objects.equals(date, that.date);
  }

  @Override
  public int hashCode() {
    return Objects.hash(warID, attackerID, defenderID, attackerAA, defenderAA, warType, status, date);
  }

  @Override
  public String toString() {
    return "SWarContainer{" +
        "warID=" + warID +
        ", attackerID=" + attackerID +
        ", defenderID=" + defenderID +
        ", attackerAA='" + attackerAA + '\'' +
        ", defenderAA='" + defenderAA + '\'' +
        ", warType='" + warType + '\'' +
        ", status='" + status + '\'' +
        ", date='" + date + '\'' +
        '}';
  }
}
