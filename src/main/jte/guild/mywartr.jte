@import java.util.*
@import link.locutus.discord.apiv1.enums.MilitaryUnit
@import link.locutus.discord.apiv1.enums.city.project.Projects
@import link.locutus.discord.apiv1.enums.city.project.Project
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import net.dv8tion.jda.api.entities.User
@import net.dv8tion.jda.api.entities.Guild
@import link.locutus.discord.db.entities.DBNation
@import java.util.List
@import java.util.UUID
@import link.locutus.discord.apiv1.enums.DomesticPolicy
@import link.locutus.discord.util.MathMan
@import link.locutus.discord.util.TimeUtil
@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.entities.Coalition
@import link.locutus.discord.Locutus
@import link.locutus.discord.util.task.ia.IACheckup
@import link.locutus.discord.user.Roles
@import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
@import link.locutus.discord.util.PW
@import java.util.Collection
@import link.locutus.discord.db.entities.DBWar
@import link.locutus.discord.util.task.war.WarCard
@import link.locutus.discord.apiv1.enums.AttackType
@import link.locutus.discord.apiv1.enums.city.JavaCity
@import link.locutus.discord.util.TimeUtil
@import link.locutus.discord.pnw.BeigeReason
@param WebStore ws
@param GuildDB db
@param DBNation nation
@param User author
@param DBWar war
@param DBNation enemy
@param WarCard warCard
@param AttackType recommendedAttack
@param boolean isAttacker
@param Set<BeigeReason> permitted

<table class="table bg-light border-2 border-secondary">
    <thead>
    <tr>
        <th scope="col">Nation</th>
        <th scope="col">Alliance</th>
        <th scope="col">&#127961;&#65039;</th>
        <th scope="col">&#128130;</th>
        <th scope="col">&#9881;&#65039;</th>
        <th scope="col">&#9992;&#65039;</th>
        <th scope="col">&#128674;</th>
        <th scope="col">&#128640;/&#9762;&#65039;</th>
        <th scope="col">Off/Def</th>
        <th scope="col">MAP</th>
        <th scope="col">Resist</th>
    </tr>
    </thead>
    <tbody>
<tr class="border-top border-1 border-secondary">
    <td><a href="${enemy.getUrl()}">${enemy.getNation()}</a>
    @if(enemy.active_m() > 2440)
        <span class="badge bg-secondary">inactive ${TimeUtil.minutesToTime(enemy.active_m())}</span>
    @endif
    </td>
    <td><a href="${enemy.getAllianceUrl()}">${enemy.getAllianceName()}</a></td>
    <td>${enemy.getCities()}</td>
    <td style="color:rgb(${Math.min(255, Math.max(0, 255 * enemy.getGroundStrength(false, false) / ((double) 4 * nation.getGroundStrength(false, false))))}, 0, 0)">${enemy.getSoldiers()}</td>
    <td style="color:rgb(${Math.min(255, Math.max(0, 255 * enemy.getGroundStrength(false, false) / ((double) 4 * nation.getGroundStrength(false, false))))}, 0, 0)">${enemy.getTanks()}</td>
    <td style="color:rgb(${Math.min(255, Math.max(0, 255 * enemy.getAircraft() / ((double) 5 * nation.getAircraft())))}, 0, 0)">${enemy.getAircraft()}</td>
    <td style="color:rgb(${Math.min(255, Math.max(0, 255 * enemy.getShips() / ((double) 5 * nation.getShips())))}, 0, 0)">${enemy.getShips()}</td>
    <td>${enemy.getMissiles()}${enemy.getNukes()}</td>
    <td>${enemy.getOff()}${enemy.getDef()}</td>
    <td>${isAttacker?warCard.defenderMAP:warCard.attackerMAP}</td>
    <td><div class="progress-bar" role="progressbar" aria-valuenow="${(isAttacker?warCard.defenderResistance:warCard.attackerResistance)}"
             aria-valuemin="0" aria-valuemax="100"
             style="width:${Math.max(10,(isAttacker?warCard.defenderResistance:warCard.attackerResistance))}%;background-color:rgb(${MathMan.clamp(255 - (isAttacker?warCard.defenderResistance:warCard.attackerResistance) * 255 / 100, 0, 255)}, ${MathMan.clamp((isAttacker?warCard.defenderResistance:warCard.attackerResistance) * 255 / 100, 0, 255)}, 0)!important">${(isAttacker?warCard.defenderResistance:warCard.attackerResistance)}</div></td>
