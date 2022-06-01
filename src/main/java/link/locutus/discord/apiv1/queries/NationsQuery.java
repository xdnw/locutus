package link.locutus.discord.apiv1.queries;

import link.locutus.discord.apiv1.core.UrlBuilder;
import link.locutus.discord.apiv1.domains.Nations;
import link.locutus.discord.apiv1.enums.QueryURL;

import java.util.HashMap;
import java.util.stream.Collectors;

public class NationsQuery extends Query {
  private final Boolean vm;
  private final Integer maxScore;
  private final Integer minScore;
  private final Integer allianceId;

  public NationsQuery(Boolean vm, Integer maxScore, Integer minScore, Integer allianceId, String apiKey) {
    super(apiKey);
    this.vm = vm;
    this.maxScore = maxScore;
    this.minScore = minScore;
    this.allianceId = allianceId;
  }

  @Override
  public ApiQuery build() {
    String url = UrlBuilder.build(QueryURL.NATIONS_URL, args);
    if (vm != null || maxScore != null || minScore != null || allianceId != null) {
      url = url.concat("&");
      HashMap<String, String> params = new HashMap<>();
      if (vm != null)
        params.put("vm", vm.toString());
      if (maxScore != null)
        params.put("max_score", Integer.toString(maxScore));
      if (minScore != null)
        params.put("min_score", Integer.toString(minScore));
      if (allianceId != null)
        params.put("alliance_id", Integer.toString(allianceId));
      String s = params.entrySet()
          .stream()
          .map(stringStringEntry -> stringStringEntry.getKey() + "=" + stringStringEntry.getValue())
          .collect(Collectors.joining("&"));
      url = url.concat(s);
    }
    return new ApiQuery<>(url,new Nations());
  }
}
