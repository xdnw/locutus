package link.locutus.discord.apiv1.entities;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import static link.locutus.discord.apiv1.enums.ResourceType.*;

public class BankRecord {
    @SerializedName("tx_id")
    @Expose
    private Integer txId;
    @SerializedName("tx_datetime")
    @Expose
    private String txDatetime;
    @SerializedName("sender_id")
    @Expose
    private Integer senderId;
    @SerializedName("sender_type")
    @Expose
    private Integer senderType;
    @SerializedName("receiver_id")
    @Expose
    private Integer receiverId;
    @SerializedName("receiver_type")
    @Expose
    private Integer receiverType;
    @SerializedName("banker_nation_id")
    @Expose
    private Integer bankerNationId;
    @SerializedName("note")
    @Expose
    private String note;
    @SerializedName("money")
    @Expose
    private Double money;
    @SerializedName("coal")
    @Expose
    private Double coal;
    @SerializedName("oil")
    @Expose
    private Double oil;
    @SerializedName("uranium")
    @Expose
    private Double uranium;
    @SerializedName("iron")
    @Expose
    private Double iron;
    @SerializedName("bauxite")
    @Expose
    private Double bauxite;
    @SerializedName("lead")
    @Expose
    private Double lead;
    @SerializedName("gasoline")
    @Expose
    private Double gasoline;
    @SerializedName("munitions")
    @Expose
    private Double munitions;
    @SerializedName("steel")
    @Expose
    private Double steel;
    @SerializedName("aluminum")
    @Expose
    private Double aluminum;
    @SerializedName("food")
    @Expose
    private Double food;

    public Integer getTxId() {
        return txId;
    }

    public void setTxId(Integer txId) {
        this.txId = txId;
    }

    public String getTxDatetime() {
        return txDatetime;
    }

    public void setTxDatetime(String txDatetime) {
        this.txDatetime = txDatetime;
    }

    public Integer getSenderId() {
        return senderId;
    }

    public void setSenderId(Integer senderId) {
        this.senderId = senderId;
    }

    public Integer getSenderType() {
        return senderType;
    }

    public void setSenderType(Integer senderType) {
        this.senderType = senderType;
    }

    public Integer getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Integer receiverId) {
        this.receiverId = receiverId;
    }

    public Integer getReceiverType() {
        return receiverType;
    }

    public void setReceiverType(Integer receiverType) {
        this.receiverType = receiverType;
    }

    public Integer getBankerNationId() {
        return bankerNationId;
    }

    public void setBankerNationId(Integer bankerNationId) {
        this.bankerNationId = bankerNationId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Double getMoney() {
        return money;
    }

    public void setMoney(Double money) {
        this.money = money;
    }

    public Double getCoal() {
        return coal;
    }

    public void setCoal(Double coal) {
        this.coal = coal;
    }

    public Double getOil() {
        return oil;
    }

    public void setOil(Double oil) {
        this.oil = oil;
    }

    public Double getUranium() {
        return uranium;
    }

    public void setUranium(Double uranium) {
        this.uranium = uranium;
    }

    public Double getIron() {
        return iron;
    }

    public void setIron(Double iron) {
        this.iron = iron;
    }

    public Double getBauxite() {
        return bauxite;
    }

    public void setBauxite(Double bauxite) {
        this.bauxite = bauxite;
    }

    public Double getLead() {
        return lead;
    }

    public void setLead(Double lead) {
        this.lead = lead;
    }

    public Double getGasoline() {
        return gasoline;
    }

    public void setGasoline(Double gasoline) {
        this.gasoline = gasoline;
    }

    public Double getMunitions() {
        return munitions;
    }

    public void setMunitions(Double munitions) {
        this.munitions = munitions;
    }

    public Double getSteel() {
        return steel;
    }

    public void setSteel(Double steel) {
        this.steel = steel;
    }

    public Double getAluminum() {
        return aluminum;
    }

    public void setAluminum(Double aluminum) {
        this.aluminum = aluminum;
    }

    public Double getFood() {
        return food;
    }

    public void setFood(Double food) {
        this.food = food;
    }

    public double[] toMap() {
        double[] map = new double[values.length];
        if (money != null && money != 0) map[MONEY.ordinal()] = money;
        if (coal != null && coal != 0) map[COAL.ordinal()] =coal;
        if (oil != null && oil != 0) map[OIL.ordinal()] =oil;
        if (uranium != null && uranium != 0) map[URANIUM.ordinal()] =uranium;
        if (iron != null && iron != 0) map[IRON.ordinal()] =iron;
        if (bauxite != null && bauxite != 0) map[BAUXITE.ordinal()] =bauxite;
        if (lead != null && lead != 0) map[LEAD.ordinal()] =lead;
        if (gasoline != null && gasoline != 0) map[GASOLINE.ordinal()] =gasoline;
        if (munitions != null && munitions != 0) map[MUNITIONS.ordinal()] =munitions;
        if (steel != null && steel != 0) map[STEEL.ordinal()] =steel;
        if (aluminum != null && aluminum != 0) map[ALUMINUM.ordinal()] =aluminum;
        if (food != null && food != 0) map[FOOD.ordinal()] =food;
        return map;
    }
}
