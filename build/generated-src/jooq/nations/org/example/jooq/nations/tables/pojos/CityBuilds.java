/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.nations.tables.pojos;


import java.io.Serializable;
import java.util.Arrays;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class CityBuilds implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Integer id;
    private final Integer nation;
    private final Long created;
    private final Integer infra;
    private final Integer land;
    private final Boolean powered;
    private final byte[] improvements;
    private final Long updateFlag;
    private final Long nukeDate;

    public CityBuilds(CityBuilds value) {
        this.id = value.id;
        this.nation = value.nation;
        this.created = value.created;
        this.infra = value.infra;
        this.land = value.land;
        this.powered = value.powered;
        this.improvements = value.improvements;
        this.updateFlag = value.updateFlag;
        this.nukeDate = value.nukeDate;
    }

    public CityBuilds(
        Integer id,
        Integer nation,
        Long created,
        Integer infra,
        Integer land,
        Boolean powered,
        byte[] improvements,
        Long updateFlag,
        Long nukeDate
    ) {
        this.id = id;
        this.nation = nation;
        this.created = created;
        this.infra = infra;
        this.land = land;
        this.powered = powered;
        this.improvements = improvements;
        this.updateFlag = updateFlag;
        this.nukeDate = nukeDate;
    }

    /**
     * Getter for <code>CITY_BUILDS.id</code>.
     */
    public Integer getId() {
        return this.id;
    }

    /**
     * Getter for <code>CITY_BUILDS.nation</code>.
     */
    public Integer getNation() {
        return this.nation;
    }

    /**
     * Getter for <code>CITY_BUILDS.created</code>.
     */
    public Long getCreated() {
        return this.created;
    }

    /**
     * Getter for <code>CITY_BUILDS.infra</code>.
     */
    public Integer getInfra() {
        return this.infra;
    }

    /**
     * Getter for <code>CITY_BUILDS.land</code>.
     */
    public Integer getLand() {
        return this.land;
    }

    /**
     * Getter for <code>CITY_BUILDS.powered</code>.
     */
    public Boolean getPowered() {
        return this.powered;
    }

    /**
     * Getter for <code>CITY_BUILDS.improvements</code>.
     */
    public byte[] getImprovements() {
        return this.improvements;
    }

    /**
     * Getter for <code>CITY_BUILDS.update_flag</code>.
     */
    public Long getUpdateFlag() {
        return this.updateFlag;
    }

    /**
     * Getter for <code>CITY_BUILDS.nuke_date</code>.
     */
    public Long getNukeDate() {
        return this.nukeDate;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final CityBuilds other = (CityBuilds) obj;
        if (this.id == null) {
            if (other.id != null)
                return false;
        }
        else if (!this.id.equals(other.id))
            return false;
        if (this.nation == null) {
            if (other.nation != null)
                return false;
        }
        else if (!this.nation.equals(other.nation))
            return false;
        if (this.created == null) {
            if (other.created != null)
                return false;
        }
        else if (!this.created.equals(other.created))
            return false;
        if (this.infra == null) {
            if (other.infra != null)
                return false;
        }
        else if (!this.infra.equals(other.infra))
            return false;
        if (this.land == null) {
            if (other.land != null)
                return false;
        }
        else if (!this.land.equals(other.land))
            return false;
        if (this.powered == null) {
            if (other.powered != null)
                return false;
        }
        else if (!this.powered.equals(other.powered))
            return false;
        if (this.improvements == null) {
            if (other.improvements != null)
                return false;
        }
        else if (!Arrays.equals(this.improvements, other.improvements))
            return false;
        if (this.updateFlag == null) {
            if (other.updateFlag != null)
                return false;
        }
        else if (!this.updateFlag.equals(other.updateFlag))
            return false;
        if (this.nukeDate == null) {
            if (other.nukeDate != null)
                return false;
        }
        else if (!this.nukeDate.equals(other.nukeDate))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.id == null) ? 0 : this.id.hashCode());
        result = prime * result + ((this.nation == null) ? 0 : this.nation.hashCode());
        result = prime * result + ((this.created == null) ? 0 : this.created.hashCode());
        result = prime * result + ((this.infra == null) ? 0 : this.infra.hashCode());
        result = prime * result + ((this.land == null) ? 0 : this.land.hashCode());
        result = prime * result + ((this.powered == null) ? 0 : this.powered.hashCode());
        result = prime * result + ((this.improvements == null) ? 0 : Arrays.hashCode(this.improvements));
        result = prime * result + ((this.updateFlag == null) ? 0 : this.updateFlag.hashCode());
        result = prime * result + ((this.nukeDate == null) ? 0 : this.nukeDate.hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CityBuilds (");

        sb.append(id);
        sb.append(", ").append(nation);
        sb.append(", ").append(created);
        sb.append(", ").append(infra);
        sb.append(", ").append(land);
        sb.append(", ").append(powered);
        sb.append(", ").append("[binary...]");
        sb.append(", ").append(updateFlag);
        sb.append(", ").append(nukeDate);

        sb.append(")");
        return sb.toString();
    }
}
