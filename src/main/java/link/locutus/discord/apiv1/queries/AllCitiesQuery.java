package link.locutus.discord.apiv1.queries;

import link.locutus.discord.apiv1.core.UrlBuilder;
import link.locutus.discord.apiv1.domains.AllCities;
import link.locutus.discord.apiv1.enums.QueryURL;

public class AllCitiesQuery extends Query {

  public AllCitiesQuery(String apiKey) {
    super(apiKey);
  }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.ALL_CITIES_URL, args), new AllCities());
  }
}
