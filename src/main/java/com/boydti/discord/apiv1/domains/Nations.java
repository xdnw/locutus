package com.boydti.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.boydti.discord.apiv1.domains.subdomains.SNationContainer;

import java.util.List;
import java.util.Objects;

public class Nations extends Entity {
  @SerializedName("success")
  @Expose
  private boolean success;
  @SerializedName("nations")
  @Expose
  private List<SNationContainer> nations = null;

  public boolean isSuccess() {
    return success;
  }

  public List<SNationContainer> getNationsContainer() {
    return nations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Nations nations1 = (Nations) o;
    return success == nations1.success &&
        Objects.equals(nations, nations1.nations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, nations);
  }

  @Override
  public String toString() {
    return "Nations{" +
        "success=" + success +
        ", nations=" + nations +
        '}';
  }
}
