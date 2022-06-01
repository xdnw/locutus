package link.locutus.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import link.locutus.discord.apiv1.domains.subdomains.MemberNationContainer;

import java.util.List;
import java.util.Objects;

public class Members extends Entity {
  @SerializedName("success")
  @Expose
  private boolean success;
  @SerializedName("nations")
  @Expose
  private List<MemberNationContainer> nations = null;

  public boolean isSuccess() {
    return success;
  }

  public List<MemberNationContainer> getNations() {
    return nations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Members members = (Members) o;
    return success == members.success &&
        Objects.equals(nations, members.nations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, nations);
  }

  @Override
  public String toString() {
    return "Members{" +
        "success=" + success +
        ", nations=" + nations +
        '}';
  }
}
