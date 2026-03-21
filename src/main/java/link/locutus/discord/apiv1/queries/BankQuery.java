package link.locutus.discord.apiv1.queries;

import link.locutus.discord.apiv1.core.UrlBuilder;
import link.locutus.discord.apiv1.domains.Bank;
import link.locutus.discord.apiv1.enums.QueryURL;

public class BankQuery extends Query<Bank> {

  public BankQuery(int aid, String apiKey) {
    super(Integer.toString(aid), apiKey);
  }

  @Override
  public ApiQuery<Bank> build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.BANK_URL, args), new Bank());
  }
}
