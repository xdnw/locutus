package link.locutus.discord.apiv1.core;

import link.locutus.discord.apiv1.domains.Entity;
import link.locutus.discord.apiv1.queries.ApiQuery;

import java.io.IOException;

public class QueryExecutor {

  private CacheClient cacheClient = null;
  private boolean testServerMode;
  private boolean enableCache;

  public QueryExecutor(boolean enableCache, boolean testServerMode, int cacheMaxSize, long cacheRetainTime) {
    this.enableCache = enableCache;
    this.testServerMode = testServerMode;
    if (this.enableCache)
      cacheClient = new CacheClient(cacheMaxSize, cacheRetainTime);
  }

  public Entity execute(ApiQuery apiQuery) throws IOException {
    apiQuery.buildUrlStr(testServerMode);
    String url = apiQuery.getUrlStr();
    if (this.enableCache && cacheClient.contains(url))
      return getFromCache(url);
    else
      return getFromSource(apiQuery);
  }

  private Entity getFromCache(String url) {
    return cacheClient.getIfExists(url);
  }

  private Entity getFromSource(ApiQuery apiQuery) throws IOException {
    Entity entity = apiQuery.fetchAPI().getEntity();
    if (this.enableCache)
      cacheClient.add(apiQuery.getUrlStr(), entity);
    return entity;
  }

  public CacheClient getCacheClient() {
    return cacheClient;
  }

  public void clearCacheClient() {
    if (cacheClient != null)
      cacheClient.clear();
  }
}
