package link.locutus.discord.apiv1.queries;

import link.locutus.discord.apiv1.core.UrlBuilder;
import link.locutus.discord.apiv1.domains.WarAttacks;
import link.locutus.discord.apiv1.enums.QueryURL;

public class WarAttacksQuery extends Query {
    private final Integer war_id;
    private final Integer min_war_attack_id;
    private final Integer max_war_attack_id;

    public WarAttacksQuery(Integer war_id, Integer min_war_attack_id, Integer max_war_attack_id, String apiKey) {
        super(new String[]{apiKey});
        this.war_id = war_id;
        this.min_war_attack_id = min_war_attack_id;
        this.max_war_attack_id = max_war_attack_id;
    }

    public ApiQuery build() {
        String url = UrlBuilder.build(QueryURL.WAR_ATTACKS_URL, this.args);
        if (this.war_id != null) {
            url = url.concat("&war_id=").concat(Integer.toString(this.war_id));
        } else {
            if (this.max_war_attack_id != null) {
                url = url.concat("&max_war_attack_id=").concat(Integer.toString(this.max_war_attack_id));
            }

            if (this.min_war_attack_id != null) {
                url = url.concat("&min_war_attack_id=").concat(Integer.toString(this.min_war_attack_id));
            }
        }

        return new ApiQuery(url, new WarAttacks());
    }
}
