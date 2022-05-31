package com.boydti.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.boydti.discord.apiv1.domains.subdomains.SWarContainer;

import java.util.List;
import java.util.Objects;

public class Wars extends Entity {
  @SerializedName("success")
  @Expose
  private boolean success;
  @SerializedName("wars")
  @Expose
  private List<SWarContainer> wars = null;

  public boolean isSuccess() {
    return success;
  }

  public List<SWarContainer> getWars() {
    return wars;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Wars wars1 = (Wars) o;
    return success == wars1.success &&
        Objects.equals(wars, wars1.wars);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, wars);
  }

  @Override
  public String toString() {
    return "Wars{" +
        "success=" + success +
        ", wars=" + wars +
        '}';
  }
}
