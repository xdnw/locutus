package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiCounteringPage extends BotWikiGen {
    public WikiCounteringPage(CommandManager2 manager) {
        super(manager, "countering");
    }

    @Override
    public String generateMarkdown() {
        return build(
                "# Find a member in range of an enemy",
                "Using the enemies nation link",
                commandMarkdownSpoiler(CM.war.counter.nation.cmd),
                "Using the war url",
                commandMarkdownSpoiler(CM.war.counter.url.cmd),
                "# Create a sheet of uncountered enemies",
                commandMarkdownSpoiler(CM.war.counter.sheet.cmd),
                "# Create a war room",
                commandMarkdownSpoiler(CM.war.room.create.cmd),
                "# Auto find counters and create war rooms",
                commandMarkdownSpoiler(CM.war.counter.auto.cmd),
                """
                        # General player tips for countering:
                        A counter is when an alliance declares a war on a nation for attacking one of its members/applicants. They are usually authorized counters for unprovoked attacks on members.
                                                
                        Unlike a raid, the goal of a counter isn't to profit, it's to get the enemy's military down to inhibit their ability to raid or damage our members. If you have spare MAP and they have low (or no) units, it is usually better to hold your attacks until they buy more military.
                                                
                        The alliance can cover costs for a counter
                                                
                        1.  Switch your mmr for the counter (e.g. mmr=5051 for a pirate with only soldiers, mmr=5551 for a militarized enemy)
                                                
                        2.  Use e.g. {command} to see if you will be in war range if you buy military units now. If you will not be in range, you will want to buy those units AFTER you declare your war.
                                                
                        3.  If you think you might need resources for your counter (e.g. money, munitions, steel, aluminum, gasoline), withdraw your own funds, or ask in #grant-requests (it will be sent as a loan and what you spend will be reimbursed)
                                                
                        4.  Units can be purchased each day. It is therefore preferable to declare (along with the other people countering) just before day change, and buy your units then. Note: You can change your day change: <https://politicsandwar.com/account/>
                                                
                        5.  After declaring, use your war room to communicate with other people countering.
                                                
                        6.  After you finish the counter (win, lose or timeout), request reimbursement in #grant-requests by posting the war link
                                                
                        7.  When the conflict is over, you can sell some of your military and switch back to your peacetime mmr.
                                                
                        Tips:
                                                
                        -   Focus on taking down the units the enemy is strongest in. If the enemy has strong ground or air, take that down first.
                                                
                        -   If the enemy is weak and their military is not currently a threat, get a blockade up and sit on them
                                                
                        -   Don't airstrike infra, cash, waste fortifies, or double blockades, or unnecessarily suicide units
                        """.replace("{command}", CM.nation.score.cmd.create("@user", null, null, null, null, null, null, null, null, null, null, "5551").toString())
        );
    }
}
