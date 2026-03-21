package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class WebAutoRoleRoles {
    public final List<WebAllianceAutoRole> alliance_roles;
    public final List<WebCityAutoRole> city_roles;
    public final List<WebTaxAutoRole> tax_roles;

    public WebAutoRoleRoles(List<WebAllianceAutoRole> allianceRoles, List<WebCityAutoRole> cityRoles,
            List<WebTaxAutoRole> taxRoles) {
        this.alliance_roles = allianceRoles;
        this.city_roles = cityRoles;
        this.tax_roles = taxRoles;
    }
}
