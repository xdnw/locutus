package com.boydti.discord.apiv1.queries;

import com.boydti.discord.apiv1.core.UrlBuilder;
import com.boydti.discord.apiv1.domains.Alliance;
import com.boydti.discord.apiv1.enums.QueryURL;

public class AllianceQuery extends Query {

  public AllianceQuery(int aid, String apiKey) {
    super(Integer.toString(aid), apiKey);
  }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.ALLIANCE_URL, args), new Alliance());
  }
}
