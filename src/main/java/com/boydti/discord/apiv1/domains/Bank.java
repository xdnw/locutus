package com.boydti.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.boydti.discord.apiv1.domains.subdomains.AllianceBankContainer;

import java.util.List;
import java.util.Objects;

public class Bank extends Entity {
  @SerializedName("success")
  @Expose
  private boolean success;
  @SerializedName("alliance_bank_contents")
  @Expose
  private List<AllianceBankContainer> allianceBankContainers = null;

  public boolean isSuccess() {
    return success;
  }

  public List<AllianceBankContainer> getAllianceBanks() {
    return allianceBankContainers;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Bank bank = (Bank) o;
    return success == bank.success &&
        Objects.equals(allianceBankContainers, bank.allianceBankContainers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, allianceBankContainers);
  }

  @Override
  public String toString() {
    return "Bank{" +
        "success=" + success +
        ", allianceBankContainers=" + allianceBankContainers +
        '}';
  }
}
