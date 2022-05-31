package com.boydti.discord.apiv1.core;

import com.boydti.discord.apiv1.domains.Entity;

import java.io.PrintStream;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheClient {
  private final int CACHE_MAX_SIZE;
  private final long CACHE_RETAIN_TIME;
  private Map<String, Entity> cacheMap;
  private Map<String, Instant> timeMap;

  CacheClient(int cacheSize, long cacheRetainTime) {
    this.CACHE_MAX_SIZE = cacheSize;
    this.CACHE_RETAIN_TIME = cacheRetainTime;
    cacheMap = Collections.synchronizedMap(new ConcurrentHashMap<>(cacheSize + 1, 1F));
    timeMap = Collections.synchronizedMap(new ConcurrentHashMap<>(cacheSize + 1, 1F));
  }

  void add(String url, Entity entity) {
    update();
    url = Utility.obfuscateApiKey(url);
    if (cacheMap.size() >= CACHE_MAX_SIZE)
      removeOldest();
    cacheMap.put(url, entity);
    timeMap.put(url, Instant.now());
  }

  boolean contains(String url) {
    update();
    return cacheMap.containsKey(Utility.obfuscateApiKey(url));
  }

  Entity getIfExists(String url) {
    return cacheMap.get(Utility.obfuscateApiKey(url));
  }

  void clear() {
    cacheMap.clear();
    timeMap.clear();
  }

  private void removeOldest() {
    timeMap.entrySet()
        .stream()
        .min(Comparator.comparingLong(entry -> entry.getValue().getEpochSecond()))
        .ifPresent(entry -> {
          cacheMap.remove(entry.getKey());
          timeMap.remove(entry.getKey());
        });
  }

  private void update() {
    Instant now = Instant.now();
    for (Iterator<Map.Entry<String, Instant>> it = timeMap.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, Instant> entry = it.next();
      if ((now.getEpochSecond() - entry.getValue().getEpochSecond()) > (CACHE_RETAIN_TIME / 1000)) {
        it.remove();
        cacheMap.remove(entry.getKey());
      }
    }
  }

  public void printCacheStats(PrintStream stream) {
    stream.println(Integer.toString(getCacheSize()).concat("/").concat(Integer.toString(getCapacity())));
  }

  public int getCacheSize() {
    return cacheMap.size();
  }

  public int getCapacity() {
    return CACHE_MAX_SIZE;
  }
}