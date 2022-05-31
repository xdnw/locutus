package com.boydti.discord.apiv1.domains.subdomains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class HighestbuyContainer {
  @SerializedName("date")
  @Expose
  private String date;
  @SerializedName("nationid")
  @Expose
  private String nationid;
  @SerializedName("amount")
  @Expose
  private String amount;
  @SerializedName("price")
  @Expose
  private String price;
  @SerializedName("totalvalue")
  @Expose
  private int totalvalue;

  public String getDate() {
    return date;
  }

  public String getNationid() {
    return nationid;
  }

  public String getAmount() {
    return amount;
  }

  public String getPrice() {
    return price;
  }

  public int getTotalvalue() {
    return totalvalue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HighestbuyContainer that = (HighestbuyContainer) o;
    return totalvalue == that.totalvalue &&
        Objects.equals(date, that.date) &&
        Objects.equals(nationid, that.nationid) &&
        Objects.equals(amount, that.amount) &&
        Objects.equals(price, that.price);
  }

  @Override
  public int hashCode() {
    return Objects.hash(date, nationid, amount, price, totalvalue);
  }

  @Override
  public String toString() {
    return "HighestbuyContainer{" +
        "date='" + date + '\'' +
        ", nationid='" + nationid + '\'' +
        ", amount='" + amount + '\'' +
        ", price='" + price + '\'' +
        ", totalvalue=" + totalvalue +
        '}';
  }
}
