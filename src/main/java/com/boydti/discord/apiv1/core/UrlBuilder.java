package com.boydti.discord.apiv1.core;

import com.boydti.discord.apiv1.enums.QueryURL;

import java.text.MessageFormat;

public class UrlBuilder {
  public static String build(QueryURL url, String[] args) {
    return MessageFormat.format(url.getUrl(), (Object[]) args);
  }
}