</tr>
<tr class="border-top border-1 border-secondary">
    <td><a href="${enemy.getUrl()}">${nation.getNation()}</a></td>
    <td><a href="${enemy.getAllianceUrl()}">${nation.getAllianceName()}</a></td>
    <td>${nation.getCities()}</td>
    <td style="color:rgb(0, ${Math.min(255, Math.max(0, 255 * nation.getGroundStrength(false, false) / ((double) 4 * enemy.getGroundStrength(false, false))))}, 0)">${nation.getSoldiers()}</td>
    <td style="color:rgb(0, ${Math.min(255, Math.max(0, 255 * nation.getGroundStrength(false, false) / ((double) 4 * enemy.getGroundStrength(false, false))))}, 0)">${nation.getTanks()}</td>
    <td style="color:rgb(0, ${Math.min(255, Math.max(0, 255 * nation.getAircraft() / ((double) 5 * enemy.getAircraft())))}, 0)">${nation.getAircraft()}</td>
    <td style="color:rgb(0, ${Math.min(255, Math.max(0, 255 * nation.getShips() / ((double) 5 * enemy.getShips())))}, 0)">${nation.getShips()}</td>
    <td>${nation.getMissiles()}/${nation.getNukes()}</td>
    <td>${nation.getOff()}/${nation.getDef()}</td>
    <td>${!isAttacker?warCard.defenderMAP:warCard.attackerMAP}</td>
    <td><div class="progress-bar" role="progressbar"
             aria-valuenow="${(!isAttacker?warCard.defenderResistance:warCard.attackerResistance)}" aria-valuemin="0" aria-valuemax="100"
             style="width:${Math.max(10,(!isAttacker?warCard.defenderResistance:warCard.attackerResistance))}%;background-color:rgb(${MathMan.clamp(255 - (!isAttacker?warCard.defenderResistance:warCard.attackerResistance) * 255 / 100, 0, 255)}, ${MathMan.clamp((!isAttacker?warCard.defenderResistance:warCard.attackerResistance) * 255 / 100, 0, 255)}, 0)!important">${(!isAttacker?warCard.defenderResistance:warCard.attackerResistance)}</div></td>
</tr>
<tr class="border-top border-1 border-secondary">
    <td colspan="3"></td>
    <td colspan="2">
        <a class="btn btn-secondary btn-sm d-flex p-0 m-0 justify-content-center" href="https://politicsandwar.com/nation/war/groundbattle/war=${war.warId}">&#128130; ground attack</a>
    </td>
    <td><a class="btn btn-secondary btn-sm d-flex p-0 m-0 justify-content-center" href="https://politicsandwar.com/nation/war/airstrike/war=${war.warId}">&#9992;&#65039; airstrike</a></td>
    <td><a class="btn btn-secondary btn-sm d-flex p-0 m-0 justify-content-center" href="https://politicsandwar.com/nation/war/navalbattle/war=${war.warId}">&#128674; naval</a></td>
    <td class="d-flex justify-content-center">
        <a class="btn btn-secondary w-50 btn-sm p-0 m-0 justify-content-center" href="https://politicsandwar.com/nation/war/missile/war=${war.warId}">&#128640;</a>|<a class="btn btn-secondary w-50 btn-sm p-0 m-0 justify-content-center" href="https://politicsandwar.com/nation/war/nuke/war=${war.warId}">&#9762;&#65039;</a>
    </td>

