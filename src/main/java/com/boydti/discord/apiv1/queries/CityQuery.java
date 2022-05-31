package com.boydti.discord.apiv1.queries;

import com.boydti.discord.apiv1.core.UrlBuilder;
import com.boydti.discord.apiv1.domains.City;
import com.boydti.discord.apiv1.enums.QueryURL;

public class CityQuery extends Query {

  public CityQuery(int cid, String apiKey) {
    super(Integer.toString(cid), apiKey);
  }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.CITY_URL, args), new City());
  }
}
