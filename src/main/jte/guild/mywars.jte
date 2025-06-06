@import java.util.*
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.db.guild.GuildKey
@import link.locutus.discord.apiv1.enums.MilitaryUnit
@import link.locutus.discord.apiv1.enums.city.project.Projects
@import link.locutus.discord.apiv1.enums.city.project.Project
@import net.dv8tion.jda.api.entities.User
@import net.dv8tion.jda.api.entities.Guild
@import link.locutus.discord.db.entities.DBNation
@import java.util.List
@import java.util.UUID
@import link.locutus.discord.apiv1.enums.DomesticPolicy
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
@import link.locutus.discord.pnw.BeigeReason
@param WebStore ws
@param GuildDB db
@param DBNation nation
@param User author
@param Collection<JavaCity> cities
@param boolean isFightingActives
@param Map<DBWar, DBNation> offensives
@param Map<DBWar, DBNation> defensives
@param Map<DBWar, WarCard> warCards
@param Map<DBWar, AttackType> recommendedAttacks

@if(nation.getNumWars() > 0)
<div class="bg-lightcontainer mt-3 rounded shadow py-1">
    <h3 class="">Current Wars</h3>
    <hr>
    <a href="https://politicsandwar.com/nation/war/" class="btn btn-primary btn">War Page <i class="bi bi-box-arrow-up-right text-light"></i></a>
    <hr>

    @if(isFightingActives)
    <h4>Buy units</h4>
    <p class="border-left border-2 border-secondary ml-1">It is recommended to repurchase lost units after each attack</p>
    @if(MilitaryUnit.SOLDIER.getCap(() -> cities, nation::hasProject, nation.getResearchBits()) > 0)
    <a href="https://politicsandwar.com/nation/military/soldiers/" class="
    @if(nation.getSoldiers() * 1.05 < MilitaryUnit.SOLDIER.getCap(() -> cities, nation::hasProject, nation.getResearchBits()))
        btn btn-sm btn-primary
    @else
        btn btn-sm btn-secondary
    @endif
    ">Buy soldiers <i class="bi bi-box-arrow-up-right text-light"></i></a>
    @endif
    @if(MilitaryUnit.TANK.getCap(() -> cities, nation::hasProject, nation.getResearchBits()) > 0)
    <a href="https://politicsandwar.com/nation/military/tanks/" class="
    @if(nation.getTanks() * 1.05 < MilitaryUnit.TANK.getCap(() -> cities, nation::hasProject, nation.getResearchBits()))
        btn btn-sm btn-primary
    @else
        btn btn-sm btn-secondary
    @endif
    ">Buy tanks <i class="bi bi-box-arrow-up-right text-light"></i></a>
    @endif
    @if(MilitaryUnit.AIRCRAFT.getCap(() -> cities, nation::hasProject, nation.getResearchBits()) > 0)
    <a href="https://politicsandwar.com/nation/military/aircraft/" class="
    @if(nation.getAircraft() * 1.05 < MilitaryUnit.AIRCRAFT.getCap(() -> cities, nation::hasProject, nation.getResearchBits()))
        btn btn-sm btn-primary
    @else
        btn btn-sm btn-secondary
    @endif
    ">Buy aircraft <i class="bi bi-box-arrow-up-right text-light"></i></a>
    @endif
    @if(MilitaryUnit.SHIP.getCap(() -> cities, nation::hasProject, nation.getResearchBits()) > 0)
    <a href="https://politicsandwar.com/nation/military/navy/" class="
    @if(nation.getShips() * 1.05 < MilitaryUnit.SHIP.getCap(() -> cities, nation::hasProject, nation.getResearchBits()))
        btn btn-sm btn-primary
    @else
        btn btn-sm btn-secondary
    @endif
    ">Buy navy <i class="bi bi-box-arrow-up-right text-light"></i></a>
    @endif

    <a href="https://politicsandwar.com/nation/military/spies/" class="btn btn-sm btn-primary">Buy spies <i class="bi bi-box-arrow-up-right text-light"></i></a>

    @if(nation.getMissiles() == 0 && nation.getMissiles() < MilitaryUnit.MISSILE.getCap(() -> cities, nation::hasProject, nation.getResearchBits()))
    <a href="https://politicsandwar.com/nation/military/missiles/" class="btn btn-sm btn-primary">Buy missiles <i class="bi bi-box-arrow-up-right text-light"></i></a>
    @endif
    @if(nation.getNukes() == 0 && nation.getNukes() < MilitaryUnit.NUKE.getCap(() -> cities, nation::hasProject, nation.getResearchBits()))
    <a href="https://politicsandwar.com/nation/military/missiles/" class="btn btn-sm btn-primary">Buy nukes <i class="bi bi-box-arrow-up-right text-light"></i></a>
    @endif

    @else
    <a href="https://politicsandwar.com/nation/military/" class="btn btn-primary btn-sm">Buy military <i class="bi bi-box-arrow-up-right text-light"></i></a>
    @endif
    <hr>
    @if(!offensives.isEmpty())
    <h4 class="">Offensives</h4>
        @for (Map.Entry<DBWar, DBNation> entry : offensives.entrySet())
            @if(db.getOrNull(GuildKey.ENEMY_BEIGED_ALERT_VIOLATIONS) != null && db.isEnemyAlliance(entry.getKey().getDefender_aa()))
                @template.guild.mywartr(ws = ws, db = db, nation = nation, author = author, war = entry.getKey(), enemy = entry.getValue(), warCard = warCards.get(entry.getKey()), recommendedAttack = recommendedAttacks.get(entry.getKey()), isAttacker = true, permitted = BeigeReason.getAllowedBeigeReasons(db, nation, entry.getKey(), null))
            @else
                @template.guild.mywartr(ws = ws, db = db, nation = nation, author = author, war = entry.getKey(), enemy = entry.getValue(), warCard = warCards.get(entry.getKey()), recommendedAttack = recommendedAttacks.get(entry.getKey()), isAttacker = true, permitted = null)
            @endif
        @endfor
    @endif
    @if(!defensives.isEmpty())
        <h4 class="">Defensives</h4>
        @for (Map.Entry<DBWar, DBNation> entry : defensives.entrySet())
            @template.guild.mywartr(ws = ws, db = db, nation = nation, author = author, war = entry.getKey(), enemy = entry.getValue(), warCard = warCards.get(entry.getKey()), recommendedAttack = recommendedAttacks.get(entry.getKey()), isAttacker = false, permitted = null)
        @endfor
    @endif
</div>
@else
<div class="bg-white container mt-3 rounded shadow py-1">
    <h2>You currently have no wars</h2>
</div>
@endif