</tr>
<tr class="border-bottom border-1 border-secondary mb-2">
    <td colspan="100">
        <a href="https://politicsandwar.com/nation/war/timeline/war=${war.warId}">War link</a><br>
        <hr>
        @if(nation.hasProject(Projects.MISSILE_LAUNCH_PAD) && enemy.hasProject(Projects.IRON_DOME))
            <p>IRON_DOME (50% chance to thwart missiles)</p>
        @endif
        @if(nation.hasProject(Projects.NUCLEAR_RESEARCH_FACILITY) && enemy.hasProject(Projects.VITAL_DEFENSE_SYSTEM))
            <p>VITAL_DEFENSE_SYSTEM (20% chance to thwart nukes)</p>
        @endif
        @if(warCard.attackerFortified)

        @endif
        @if(warCard.defenderFortified)

        @endif
        @if(warCard.blockaded == nation.getNation_id())

        @endif
        @if(warCard.blockaded == enemy.getNation_id())

        @endif
        @if(warCard.groundControl == nation.getNation_id())

        @endif
        @if(warCard.groundControl == enemy.getNation_id())

        @endif
        @if(warCard.airSuperiority == nation.getNation_id())

        @endif
        @if(warCard.airSuperiority == enemy.getNation_id())

        @endif
        <div class="accordion" id="oddsAccordion">
            <div class="accordion-item m-1 bg-primary bg-gradient">
                <h2 class="accordion-header" id="headingodds${war.warId}">
                    <button class="accordion-button collapsed p-1 text-white btn-sm bg-primary bg-gradient" type="button" data-bs-toggle="collapse" data-bs-target="#collapseodds${war.warId}" aria-expanded="false" aria-controls="collapseodds${war.warId}">
                        Show odds
                    </button>
                </h2>
                <div id="collapseodds${war.warId}" class="accordion-collapse collapse" aria-labelledby="headingodds${war.warId}" data-bs-parent="#oddsAccordion${war.warId}">
                    <div class="accordion-body  bg-light">
                        <div><div class="bg-danger align-middle d-inline-block m-1 border border-2 border-dark" style="width:1.5em;height:1.5em"></div>Utter Failure</div>
                        <div><div class="bg-warning align-middle d-inline-block m-1 border border-2 border-dark" style="width:1.5em;height:1.5em"></div>Pyrrhic Victory</div>
                        <div><div class="bg-info align-middle d-inline-block m-1 border border-2 border-dark" style="width:1.5em;height:1.5em"></div>Moderate Success</div>
                        <div><div class="bg-primary align-middle d-inline-block m-1 border border-2 border-dark" style="width:1.5em;height:1.5em"></div>Immense Triumph</div>
                        @if(nation.getSoldiers() > enemy.getGroundStrength(true, false) * 0.3 && enemy.getGroundStrength(true, false) > 0)
                            @template.guild.odds(ws = ws, title = "Soldiers (unarmed) v Enemy", attStr = nation.getSoldiers(), defStr = enemy.getGroundStrength(true, false))
                        @endif
                        @if(nation.getSoldiers() * 1.7_5 > enemy.getGroundStrength(true, false) * 0.3 && enemy.getGroundStrength(true, false) > 0)
                            @template.guild.odds(ws = ws, title = "Soldiers (munitions) v Enemy", attStr = nation.getSoldiers() * 1.7_5, defStr = enemy.getGroundStrength(true, false))
                        @endif
                        @if(nation.getGroundStrength(true, false) > 0 && enemy.getGroundStrength(true, false) > 0)
                            @template.guild.odds(ws = ws, title = "Ground", attStr = nation.getGroundStrength(true, false), defStr = enemy.getGroundStrength(true, false))
                        @endif
                        @if(nation.getAircraft() > 0 && enemy.getAircraft() > 0)
                            @template.guild.odds(ws = ws, title = "Airstrike", attStr = nation.getAircraft(), defStr = enemy.getAircraft())
                        @endif
                        @if(nation.getShips() > 0 && enemy.getShips() > 0)
                            @template.guild.odds(ws = ws, title = "Naval", attStr = nation.getShips(), defStr = enemy.getShips())
                        @endif
                    </div>
                </div>
            </div>
        </div>
