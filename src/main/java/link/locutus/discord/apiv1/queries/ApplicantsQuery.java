package link.locutus.discord.apiv1.queries;

import link.locutus.discord.apiv1.core.UrlBuilder;
import link.locutus.discord.apiv1.domains.Applicants;
import link.locutus.discord.apiv1.enums.QueryURL;

public class ApplicantsQuery extends Query {

  public ApplicantsQuery(int aid, String apiKey) {
    super(Integer.toString(aid), apiKey);
   }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.APPLICANTS_URL, args), new Applicants());
  }
}
