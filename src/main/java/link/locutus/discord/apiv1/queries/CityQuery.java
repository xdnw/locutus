package link.locutus.discord.apiv1.queries;

import link.locutus.discord.apiv1.core.UrlBuilder;
import link.locutus.discord.apiv1.domains.City;
import link.locutus.discord.apiv1.enums.QueryURL;

public class CityQuery extends Query {

  public CityQuery(int cid, String apiKey) {
    super(Integer.toString(cid), apiKey);
  }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.CITY_URL, args), new City());
  }
}
