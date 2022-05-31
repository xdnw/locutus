package com.boydti.discord.apiv1.domains.subdomains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class TradeContainer {
  @SerializedName("trade_id")
  @Expose
  private String tradeId;
  @SerializedName("date")
  @Expose
  private String date;
  @SerializedName("offerer_nation_id")
  @Expose
  private String offererNationId;
  @SerializedName("accepter_nation_id")
  @Expose
  private String accepterNationId;
  @SerializedName("resource")
  @Expose
  private String resource;
  @SerializedName("offer_type")
  @Expose
  private String offerType;
  @SerializedName("quantity")
  @Expose
  private String quantity;
  @SerializedName("price")
  @Expose
  private String price;

  public String getTradeId() {
    return tradeId;
  }

  public String getDate() {
    return date;
  }

  public String getOffererNationId() {
    return offererNationId;
  }

  public String getAccepterNationId() {
    return accepterNationId;
  }

  public String getResource() {
    return resource;
  }

  public String getOfferType() {
    return offerType;
  }

  public String getQuantity() {
    return quantity;
  }

  public String getPrice() {
    return price;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TradeContainer that = (TradeContainer) o;
    return Objects.equals(tradeId, that.tradeId) &&
        Objects.equals(date, that.date) &&
        Objects.equals(offererNationId, that.offererNationId) &&
        Objects.equals(accepterNationId, that.accepterNationId) &&
        Objects.equals(resource, that.resource) &&
        Objects.equals(offerType, that.offerType) &&
        Objects.equals(quantity, that.quantity) &&
        Objects.equals(price, that.price);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tradeId, date, offererNationId, accepterNationId, resource, offerType, quantity, price);
  }

  @Override
  public String toString() {
    return "TradeContainer{" +
        "tradeId='" + tradeId + '\'' +
        ", date='" + date + '\'' +
        ", offererNationId='" + offererNationId + '\'' +
        ", accepterNationId='" + accepterNationId + '\'' +
        ", resource='" + resource + '\'' +
        ", offerType='" + offerType + '\'' +
        ", quantity='" + quantity + '\'' +
        ", price='" + price + '\'' +
        '}';
  }
}
