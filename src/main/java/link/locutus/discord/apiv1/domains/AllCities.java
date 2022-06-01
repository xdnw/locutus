package link.locutus.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import link.locutus.discord.apiv1.domains.subdomains.SCityContainer;

import java.util.List;
import java.util.Objects;

public class AllCities extends Entity {
  @SerializedName("success")
  @Expose
  private Boolean success;
  @SerializedName("all_cities")
  @Expose
  private List<SCityContainer> allCities = null;

  public Boolean isSuccess() {
    return success;
  }

  public List<SCityContainer> getAllCities() {
    return allCities;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AllCities allCities1 = (AllCities) o;
    return Objects.equals(success, allCities1.success) &&
        Objects.equals(allCities, allCities1.allCities);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, allCities);
  }

  @Override
  public String toString() {
    return "AllCities{" +
        "success=" + success +
        ", allCities=" + allCities +
        '}';
  }
}
