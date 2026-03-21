package link.locutus.discord.apiv1.queries;

import link.locutus.discord.apiv1.core.UrlBuilder;
import link.locutus.discord.apiv1.domains.War;
import link.locutus.discord.apiv1.enums.QueryURL;

public class WarQuery extends Query<War> {

  public WarQuery(int wid, String apiKey) {
    super(Integer.toString(wid), apiKey);
  }

  @Override
  public ApiQuery<War> build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.WAR_URL, args), new War());
  }
}
