package com.boydti.discord.apiv1;

public enum QueryURL {
    LIVE_URL("https://politicsandwar.com/api/"),
    TEST_URL("https://test.politicsandwar.com/api/"),
    NATION_URL("nation/id={1}&key={0}"),
    NATIONS_URL("nations/?key={0}"),
    ALLIANCE_URL("alliance/id={1}&key={0}"),
    ALLIANCE_MEMBERS_URL("alliance-members/?allianceid={1}&key={0}"),
    ALLIANCES_URL("alliances/?key={0}"),
    WAR_URL("war/{1}&key={0}"),
    WARS_URL("wars/?key={0}"),
    BANK_URL("alliance-bank/?allianceid={1}&key={0}"),
    MEMBERS_URL("alliance-members/?allianceid={1}&key={0}"),
    APPLICANTS_URL("applicants/{1}&key={0}"),
    CITY_URL("city/id={1}&key={0}"),
    TRADEPRICE_URL("tradeprice/resource={1}&key={0}"),
    TRADEHISTORY_URL("trade-history/?key={0}"),
    ALL_CITIES_URL("all-cities/?key={0}"),
    NATION_MILITARY_URL("nation-military/?key={0}"),
    WAR_ATTACKS_URL("war-attacks/key={0}");

    private final String url;

    private QueryURL(String url) {
        this.url = url;
    }

    public String getUrl(String key) {
        return getUrl(key, null, null);
    }

    public String getUrl(String key, String arg, String query) {
        String url = this.url.replace("{0}", key);
        if (arg != null) url = url.replace("{1}", arg);
        if (query != null && !query.isEmpty()) url = url + "&" + query;
        return url;
    }
}
