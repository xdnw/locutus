package link.locutus.discord.apiv1.enums;

public enum QueryURL {
  LIVE_URL("https://politicsandwar.com/api/"),
  TEST_URL("https://test.politicsandwar.com/api/"),
  NATION_URL("nation/id={0}&key={1}"),
  NATIONS_URL("nations/?key={0}"),
  ALLIANCE_URL("alliance/id={0}&key={1}"),
    ALLIANCE_MEMBERS_URL("alliance-members/?allianceid={0}&key={1}"),
  ALLIANCES_URL("alliances/?key={0}"),
  WAR_URL("war/{0}&key={1}"),
  WARS_URL("wars/?key={0}"),
  BANK_URL("alliance-bank/?allianceid={0}&key={1}"),
  MEMBERS_URL("alliance-members/?allianceid={0}&key={1}"),
  APPLICANTS_URL("applicants/{0}&key={1}"),
  CITY_URL("city/id={0}&key={1}"),
  TRADEPRICE_URL("tradeprice/resource={0}&key={1}"),
  TRADEHISTORY_URL("trade-history/?key={0}"),
  ALL_CITIES_URL("all-cities/?key={0}"),
  NATION_MILITARY_URL("nation-military/?key={0}"),
  WAR_ATTACKS_URL("war-attacks/key={0}");

  private final String url;

  QueryURL(final String url) {
    this.url = url;
  }

  public String getUrl() {
    return url;
  }
}