<!--        <hr>-->
        @if(permitted != null && isAttacker && (db.isEnemyAlliance(war.getDefender_aa())))
        <div class="alert @if(permitted.isEmpty())
                alert-danger
            @else
                alert-success
            @endif
            ">
            <p class="lead">This is an enemy nation</p>
            @if(permitted.isEmpty())
                <p><b>Please avoid defeating this enemy. None of the following allowed beige reasons are met</b></p>
                <ul>
                @for (BeigeReason reason : db.getAllowedBeigeReasons(enemy))
                    <li><u>${reason}</u><br>${reason.getDescription()}</li>
                @endfor
                </ul>
            @else
                <p><b>You can defeat this enemy for the following reasons</b></p>
                <ul>
                    @for (BeigeReason reason : permitted)
                    <li><u>${reason}</u><br>${reason.getDescription()}</li>
                    @endfor
                </ul>
            @endif
            <div class="accordion" id="beigeAccordion${war.warId}">
                <div class="accordion-item bg-primary bg-gradient">
                    <h2 class="accordion-header" id="headingBeige${war.warId}">
                        <button class="accordion-button collapsed p-1 text-white btn-sm bg-primary bg-gradient" type="button" data-bs-toggle="collapse" data-bs-target="#collapseBeige${war.warId}" aria-expanded="false" aria-controls="collapseBeige${war.warId}">
                            Beige Cycling Info
                        </button>
                    </h2>
                    <div id="collapseBeige${war.warId}" class="accordion-collapse collapse" aria-labelledby="headingBeige${war.warId}" data-bs-parent="#beigeAccordion${war.warId}">
                        <div class="accordion-body bg-light">
                <h5>What is beige?</h5>
                <p>A nation defeated gets 2 more days of being on the beige color. Beige protects from new war declarations. We want to have active enemies always in war, so they don&apos;t have the opportunity to build back up.</p>
                <h5>Tips for avoiding unnecessary attacks:</h5>
                <ol>
                    <li>Don&apos;t open with navals if they have units which are a threat. Ships can&apos;t attack planes, tanks or soldiers.</li>
                    <li>Dont naval if you already have them blockaded</li>
                    <li>Never airstrike infra, cash, or small amounts of units - wait for them to build more units</li>
                    <li>If they just have some soldiers and can&apos;t get a victory against you, don&apos;t spam ground attacks.</li>
                    <li>If the enemy only has soldiers (no tanks) and you have max planes. Airstriking soldiers kills more soldiers than a ground attack will.</li>
                    <li>Missiles/Nukes do NOT kill any units</li>
                </ol>
                <p>note: You can do some unnecessary attacks if the war is going to expire, or you need to beige them as part of a beige cycle</p>

                <h5>What is beige cycling?</h5>
                <p>Beige cycling is when we have a weakened enemy, and 3 strong nations declared on that enemy - then 1 nation defeats them, whilst the other two sit on them whilst they are on beige. <br>
                    When their 2 days of beige from the defeat ends, another nation declares on the enemies free slot and the next nation defeats the enemy.</p>
                <h5>Beige cycling checklist:</h5>
                <ol>
                    <li>Is the enemy military mostly weakened/gone?</li>
                    <li>Is the enemy not currently on beige?</li>
                    <li>Do they have 3 defensive wars, with the other two attackers having enough military?</li>
                    <li>Are you the first person to have declared?</li>
                </ol>
                <p>Tip: Save your MAP. Avoid going below 40 resistance until you are GO for beiging them</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        @endif
    </td>
</tr>
    </tbody>
</table>