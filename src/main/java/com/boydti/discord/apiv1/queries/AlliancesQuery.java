package com.boydti.discord.apiv1.queries;

import com.boydti.discord.apiv1.core.UrlBuilder;
import com.boydti.discord.apiv1.domains.Alliances;
import com.boydti.discord.apiv1.enums.QueryURL;

public class AlliancesQuery extends Query {

  public AlliancesQuery(String apiKey) {
    super(apiKey);
  }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.ALLIANCES_URL, args), new Alliances());
  }
}