package link.locutus.discord.util.task.multi;

import link.locutus.discord.apiv1.enums.ResourceType;

public class SameNetworkTrade {
    public int sellingNation;
    public int buyingNation;
    public long dateOffered;
    public ResourceType resource;
    public int amount;
    public int ppu;

    public SameNetworkTrade(int sellingNation, int buyingNation, long dateOffered, ResourceType resource, int amount, int ppu) {
        this.sellingNation = sellingNation;
        this.buyingNation = buyingNation;
        this.dateOffered = dateOffered;
        this.resource = resource;
        this.amount = amount;
        this.ppu = ppu;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SameNetworkTrade that = (SameNetworkTrade) o;

        if (sellingNation != that.sellingNation) return false;
        if (buyingNation != that.buyingNation) return false;
        if (dateOffered != that.dateOffered) return false;
        if (amount != that.amount) return false;
        if (ppu != that.ppu) return false;
        return resource == that.resource;
    }

    @Override
    public int hashCode() {
        int result = sellingNation;
        result = 31 * result + buyingNation;
        result = 31 * result + (int) (dateOffered ^ (dateOffered >>> 32));
        result = 31 * result + (resource != null ? resource.hashCode() : 0);
        result = 31 * result + amount;
        result = 31 * result + ppu;
        return result;
    }
}
