package link.locutus.discord.util.task.ia;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

public enum AuditType {
    CHECK_RANK("\uD83E\uDD47", IACheckup.AuditSeverity.WARNING, "position",
            "Not a member of the alliance in-game", false, false),
    INACTIVE("\uD83D\uDCA4", IACheckup.AuditSeverity.DANGER, "days inactive",
            "Two or more days inactive", false, false),
    FINISH_OBJECTIVES("\uD83D\uDC69\u200D\uD83C\uDFEB", IACheckup.AuditSeverity.WARNING, "false",
            "Has one city in-game", false, false),
    FIX_COLOR(FINISH_OBJECTIVES, "\uD83C\uDFA8", IACheckup.AuditSeverity.WARNING, "color",
            "Not on the alliance color or beige", false, false),
    CHANGE_CONTINENT(FINISH_OBJECTIVES, "\uD83C\uDF0D", IACheckup.AuditSeverity.WARNING, "continent",
            "Not in a continent that can build uranium (needed for self sufficient city power)", false, false),
    FIX_WAR_POLICY(FINISH_OBJECTIVES, "\uD83D\uDCDC", IACheckup.AuditSeverity.WARNING, "war policy",
            "War policy must be either fortress if no wars, or pirate", false, false),
    RAID(FINISH_OBJECTIVES, "\uD83D\uDD2A", IACheckup.AuditSeverity.WARNING, "# available targets",
            "Must have at least 4 raids going, or no suitable nations in range", false, false),
    UNUSED_MAP("\uD83D\uDE34", IACheckup.AuditSeverity.WARNING, "war ids",
            "Has 12 MAP in raids against inactives", false, false),
    BARRACKS(FINISH_OBJECTIVES, "\uD83C\uDFD5", IACheckup.AuditSeverity.WARNING, "building mmr",
            "Has <5 barracks and is either below city 10, or is fighting an active nation", false, false),
    INCORRECT_MMR(FINISH_OBJECTIVES, "\uD83C\uDFE2", IACheckup.AuditSeverity.WARNING, "building mmr",
            "Does not match the REQUIRED_MMR set by the guild", false, true),
    BUY_SOLDIERS(BARRACKS, "\uD83D\uDC82", IACheckup.AuditSeverity.WARNING, "soldier %",
            "Has barracks, but has not bought soldiers OR has not bought barracks and the alliance is at war", false, false),
    BUY_HANGARS(BUY_SOLDIERS, "\u2708\uFE0F", IACheckup.AuditSeverity.WARNING, "building mmr",
            "Nation is c10 or higher, with >1.7k infra, at peace, and has <5 avg. hangars", false, false),
    BUY_PLANES(BUY_HANGARS, "\u2708\uFE0F", IACheckup.AuditSeverity.WARNING, "plane %",
            "Nation is c10 or higher, with >1.7k infra, at peace, and has not bought planes in its hangars", false, false),
    BUY_SHIPS(BUY_PLANES, "\uD83D\uDEA2", IACheckup.AuditSeverity.WARNING, "ships",
            "Nation is at war with a pirate (weaker air and <4 ships), and has no ships", false, false),
    BEIGE_LOOT(BUY_SOLDIERS, "\uD83D\uDC82", IACheckup.AuditSeverity.INFO, "LINK",
            "Has not used the bot to find beige targets to raid", true, false), // TODO FIXME web link
    RAID_TURN_CHANGE(BEIGE_LOOT, "\uD83C\uDFAF", IACheckup.AuditSeverity.INFO, "false",
            "Has not declared a war at turn change", true, false), // TODO FIXME web link
    BUY_SPIES(FINISH_OBJECTIVES, "\uD83D\uDD75", IACheckup.AuditSeverity.WARNING, "spies",
            "Nation is below spy cap and has not bought in 2 or more days", false, false),
    GATHER_INTEL(BUY_SPIES, "\uD83D\uDD0E", IACheckup.AuditSeverity.INFO, "false",
            "Nation has not used the `/spy find intel` command", true, false),
    SPY_COMMAND(GATHER_INTEL, "\uD83D\uDCE1", IACheckup.AuditSeverity.INFO, "false",
            "Nation has not used the `/nation spies` command", true, false),
    LOOT_COMMAND(SPY_COMMAND, "", IACheckup.AuditSeverity.INFO, "false",
            "Nation has not used the `/nation loot` command", true, false),
    DAILY_SPYOPS(LOOT_COMMAND, "\uD83D\uDEF0", IACheckup.AuditSeverity.WARNING, "days since last spy op",
            "Nation has not done a spy op in 3 days nor pinged the bot with a spy report (if no api tracking is available)", false, true),
    DEPOSIT_RESOURCES(FINISH_OBJECTIVES, "\uD83C\uDFE6", IACheckup.AuditSeverity.WARNING, "false",
            "Nation has NEVER deposited resources into the alliance bank", false, false),
    CHECK_DEPOSITS(DEPOSIT_RESOURCES, "", IACheckup.AuditSeverity.INFO, "false",
            "Nation has NEVER checked their balance using the command", true, false),
    WITHDRAW_DEPOSITS(CHECK_DEPOSITS, "\uD83C\uDFE7", IACheckup.AuditSeverity.INFO, "false",
            "Nation has never received funds from the alliance", false, false),
    OBTAIN_RESOURCES(FINISH_OBJECTIVES, "\uD83C\uDFE7", IACheckup.AuditSeverity.DANGER, "daily upkeep missing",
            "Missing resources for nation upkeep", false, true),
    SAFEKEEP(FINISH_OBJECTIVES, "\uD83C\uDFE6", IACheckup.AuditSeverity.WARNING, "amount to deposit",
            "Exceeds 3x alliance warchest, and 7d of nation upkeep", false, true),
    OBTAIN_WARCHEST(OBTAIN_RESOURCES, "\uD83C\uDFE7", IACheckup.AuditSeverity.WARNING, "resources missing",
            "C10+, Has >80% planes and has below the warchest requirements", false, true),
    BUY_CITY(FINISH_OBJECTIVES, "\uD83C\uDFD9", IACheckup.AuditSeverity.INFO, "cities",
            "Nation is c10+ or has raided $200M, is at peace, and has no city timer", false, false),
    BUY_PROJECT(FINISH_OBJECTIVES, "\uD83D\uDE80", IACheckup.AuditSeverity.INFO, "free project slots",
            "Nation has no project timer and free project slots", false, false),
    ACTIVITY_CENTER(FINISH_OBJECTIVES, "\uD83D\uDE80", IACheckup.AuditSeverity.WARNING, "BUY/SELL",
            "Nation is above AC city cutoff OR nation has not purchased activity center and has free project slots and no timer", false, false),
    BUY_INFRA(FINISH_OBJECTIVES, "\uD83C\uDFD7", IACheckup.AuditSeverity.WARNING, "city ids",
            "Nation has less than 1.2k infra worth of buildings in their cities (bot does not recommend rebuilding damaged infra)", false, false),
    BUY_LAND(FINISH_OBJECTIVES, "\uD83C\uDFDE", IACheckup.AuditSeverity.WARNING, "city ids",
            "Nation has below 800 land, or >33% less land than infra in their cities and less than 2k land", false, false),
    UNPOWERED(FINISH_OBJECTIVES, "\uD83D\uDD0C", IACheckup.AuditSeverity.DANGER, "city ids",
            "Nation has unpowered cities", false, false),
    OVERPOWERED(FINISH_OBJECTIVES, "\u26A1", IACheckup.AuditSeverity.DANGER, "city ids",
            "Nation has cities with excess power plants", false, false),
    NOT_NUCLEAR(FINISH_OBJECTIVES, "\u2622", IACheckup.AuditSeverity.WARNING, "city ids",
            "Nation has cities with no nuclear power plants", false, false),
    FREE_SLOTS(FINISH_OBJECTIVES, "\uD83D\uDEA7", IACheckup.AuditSeverity.DANGER, "city ids",
            "Nation has cities with free building slots", false, false),
    NEGATIVE_REVENUE(FINISH_OBJECTIVES, "\uD83E\uDD7A", IACheckup.AuditSeverity.DANGER, "city ids",
            "Nation has cities with negative revenue", false, false),
    MISSING_PRODUCTION_BONUS(FINISH_OBJECTIVES, "\uD83D\uDCC8", IACheckup.AuditSeverity.WARNING, "city ids",
            "Nation has cities with two or more resources missing production bonus", false, false),
    EXCESS_HOSPITAL(FINISH_OBJECTIVES, "\uD83C\uDFE5", IACheckup.AuditSeverity.WARNING, "city ids",
            "Nation has cities with excess hospitals", false, false),
    EXCESS_POLICE(FINISH_OBJECTIVES, "\uD83D\uDE93", IACheckup.AuditSeverity.WARNING, "city ids",
            "Nation has cities with excess police stations", false, false),
    EXCESS_RECYCLING(FINISH_OBJECTIVES, "\u267B", IACheckup.AuditSeverity.WARNING, "city ids",
            "Nation has cities with excess recycling centers", false, false),
    GENERATE_CITY_BUILDS(MISSING_PRODUCTION_BONUS, "", IACheckup.AuditSeverity.INFO, "false",
            "Nation has not used the bot to generate city builds", true, false),
    //        ROI(GENERATE_CITY_BUILDS, "", AuditSeverity.INFO, "false",
//                "Nation has not used the bot to generate ROI", true, false),
    BLOCKADED("\uD83D\uDEA2", IACheckup.AuditSeverity.WARNING, "blockader nation ids",
            "Nation is blockaded by other nations", false, false),
//        LOSE_A_WAR(RAID_TURN_CHANGE, "", AuditSeverity.INFO),
//        PLAN_A_RAID_WITH_FRIENDS(LOSE_A_WAR, "", AuditSeverity.INFO),
//        CREATE_A_WAR_ROOM(PLAN_A_RAID_WITH_FRIENDS, "", AuditSeverity.INFO),
    ;

