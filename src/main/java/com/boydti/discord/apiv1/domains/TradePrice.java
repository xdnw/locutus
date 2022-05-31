package com.boydti.discord.apiv1.domains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.boydti.discord.apiv1.domains.subdomains.HighestbuyContainer;
import com.boydti.discord.apiv1.domains.subdomains.LowestbuyContainer;

import java.util.Objects;

public class TradePrice extends Entity {
  @SerializedName("resource")
  @Expose
  private String resource;
  @SerializedName("avgprice")
  @Expose
  private String avgprice;
  @SerializedName("marketindex")
  @Expose
  private String marketindex;
  @SerializedName("highestbuy")
  @Expose
  private HighestbuyContainer highestbuyContainer;
  @SerializedName("lowestbuy")
  @Expose
  private LowestbuyContainer lowestbuyContainer;

  public String getResource() {
    return resource;
  }

  public String getAvgprice() {
    return avgprice;
  }

  public String getMarketindex() {
    return marketindex;
  }

  public HighestbuyContainer getHighestbuy() {
    return highestbuyContainer;
  }

  public LowestbuyContainer getLowestbuy() {
    return lowestbuyContainer;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TradePrice that = (TradePrice) o;
    return Objects.equals(resource, that.resource) &&
        Objects.equals(avgprice, that.avgprice) &&
        Objects.equals(marketindex, that.marketindex) &&
        Objects.equals(highestbuyContainer, that.highestbuyContainer) &&
        Objects.equals(lowestbuyContainer, that.lowestbuyContainer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(resource, avgprice, marketindex, highestbuyContainer, lowestbuyContainer);
  }

  @Override
  public String toString() {
    return "TradePrice{" +
        "resource='" + resource + '\'' +
        ", avgprice='" + avgprice + '\'' +
        ", marketindex='" + marketindex + '\'' +
        ", highestbuyContainer=" + highestbuyContainer +
        ", lowestbuyContainer=" + lowestbuyContainer +
        '}';
  }
}
