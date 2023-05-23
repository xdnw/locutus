package link.locutus.discord.config;

public class Messages {
    public static final String NOT_MEMBER = "No permission for this command";
    public static String SLOGAN = "We are the Borg. Lower your shields and surrender your ships. We will add your biological and technological distinctiveness to our own. ... Resistance is futile.";

    public static String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.134 Safari/537.36";

    public static String SUCCESS = "Success. You are now a registered user.";

    public static String CITY_URL = "" + Settings.INSTANCE.PNW_URL() + "/city/id=";

    public static String NATION_URL = "" + Settings.INSTANCE.PNW_URL() + "/nation/id=";
    public static String ALLIANCE_URL = "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=";

    public static String BLOCKADE_HELP = "A blockade prevents you from sending or receiving funds (via bank or trade)\n" +
            "It can end when:\n" +
            "- A war finishes (either nation is defeated). Wars also expire after 5 days\n" +
            "- All their ships are killed\n" +
            "- You can do a naval attack that isn't an utter failure (see `/simulate naval`)\n" +
            "- Someone else blockades them (Immense Triumph)\n" +
            "Tips:\n" +
            "- Coordinate with other people fighting\n" +
            "- Keep enough funds on your nation (5 days) so you don't need to bother breaking a blockade\n" +
            "Things you can do if you are desperately out of funds and cant wait:\n" +
            "- Daily login can provide some cash ($500k)\n" +
            "- Raiding (up to 5 raids), you can get cash from ground attacks or cash & other resources when you defeat a nation (see `/nation loot`)\n" +
            "- Doing rewarded ads <https://politicsandwar.com/rewarded-ads/> or baseball (see sidebar)\n" +
            "- Selling buildings (or replacing them with some cheaper alternative) or selling units you don't need\n" +
            "- Converted a credit to resources: <https://politicsandwar.com/donate/resources/>\n" +
            "- Switching your build to another power source (or buildings to mine e.g. uranium)";

    public static String GLOBAL_ROLE_MAPPING_INFO = "A global role mapping to ID:0 (*) will grant access to that role for all registered alliances";
}