    public final AuditType required;
    public final String emoji, infoType;
    public final IACheckup.AuditSeverity severity;
    public final String description;
    public final boolean requiresDiscord, requiresApi;

    AuditType(String emoji, IACheckup.AuditSeverity severity, String infoType, String description, boolean requiresDiscord, boolean requiresApi) {
        this(null, emoji, severity, infoType, description, requiresDiscord, requiresApi);
    }

    AuditType(AuditType required, String emoji, IACheckup.AuditSeverity severity, String infoType, String description, boolean requiresDiscord, boolean requiresApi) {
        this.required = required;
        this.emoji = emoji;
        this.severity = severity;
        this.infoType = infoType;
        this.description = description;
        this.requiresDiscord = requiresDiscord;
        this.requiresApi = requiresApi;
    }

    @Command(desc = "Audit severity")
    public IACheckup.AuditSeverity getSeverity() {
        return severity;
    }


    @Command(desc = "Audit requires API access")
    public boolean requiresApi() {
        return requiresApi;
    }

    @Command(desc = "Audit requries member has discord")
    public boolean requiresDiscord() {
        return requiresDiscord;
    }

    @Command(desc = "Audit emoji")
    public String getEmoji() {
        return emoji;
    }

    @Command(desc = "The required audit, or null")
    public AuditType getRequired() {
        return required;
    }

    @Command(desc = "Name of the audit")
    public String getName() {
        return name();
    }
}
