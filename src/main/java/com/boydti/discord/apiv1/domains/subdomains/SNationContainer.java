package com.boydti.discord.apiv1.domains.subdomains;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.Objects;

public class SNationContainer {
    @SerializedName("nationid")
    @Expose
    private Integer nationid;
    @SerializedName("nation")
    @Expose
    private String nation;
    @SerializedName("leader")
    @Expose
    private String leader;
    @SerializedName("continent")
    @Expose
    private String continent;
    @SerializedName("war_policy")
    @Expose
    private String warPolicy;
    @SerializedName("color")
    @Expose
    private String color;
    @SerializedName("alliance")
    @Expose
    private String alliance;
    @SerializedName("allianceid")
    @Expose
    private Integer allianceid;
    @SerializedName("allianceposition")
    @Expose
    private Integer allianceposition;
    @SerializedName("cities")
    @Expose
    private Integer cities;
    @SerializedName("infrastructure")
    @Expose
    private Double infrastructure;
    @SerializedName("offensivewars")
    @Expose
    private Integer offensivewars;
    @SerializedName("defensivewars")
    @Expose
    private Integer defensivewars;
    @SerializedName("score")
    @Expose
    private Double score;
    @SerializedName("rank")
    @Expose
    private Integer rank;
    @SerializedName("vacmode")
    @Expose
    private Integer vacmode;
    @SerializedName("minutessinceactive")
    @Expose
    private Integer minutessinceactive;

    public SNationContainer() {
    }

    public Integer getNationid() {
        return this.nationid;
    }

    public String getNation() {
        return this.nation;
    }

    public String getLeader() {
        return this.leader;
    }

    public String getContinent() {
        return this.continent;
    }

    public String getWarPolicy() {
        return this.warPolicy;
    }

    public String getColor() {
        return this.color;
    }

    public String getAlliance() {
        return this.alliance;
    }

    public Integer getAllianceid() {
        return this.allianceid;
    }

    public Integer getAllianceposition() {
        return this.allianceposition;
    }

    public Integer getCities() {
        return this.cities;
    }

    public Integer getOffensivewars() {
        return this.offensivewars;
    }

    public Integer getDefensivewars() {
        return this.defensivewars;
    }

    public Double getScore() {
        return this.score;
    }

    public Integer getRank() {
        return this.rank;
    }

    public Integer getVacmode() {
        return this.vacmode;
    }

    public Integer getMinutessinceactive() {
        return this.minutessinceactive;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            SNationContainer container = (SNationContainer)o;
            return this.nationid == container.nationid && this.allianceid == container.allianceid && this.allianceposition == container.allianceposition && this.cities == container.cities && this.offensivewars == container.offensivewars && this.defensivewars == container.defensivewars && Double.compare(container.score, this.score) == 0 && this.rank == container.rank && this.minutessinceactive == container.minutessinceactive && Objects.equals(this.nation, container.nation) && Objects.equals(this.leader, container.leader) && Objects.equals(this.continent, container.continent) && Objects.equals(this.warPolicy, container.warPolicy) && Objects.equals(this.color, container.color) && Objects.equals(this.alliance, container.alliance) && Objects.equals(this.vacmode, container.vacmode);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(this.nationid, this.nation, this.leader, this.continent, this.warPolicy, this.color, this.alliance, this.allianceid, this.allianceposition, this.cities, this.offensivewars, this.defensivewars, this.score, this.rank, this.vacmode, this.minutessinceactive);
    }

    public String toString() {
        return "SNationContainer{nationid=" + this.nationid + ", nation='" + this.nation + "', leader='" + this.leader + "', continent='" + this.continent + "', warPolicy='" + this.warPolicy + "', color='" + this.color + "', alliance='" + this.alliance + "', allianceid=" + this.allianceid + ", allianceposition=" + this.allianceposition + ", cities=" + this.cities + ", offensivewars=" + this.offensivewars + ", defensivewars=" + this.defensivewars + ", score=" + this.score + ", rank=" + this.rank + ", vacmode='" + this.vacmode + "', minutessinceactive=" + this.minutessinceactive + "}";
    }

    public Double getInfrastructure() {
        return this.infrastructure;
    }

    public void setInfrastructure(Double infrastructure) {
        this.infrastructure = infrastructure;
    }
}
