package link.locutus.discord.apiv1;

import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv2.PoliticsAndWarV2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PoliticsAndWarBuilder {

  private boolean testServerMode;
  private List<String> apiKeysList;
  private boolean enableCache;

  public PoliticsAndWarBuilder() {
    this.enableCache = false;
    this.apiKeysList = new ArrayList<>();
    this.testServerMode = false;
  }

  public PoliticsAndWarBuilder addApiKey(String apiKey) {
    this.apiKeysList.add(apiKey);
    return this;
  }

  public PoliticsAndWarBuilder addApiKeys(String... apiKey) {
    this.apiKeysList.addAll(Arrays.asList(apiKey));
    return this;
  }

  public PoliticsAndWarBuilder setTestServerMode(boolean testServerMode) {
    this.testServerMode = testServerMode;
    return this;
  }

  public PoliticsAndWarBuilder setEnableCache(boolean enableCache) {
    this.enableCache = enableCache;
    return this;
  }

  public PoliticsAndWarV2 build() {
    ApiKeyPool pool = ApiKeyPool.builder().addKeys(apiKeysList).build();
    return new PoliticsAndWarV2(pool, testServerMode, enableCache);
  }
}