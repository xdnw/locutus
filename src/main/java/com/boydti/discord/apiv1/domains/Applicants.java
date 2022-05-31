package com.boydti.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.boydti.discord.apiv1.domains.subdomains.ApplicantNationsContainer;

import java.util.List;
import java.util.Objects;

public class Applicants extends Entity {
  @SerializedName("success")
  @Expose
  private boolean success;
  @SerializedName("nations")
  @Expose
  private List<ApplicantNationsContainer> applicants = null;

  public boolean isSuccess() {
    return success;
  }

  public List<ApplicantNationsContainer> getApplicants() {
    return applicants;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Applicants that = (Applicants) o;
    return success == that.success &&
        Objects.equals(applicants, that.applicants);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, applicants);
  }

  @Override
  public String toString() {
    return "Applicants{" +
        "success=" + success +
        ", applicants=" + applicants +
        '}';
  }
}
