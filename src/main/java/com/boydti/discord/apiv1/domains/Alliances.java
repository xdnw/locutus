package com.boydti.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.boydti.discord.apiv1.domains.subdomains.SAllianceContainer;

import java.util.List;
import java.util.Objects;

public class Alliances extends Entity {
  @SerializedName("success")
  @Expose
  private boolean success;
  @SerializedName("alliances")
  @Expose
  private List<SAllianceContainer> alliances = null;

  public boolean isSuccess() {
    return success;
  }

  public List<SAllianceContainer> getAlliances() {
    return alliances;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Alliances alliances1 = (Alliances) o;
    return success == alliances1.success &&
        Objects.equals(alliances, alliances1.alliances);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, alliances);
  }

  @Override
  public String toString() {
    return "Alliances{" +
        "success=" + success +
        ", alliances=" + alliances +
        '}';
  }
}
