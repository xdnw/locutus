package link.locutus.discord.apiv1.queries;

import link.locutus.discord.apiv1.PoliticsAndWarAPIException;
import link.locutus.discord.apiv1.core.Response;
import link.locutus.discord.apiv1.core.Utility;
import link.locutus.discord.apiv1.domains.Entity;
import link.locutus.discord.apiv1.enums.QueryURL;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

public class ApiQuery<T extends Entity> {

  private final String urlPart;
  private String urlStr = "";
  private final T t;

  ApiQuery(String urlPart, T t) {
    this.urlPart = urlPart;
    this.t = t;
  }

  private static String convertStreamToString(java.io.InputStream is) {
    String result = new BufferedReader(new InputStreamReader(is))
            .lines().collect(Collectors.joining("\n"));
    return result;
  }

  public void buildUrlStr(boolean testServerMode) {
    String baseUrl = testServerMode ? QueryURL.TEST_URL.getUrl() : QueryURL.LIVE_URL.getUrl();
    urlStr = baseUrl.concat(urlPart);
  }

  public Response fetchAPI() throws IOException {
    HttpURLConnection conn = null;
    try {
      URL url = new URL(urlStr);
      conn = (HttpURLConnection) url.openConnection();
      conn.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.77 Safari/537.36");
      conn.setRequestMethod("GET");
      int respCode = conn.getResponseCode();
      String respMessage = String.format("Politics and War API returned '%s' from url: %s", respCode + " " + conn.getResponseMessage(), urlPart);

      InputStream stream = conn.getErrorStream();

      if (stream == null && (respCode >= 200 && respCode < 300)) {
        stream = conn.getInputStream();
        return new Response<>(convertStreamToString(stream), t, urlStr);
      } else {
        throw new PoliticsAndWarAPIException(Utility.obfuscateApiKey(respMessage));
      }
    } finally {
      if(conn != null) {
        conn.disconnect();
      }
    }
  }

  public String getUrlStr() {
    return urlStr;
  }
}
