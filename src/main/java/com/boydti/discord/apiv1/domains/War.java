package com.boydti.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.boydti.discord.apiv1.domains.subdomains.WarContainer;

import java.util.List;
import java.util.Objects;

public class War extends Entity {
  @SerializedName("success")
  @Expose
  private boolean success;
  @SerializedName("war")
  @Expose
  private List<WarContainer> war = null;

  public boolean isSuccess() {
    return success;
  }

  public List<WarContainer> getWar() {
    return war;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    War war1 = (War) o;
    return success == war1.success &&
        Objects.equals(war, war1.war);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, war);
  }

  @Override
  public String toString() {
    return "War{" +
        "success=" + success +
        ", war=" + war +
        '}';
  }
}
