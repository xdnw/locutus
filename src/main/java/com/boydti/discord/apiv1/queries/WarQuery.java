package com.boydti.discord.apiv1.queries;

import com.boydti.discord.apiv1.core.UrlBuilder;
import com.boydti.discord.apiv1.domains.War;
import com.boydti.discord.apiv1.enums.QueryURL;

public class WarQuery extends Query {

  public WarQuery(int wid, String apiKey) {
    super(Integer.toString(wid), apiKey);
  }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.WAR_URL, args), new War());
  }
}
