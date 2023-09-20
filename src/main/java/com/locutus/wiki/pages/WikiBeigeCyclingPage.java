package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.BeigeReason;
import link.locutus.discord.user.Roles;

import java.util.Arrays;
import java.util.stream.Collectors;

import static link.locutus.discord.pnw.BeigeReason.*;

public class WikiBeigeCyclingPage extends BotWikiGen {
    public WikiBeigeCyclingPage(CommandManager2 manager) {
        super(manager, "beige cycling");
    }

    @Override
    public String generateMarkdown() {
        return build(
                "# Alerts When Enemies Are Beiged",
                GuildKey.ENEMY_BEIGED_ALERT.help(),
                CM.settings_beige_alerts.ENEMY_BEIGED_ALERT.cmd.toSlashCommand(),
                "# Set member beige cycling requirements",
                """
                        Beige cycling rules can be set for multiple city ranges.
                        Use the command below to add a city range and a comma separated list of allowed beige reasons""",
                        CM.settings_beige_alerts.addBeigeReasons.cmd.create("", "").toSlashCommand(true),
                        "Here is an example",
                CM.settings_beige_alerts.addBeigeReasons.cmd.create("c10-15", INACTIVE.name() + "," + VACATION_MODE.name() + "," + APPLICANT.name()).toSlashCommand(true),
                "To remove a city range:",
                CM.settings_beige_alerts.removeBeigeReasons.cmd.toSlashCommand(true),
                "To view current beige cycling settings:",
                CM.settings.info.cmd.create(GuildKey.ALLOWED_BEIGE_REASONS.name(), null, null).toSlashCommand(true),
                "The following is a list of beige reasons",
                Arrays.stream(BeigeReason.values()).map(f -> "- `" + f.name() + "`: " + f.getDescription()).collect(Collectors.joining("\n")),
                "# Alerts when beige cycling violations occur",
                GuildKey.ENEMY_BEIGED_ALERT_VIOLATIONS.help(),
                CM.settings_beige_alerts.ENEMY_BEIGED_ALERT_VIOLATIONS.cmd.toSlashCommand(),
                "You can set a role to be pinged",
                CM.role.setAlias.cmd.create(Roles.ENEMY_BEIGE_ALERT_AUDITOR.name(), "@someRole",null, null).toSlashCommand(true),
                "# Using Locutus to check if an enemy can be beiged",
                CM.nation.canIBeige.cmd.toSlashCommand(true),
                commandMarkdownSpoiler(CM.nation.canIBeige.cmd),
                """
                        # What is Beige
                        A nation defeated in war gets 2 more days of being on the beige color. Beige protects from new war declarations. We want to have active enemies always in war, so they don't have the opportunity to build back up.
                        
                        Enemies being beige cycled can receive more defensive war losses than a non cycled enemy because they can be defeated immediate upon leaving beige and becoming war slotted. 
                                                
                        # How to maximize your attacks during a war
                        - Don't open with navals if enemies have units which are a threat. Ships can't attack planes, tanks or soldiers.
                        - Don't naval if you already have them blockaded.
                        - Never airstrike infra, cash, or small amounts of units - wait for them to build more units.
                        - If they just have some soldiers and can't get a victory against you, don't spam ground attacks.
                        - If the enemy only has soldiers (no tanks) and you have max planes, airstriking soldiers kills more soldiers than a ground attack will.
                        - Missiles/Nukes do NOT kill any units.
                                                
                        Note: You can do some unnecessary attacks if the war is going to expire, or you need to beige them as part of a beige cycle.
                                                
                        # What is beige cycling?
                        Beige cycling is when we have a weakened enemy, and 3 strong nations declared on that enemy - then 1 nation defeats them, whilst the other two sit on them whilst they are on beige.
                        When their 2 days of beige from the defeat ends, another nation declares on the enemies free slot and the next nation defeats the enemy.
                                                
                        # Example Beige cycling checklist:
                        1. Is the enemy military mostly weakened/gone?
                        2. Is the enemy not currently on beige?
                        3. Do they have 3 defensive wars, with the other two attackers having enough military?
                        4. Are you the first person to have declared?
                                                
                        Tip: Save your MAP. Avoid going below 40 resistance until you are GO for beiging them."""
        );
    }
}
