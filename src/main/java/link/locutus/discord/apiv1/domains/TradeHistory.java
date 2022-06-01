package link.locutus.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import link.locutus.discord.apiv1.domains.subdomains.TradeContainer;

import java.util.List;
import java.util.Objects;

public class TradeHistory extends Entity {
  @SerializedName("success")
  @Expose
  private boolean success;
  @SerializedName("trades")
  @Expose
  private List<TradeContainer> tradeContainers = null;

  public boolean isSuccess() {
    return success;
  }

  public List<TradeContainer> getTrades() {
    return tradeContainers;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TradeHistory that = (TradeHistory) o;
    return success == that.success &&
        Objects.equals(tradeContainers, that.tradeContainers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, tradeContainers);
  }

  @Override
  public String toString() {
    return "TradeHistory{" +
        "success=" + success +
        ", tradeContainers=" + tradeContainers +
        '}';
  }
}
