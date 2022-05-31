package com.boydti.discord.apiv1.queries;

import com.boydti.discord.apiv1.core.UrlBuilder;
import com.boydti.discord.apiv1.domains.Members;
import com.boydti.discord.apiv1.enums.QueryURL;

public class MembersQuery extends Query {

  public MembersQuery(int aid, String apiKey) {
    super(Integer.toString(aid), apiKey);
  }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.MEMBERS_URL, args), new Members());
  }
}
