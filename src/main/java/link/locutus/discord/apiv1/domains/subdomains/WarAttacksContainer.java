package link.locutus.discord.apiv1.domains.subdomains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class WarAttacksContainer {
    @SerializedName("war_attack_id")
    @Expose
    private String warAttackId;
    @SerializedName("aircraft_killed_by_tanks")
    @Expose
    private double aircraft_killed_by_tanks;
    @SerializedName("date")
    @Expose
    private String date;
    @SerializedName("war_id")
    @Expose
    private String warId;
    @SerializedName("attacker_nation_id")
    @Expose
    private String attackerNationId;
    @SerializedName("defender_nation_id")
    @Expose
    private String defenderNationId;
    @SerializedName("attack_type")
    @Expose
    private String attackType;
    @SerializedName("victor")
    @Expose
    private String victor;
    @SerializedName("success")
    @Expose
    private String success;
    @SerializedName("attcas1")
    @Expose
    private String attcas1;
    @SerializedName("attcas2")
    @Expose
    private String attcas2;
    @SerializedName("defcas1")
    @Expose
    private String defcas1;
    @SerializedName("defcas2")
    @Expose
    private String defcas2;
    @SerializedName("city_id")
    @Expose
    private String cityId;
    @SerializedName("infra_destroyed")
    @Expose
    private String infraDestroyed;
    @SerializedName("improvements_destroyed")
    @Expose
    private String improvementsDestroyed;
    @SerializedName("money_looted")
    @Expose
    private String moneyLooted;
    @SerializedName("note")
    @Expose
    private String note;
    @SerializedName("city_infra_before")
    @Expose
    private String cityInfraBefore;
    @SerializedName("infra_destroyed_value")
    @Expose
    private String infraDestroyedValue;
    @SerializedName("att_gas_used")
    @Expose
    private String attGasUsed;
    @SerializedName("att_mun_used")
    @Expose
    private String attMunUsed;
    @SerializedName("def_gas_used")
    @Expose
    private String defGasUsed;
    @SerializedName("def_mun_used")
    @Expose
    private String defMunUsed;

    public double getAircraftKilledByTanks() {
        return aircraft_killed_by_tanks;
    }

    public String getWarAttackId() {
        return warAttackId;
    }

    public String getDate() {
        return date;
    }

    public String getWarId() {
        return warId;
    }

    public String getAttackerNationId() {
        return attackerNationId;
    }

    public String getDefenderNationId() {
        return defenderNationId;
    }

    public String getAttackType() {
        return attackType;
    }

    public String getVictor() {
        return victor;
    }

    public String getSuccess() {
        return success;
    }

    public String getAttcas1() {
        return attcas1;
    }

    public String getAttcas2() {
        return attcas2;
    }

    public String getDefcas1() {
        return defcas1;
    }

    public String getDefcas2() {
        return defcas2;
    }

    public String getCityId() {
        return cityId;
    }

    public String getInfraDestroyed() {
        return infraDestroyed;
    }

    public String getImprovementsDestroyed() {
        return improvementsDestroyed;
    }

    public String getMoneyLooted() {
        return moneyLooted;
    }

    public String getNote() {
        return note;
    }

    public String getCityInfraBefore() {
        return cityInfraBefore;
    }

    public String getInfraDestroyedValue() {
        return infraDestroyedValue;
    }

    public String getAttGasUsed() {
        return attGasUsed;
    }

    public String getAttMunUsed() {
        return attMunUsed;
    }

    public String getDefGasUsed() {
        return defGasUsed;
    }

    public String getDefMunUsed() {
        return defMunUsed;
    }

    @Override
    public String toString() {
        return "WarAttacksContainer{" +
                "warAttackId='" + warAttackId + '\'' +
                ", date='" + date + '\'' +
                ", warId='" + warId + '\'' +
                ", attackerNationId='" + attackerNationId + '\'' +
                ", defenderNationId='" + defenderNationId + '\'' +
                ", attackType='" + attackType + '\'' +
                ", victor='" + victor + '\'' +
                ", success='" + success + '\'' +
                ", attcas1='" + attcas1 + '\'' +
                ", attcas2='" + attcas2 + '\'' +
                ", defcas1='" + defcas1 + '\'' +
                ", defcas2='" + defcas2 + '\'' +
                ", cityId='" + cityId + '\'' +
                ", infraDestroyed='" + infraDestroyed + '\'' +
                ", improvementsDestroyed='" + improvementsDestroyed + '\'' +
                ", moneyLooted='" + moneyLooted + '\'' +
                ", note='" + note + '\'' +
                ", cityInfraBefore='" + cityInfraBefore + '\'' +
                ", infraDestroyedValue='" + infraDestroyedValue + '\'' +
                ", attGasUsed='" + attGasUsed + '\'' +
                ", attMunUsed='" + attMunUsed + '\'' +
                ", defGasUsed='" + defGasUsed + '\'' +
                ", defMunUsed='" + defMunUsed + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WarAttacksContainer that = (WarAttacksContainer) o;
        return Objects.equals(warAttackId, that.warAttackId) &&
                Objects.equals(date, that.date) &&
                Objects.equals(warId, that.warId) &&
                Objects.equals(attackerNationId, that.attackerNationId) &&
                Objects.equals(defenderNationId, that.defenderNationId) &&
                Objects.equals(attackType, that.attackType) &&
                Objects.equals(victor, that.victor) &&
                Objects.equals(success, that.success) &&
                Objects.equals(attcas1, that.attcas1) &&
                Objects.equals(attcas2, that.attcas2) &&
                Objects.equals(defcas1, that.defcas1) &&
                Objects.equals(defcas2, that.defcas2) &&
                Objects.equals(cityId, that.cityId) &&
                Objects.equals(infraDestroyed, that.infraDestroyed) &&
                Objects.equals(improvementsDestroyed, that.improvementsDestroyed) &&
                Objects.equals(moneyLooted, that.moneyLooted) &&
                Objects.equals(note, that.note) &&
                Objects.equals(cityInfraBefore, that.cityInfraBefore) &&
                Objects.equals(infraDestroyedValue, that.infraDestroyedValue) &&
                Objects.equals(attGasUsed, that.attGasUsed) &&
                Objects.equals(attMunUsed, that.attMunUsed) &&
                Objects.equals(defGasUsed, that.defGasUsed) &&
                Objects.equals(defMunUsed, that.defMunUsed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warAttackId, date, warId, attackerNationId, defenderNationId, attackType, victor, success, attcas1, attcas2, defcas1, defcas2, cityId, infraDestroyed, improvementsDestroyed, moneyLooted, note, cityInfraBefore, infraDestroyedValue, attGasUsed, attMunUsed, defGasUsed, defMunUsed);
    }
}
