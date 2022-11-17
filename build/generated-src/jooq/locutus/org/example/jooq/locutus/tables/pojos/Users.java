/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.locutus.tables.pojos;


import java.io.Serializable;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Users implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Integer nationId;
    private final Long discordId;
    private final String discordName;

    public Users(Users value) {
        this.nationId = value.nationId;
        this.discordId = value.discordId;
        this.discordName = value.discordName;
    }

    public Users(
        Integer nationId,
        Long discordId,
        String discordName
    ) {
        this.nationId = nationId;
        this.discordId = discordId;
        this.discordName = discordName;
    }

    /**
     * Getter for <code>USERS.nation_id</code>.
     */
    public Integer getNationId() {
        return this.nationId;
    }

    /**
     * Getter for <code>USERS.discord_id</code>.
     */
    public Long getDiscordId() {
        return this.discordId;
    }

    /**
     * Getter for <code>USERS.discord_name</code>.
     */
    public String getDiscordName() {
        return this.discordName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Users other = (Users) obj;
        if (this.nationId == null) {
            if (other.nationId != null)
                return false;
        }
        else if (!this.nationId.equals(other.nationId))
            return false;
        if (this.discordId == null) {
            if (other.discordId != null)
                return false;
        }
        else if (!this.discordId.equals(other.discordId))
            return false;
        if (this.discordName == null) {
            if (other.discordName != null)
                return false;
        }
        else if (!this.discordName.equals(other.discordName))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.nationId == null) ? 0 : this.nationId.hashCode());
        result = prime * result + ((this.discordId == null) ? 0 : this.discordId.hashCode());
        result = prime * result + ((this.discordName == null) ? 0 : this.discordName.hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Users (");

        sb.append(nationId);
        sb.append(", ").append(discordId);
        sb.append(", ").append(discordName);

        sb.append(")");
        return sb.toString();
    }
}