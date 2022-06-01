package link.locutus.discord.apiv2;

public enum QueryURLV2 {
    // V2
    BANK_RECORDS("nation-bank-recs/{0}/&nation_id={1}"),
    LIVE_URL("https://politicsandwar.com/api/v2"),
    TEST_URL("https://test.politicsandwar.com/api/v2"),

    // v1
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

    ;

    private final String url;

    private QueryURLV2(String url) {
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
