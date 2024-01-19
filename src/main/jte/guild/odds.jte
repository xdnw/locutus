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
@import link.locutus.discord.util.PnwUtil
@import java.util.Collection
@import link.locutus.discord.db.entities.DBWar
@import link.locutus.discord.util.task.war.WarCard
@import link.locutus.discord.apiv1.enums.AttackType
@import link.locutus.discord.apiv1.enums.city.JavaCity
@import link.locutus.discord.util.TimeUtil
@import link.locutus.discord.pnw.BeigeReason

@param WebStore ws
@param String title
@param double attStr
@param  double defStr
<div>
<h5>Odds ${title}:${MathMan.format(attStr)} vs ${MathMan.format(defStr)}</h5>
<div class="progress">
    @for (int success = 0; success <= 3; success++)
    @template.guild.oddssuccess(ws = ws, odds = (PnwUtil.getOdds(attStr, defStr, success) * 100), success = success)
    @endfor
</div>
</div>