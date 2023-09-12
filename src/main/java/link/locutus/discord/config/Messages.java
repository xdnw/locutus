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

    public static String FORUM_NEWS_ERROR = """
                You must provide a valid link to a `forum_post`, or a `news_post`
                Forums: <https://forum.politicsandwar.com/index.php?/forum/40-orbis-central/>
                News Servers: 
                - <https://politicsandwar.fandom.com/wiki/Category:News_Servers>
                - <https://politicsandwar.fandom.com/wiki/Discord_Directory>
                
                Supported News Servers:
                - Ducc News Network
                - Royal Orbis News
                - Orbis Crowned News
                - Very Good Media
                - Pirate Island Times
                - The Micro Minute
                - Thalmoria
                - Orbis Business & Innovation Forum
                - Black Label Media
                
                Note: DM `xdnw` to get a news server added to this list""";

    public static String PROMPT_EMOJIFY = """
                    Below is a list of channels by their category.
                    Please respond with a suitable emoji and description for each channel.
                    Do not respond with anything else, only the channel name, emoji and descriptions.
                    #Categories and channels
                    ```
                    {channels}
                    ```
                    
                    #Example response:
                    ```
                    queen-chat:\uD83D\uDCAC | general chat for queen role
                    role-buttons:\u2B55 | buttons for giving yourself discord roles
                    apply:\uD83E\uDD5A | open a ticket to apply to the alliance here
                    offensive-wars:\uD83D\uDDE1\uFE0F | alerts for alliance offensive wars
                    defensive-wars:\uD83D\uDD30 | alerts for alliance defensive wars
                    bot-channel:\uD83E\uDD16 | use discord bot commands here
                    announcements:\uD83D\uDEA8 | important alliance announcements
                    projects:\uD83D\uDEA7 | channel for project grants and requests
                    econ-staff:\uD83D\uDCB0 | channel for alliance economics (EA) staff
                    land-program:\uD83C\uDF3E | channel for member land grants
                    interview:\uD83D\uDC76 | interview channel for new applicants before becoming members
                    guides:\uD83D\uDCDA | guides for alliance members
                    withdraw-funds:\uD83C\uDFE7 | channel for using the bot to withdraw
                    trading-floor:\uD83D\uDCB1 | post or discuss intra-alliance trade offers
                    news:\uD83D\uDCF0 | news posts for Politics & War 
                    ia-auto:\uD83E\uDDBE | automation buttons for internal affairs (IA)
                    spy-info-n-ops:\uD83D\uDD0D | Post your spy operations and information here
                    spy-requests:\uD83D\uDD0D | Request spy operations against an enemy here
                    def-spyops:\uD83D\uDD75\uFE0F | Alerts for defensive spy attacks
                    beige-loot:\uD83D\uDCB0 | Alerts and commands for nation loot information
                    request-counters:\uD83C\uDD98 | post requests for counter attacks here
                    ma-guides:\uD83D\uDCDF | member guides for military affairs (MA)
                    gw28-trophy-room:\uD83C\uDFC6 | global war 28 trophies for members
                    c1-9:\uD83C\uDFDE\uFE0F | channel for members in range city 1 through 9
                    c35+:\uD83C\uDF0C | channel for members with 35 or more cities
                    fa-gov: \uD83D\uDCAC | Discussion channel for foreign affairs (FA) government
                    spy-info-n-ops: \uD83D\uDD0D | Post your spy operations and information here
                    spy-requests: \uD83D\uDD0D | Request spy operations against an enemy here
                    beige-loot: \uD83D\uDCB0 | Alerts and commands for nation loot information
                    request-counters: \uD83C\uDD98 | Post requests for counter attacks here
                    ma-guides: \uD83D\uDCDF | Member guides for military affairs (MA)
                    gw28-trophy-room: \uD83C\uDFC6 | Global war 28 trophies for members
                    c1-9: \uD83C\uDFDE\uFE0F | Channel for members in range city 1 through 9
                    c10-15: \u26FA | Channel for members in range city 10 through 15
                    c16-20: \uD83C\uDFDB\uFE0F | Channel for members in range city 16 through 20
                    c21-27: \uD83C\uDFF0 | Channel for members in range city 21 through 27
                    c28-34: \uD83C\uDFD9\uFE0F | Channel for members in range city 28 through 34
                    c35+: \uD83C\uDF0C | Channel for members with 35 or more cities
                    the-vanguard-program: \uD83C\uDFF0 | Program channel for The Vanguard Program
                    lounge: \uD83D\uDCAC | General lounge for members
                    econ-announcements: \uD83D\uDCE2 | Announcements related to alliance economics
                    info: \uD83D\uDCDD | General information channel
                    priority-targets: \u2757 | Channel for priority enemy target alerts
                    general: \uD83D\uDCAC | General discussion channel
                    war-questions-or-talk: \u2753 | Channel for discussing war-related questions and topics
                    public-chat: \uD83D\uDCAC | Public chat for general conversations
                    spy-recruitment-centre: \uD83D\uDD0D | Auditing channel for spy unit recruitment
                    c3: \uD83D\uDC1C | Channel for members in city 3
                    c11: \uD83E\uDD90 | Channel for members in city 11
                    c12: \uD83D\uDC1F | Channel for members in city 12
                    c22: \uD83D\uDC2C | Channel for members in city 22
                    above-c35: \uD83D\uDC0B | Channel for members with cities above 35
                    membership: \uD83E\uDDD2 | Membership-related discussions
                    advisors: \uD83D\uDC74 | Channel for advisor discussions
                    inactive: \uD83D\uDC80 | Channel for inactive members
                    c33-wall: \uD83E\uDDF1 | Channel for members in city 33
                    c30-wall: \uD83C\uDF09 | Channel for members in city 30
                    queen-bot: \uD83E\uDD16 | Channel for bot command for those with queen role
                    locutus-updates: \uD83D\uDCE2 | Updates related to Locutus bot
                    request-assistance: \uD83C\uDFAB | Ticket channel for requesting assistance
                    assimilation-process: \uD83D\uDC4B | Channel for discussions related to the assimilation process
                    ask-questions-here: \uD83D\uDE4B | Channel for asking questions
                    ranking: \uD83D\uDCC8 | Discussion about alliance rankings
                    blockaded: \uD83D\uDEA2 | Alerts when members are blockaded
                    enlist-with-us: \uD83D\uDCDC | Information on how to enlist with the alliance
                    meme-central: \uD83E\uDD21 | Meme central for sharing memes
                    pirate-code: \uD83C\uDFF4 | The code of conduct for how to conduct in-game piracy 
                    admin: \uD83D\uDD10 | Admin channel for administrative purposes
                    grant-request: \uD83D\uDCB8 | Channel for members to request grants
                    verified: \u2705 | Chat channel for verified players
                    music: \uD83C\uDFB5 | Voice channel for music
                    voice-chat: \uD83C\uDF99\uFE0F | Voice channel for talking
                    recruitment-spam:\uD83E\uDEA7 | channel for logging recruitment messages sent
                    ```""";
}