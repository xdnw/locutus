package com.boydti.discord.apiv1.queries;

import com.boydti.discord.apiv1.core.UrlBuilder;
import com.boydti.discord.apiv1.domains.NationMilitary;
import com.boydti.discord.apiv1.enums.QueryURL;

public class NationMilitaryQuery extends Query {

  public NationMilitaryQuery(String apiKey) {
    super(apiKey);
  }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.NATION_MILITARY_URL, args), new NationMilitary());
  }
}
