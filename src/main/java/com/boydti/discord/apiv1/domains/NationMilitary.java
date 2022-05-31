package com.boydti.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.boydti.discord.apiv1.domains.subdomains.NationMilitaryContainer;

import java.util.List;
import java.util.Objects;

public class NationMilitary extends Entity {
  @SerializedName("success")
  @Expose
  private Boolean success;
  @SerializedName("nation_militaries")
  @Expose
  private List<NationMilitaryContainer> nationMilitaries = null;

  public Boolean isSuccess() {
    return success;
  }

  public List<NationMilitaryContainer> getNationMilitaries() {
    return nationMilitaries;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NationMilitary that = (NationMilitary) o;
    return Objects.equals(success, that.success) &&
        Objects.equals(nationMilitaries, that.nationMilitaries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, nationMilitaries);
  }

  @Override
  public String toString() {
    return "NationMilitary{" +
        "success=" + success +
        ", nationMilitaries=" + nationMilitaries +
        '}';
  }
}
