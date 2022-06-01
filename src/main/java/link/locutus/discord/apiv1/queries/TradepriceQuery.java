package link.locutus.discord.apiv1.queries;

import link.locutus.discord.apiv1.core.UrlBuilder;
import link.locutus.discord.apiv1.domains.TradePrice;
import link.locutus.discord.apiv1.enums.QueryURL;
import link.locutus.discord.apiv1.enums.ResourceType;

public class TradepriceQuery extends Query {

  public TradepriceQuery(ResourceType resource, String apiKey) {
    super(resource.getName(), apiKey);
  }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.TRADEPRICE_URL, args), new TradePrice());
  }
}
