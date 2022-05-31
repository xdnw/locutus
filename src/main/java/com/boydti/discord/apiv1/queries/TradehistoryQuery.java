package com.boydti.discord.apiv1.queries;

import com.boydti.discord.apiv1.core.UrlBuilder;
import com.boydti.discord.apiv1.domains.TradeHistory;
import com.boydti.discord.apiv1.enums.QueryURL;
import com.boydti.discord.apiv1.enums.ResourceType;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TradehistoryQuery extends Query {

  private final Integer records;
  private final ResourceType[] resources;

  public TradehistoryQuery(Integer records, ResourceType[] resources, String apiKey) {
    super(apiKey);
    this.records = records;
    if (resources != null)
      this.resources = Arrays.copyOf(resources, resources.length);
    else
      this.resources = null;
  }

  @Override
  public ApiQuery build() {
    String url = UrlBuilder.build(QueryURL.TRADEHISTORY_URL, args);
    if (records != null)
      url = url.concat("&records=").concat(Integer.toString(records));
    if (resources != null)
      url = url.concat("&resources=")
          .concat(Arrays.stream(resources).map(ResourceType::getName).collect(Collectors.joining(",")));
    return new ApiQuery<>(url, new TradeHistory());
  }
}
