package link.locutus.discord.apiv1.queries;

import link.locutus.discord.apiv1.core.UrlBuilder;
import link.locutus.discord.apiv1.domains.Alliances;
import link.locutus.discord.apiv1.enums.QueryURL;

public class AlliancesQuery extends Query {

  public AlliancesQuery(String apiKey) {
    super(apiKey);
  }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.ALLIANCES_URL, args), new Alliances());
  }
}