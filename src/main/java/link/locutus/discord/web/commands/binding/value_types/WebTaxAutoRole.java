package link.locutus.discord.web.commands.binding.value_types;

import net.dv8tion.jda.api.entities.Role;

public class WebTaxAutoRole {
    public final long role_id;
    public final String name;
    public final int color;
    public final int money_rate;
    public final int rss_rate;
    public final boolean duplicate_key;

    public WebTaxAutoRole(Role role, int moneyRate, int rssRate) {
        this(role, moneyRate, rssRate, false);
    }

    public WebTaxAutoRole(Role role, int moneyRate, int rssRate, boolean duplicateKey) {
        this.role_id = role.getIdLong();
        this.name = role.getName();
        this.color = role.getColors().getPrimaryRaw();
        this.money_rate = moneyRate;
        this.rss_rate = rssRate;
        this.duplicate_key = duplicateKey;
    }
}
