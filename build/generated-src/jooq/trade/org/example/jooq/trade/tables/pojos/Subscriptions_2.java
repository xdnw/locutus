/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.trade.tables.pojos;


import java.io.Serializable;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Subscriptions_2 implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Long user;
    private final Integer resource;
    private final Long date;
    private final Integer isbuy;
    private final Integer above;
    private final Integer ppu;
    private final Integer type;

    public Subscriptions_2(Subscriptions_2 value) {
        this.user = value.user;
        this.resource = value.resource;
        this.date = value.date;
        this.isbuy = value.isbuy;
        this.above = value.above;
        this.ppu = value.ppu;
        this.type = value.type;
    }

    public Subscriptions_2(
        Long user,
        Integer resource,
        Long date,
        Integer isbuy,
        Integer above,
        Integer ppu,
        Integer type
    ) {
        this.user = user;
        this.resource = resource;
        this.date = date;
        this.isbuy = isbuy;
        this.above = above;
        this.ppu = ppu;
        this.type = type;
    }

    /**
     * Getter for <code>SUBSCRIPTIONS_2.user</code>.
     */
    public Long getUser() {
        return this.user;
    }

    /**
     * Getter for <code>SUBSCRIPTIONS_2.resource</code>.
     */
    public Integer getResource() {
        return this.resource;
    }

    /**
     * Getter for <code>SUBSCRIPTIONS_2.date</code>.
     */
    public Long getDate() {
        return this.date;
    }

    /**
     * Getter for <code>SUBSCRIPTIONS_2.isBuy</code>.
     */
    public Integer getIsbuy() {
        return this.isbuy;
    }

    /**
     * Getter for <code>SUBSCRIPTIONS_2.above</code>.
     */
    public Integer getAbove() {
        return this.above;
    }

    /**
     * Getter for <code>SUBSCRIPTIONS_2.ppu</code>.
     */
    public Integer getPpu() {
        return this.ppu;
    }

    /**
     * Getter for <code>SUBSCRIPTIONS_2.type</code>.
     */
    public Integer getType() {
        return this.type;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Subscriptions_2 other = (Subscriptions_2) obj;
        if (this.user == null) {
            if (other.user != null)
                return false;
        }
        else if (!this.user.equals(other.user))
            return false;
        if (this.resource == null) {
            if (other.resource != null)
                return false;
        }
        else if (!this.resource.equals(other.resource))
            return false;
        if (this.date == null) {
            if (other.date != null)
                return false;
        }
        else if (!this.date.equals(other.date))
            return false;
        if (this.isbuy == null) {
            if (other.isbuy != null)
                return false;
        }
        else if (!this.isbuy.equals(other.isbuy))
            return false;
        if (this.above == null) {
            if (other.above != null)
                return false;
        }
        else if (!this.above.equals(other.above))
            return false;
        if (this.ppu == null) {
            if (other.ppu != null)
                return false;
        }
        else if (!this.ppu.equals(other.ppu))
            return false;
        if (this.type == null) {
            if (other.type != null)
                return false;
        }
        else if (!this.type.equals(other.type))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.user == null) ? 0 : this.user.hashCode());
        result = prime * result + ((this.resource == null) ? 0 : this.resource.hashCode());
        result = prime * result + ((this.date == null) ? 0 : this.date.hashCode());
        result = prime * result + ((this.isbuy == null) ? 0 : this.isbuy.hashCode());
        result = prime * result + ((this.above == null) ? 0 : this.above.hashCode());
        result = prime * result + ((this.ppu == null) ? 0 : this.ppu.hashCode());
        result = prime * result + ((this.type == null) ? 0 : this.type.hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Subscriptions_2 (");

        sb.append(user);
        sb.append(", ").append(resource);
        sb.append(", ").append(date);
        sb.append(", ").append(isbuy);
        sb.append(", ").append(above);
        sb.append(", ").append(ppu);
        sb.append(", ").append(type);

        sb.append(")");
        return sb.toString();
    }
}
