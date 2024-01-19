@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.manager.v2.binding.annotation.Command
@import link.locutus.discord.commands.manager.v2.binding.annotation.Default
@import link.locutus.discord.commands.manager.v2.binding.annotation.Me
@import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance
@import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission
@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.entities.Transaction2
@import link.locutus.discord.db.entities.DBAlliance
@import link.locutus.discord.db.entities.DBNation
@import link.locutus.discord.user.Roles
@import link.locutus.discord.util.StringMan
@import link.locutus.discord.util.TimeUtil
@import link.locutus.discord.util.discord.DiscordUtil
@import link.locutus.discord.util.offshore.test.IACategory
@import link.locutus.discord.util.offshore.test.IAChannel
@import link.locutus.discord.util.task.ia.IACheckup
@import net.dv8tion.jda.api.entities.Guild
@import net.dv8tion.jda.api.entities.Member
@import net.dv8tion.jda.api.entities.Role
@import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
@import java.io.IOException
@import java.util.ArrayList
@import java.util.Collections
@import java.util.Comparator
@import java.util.HashMap
@import java.util.HashSet
@import java.util.List
@import java.util.Map
@import java.util.Set
@import java.util.concurrent.TimeUnit
@import java.util.stream.Collectors
@param WebStore ws
@param DBNation mentor
@param List<DBNation> myMentees
@param IACategory iaCat
@param GuildDB db
@param Map<DBNation, IACategory.SortedCategory> categoryMap
@param Map<DBNation, Boolean> passedMap
@param Map<Integer, Long> lastMentorTxByNationId
@param List<DBNation> mentorsWithRole
@param Map<DBNation, Integer> numPassedMap
@param IACheckup checkup2

<h2>Mentor: <a href="${mentor.getNationUrl()}">${mentor.getNation()}</a> | <a href="discord://discordapp.com/users/${mentor.getUserId()}">@${mentor.getUserDiscriminator()}</a></h2>
@if(mentor.getActive_m() > 4880)
<div class="alert alert-danger p-1 m-1"><b>Mentor is inactive: </b> ${TimeUtil.minutesToTime(mentor.getActive_m())}</div>
@endif
@if(mentor.getVm_turns() > 0)
<div class="alert alert-danger p-1 m-1"><b>Mentor is VM: </b> ${TimeUtil.turnsToTime(mentor.getVm_turns())}</div>
@endif
@if(mentor.getUser() == null)
<div class="alert alert-danger p-1 m-1"><b>Mentor is NOT verified</b></div>
@elseif(!Roles.MEMBER.has(mentor.getUser(), db.getGuild()))
<div class="alert alert-danger p-1 m-1"><b>Mentor is NOT a member</b></div>
@elseif(!Roles.hasAny(mentor.getUser(), db.getGuild(), Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERVIEWER, Roles.MENTOR))
<div class="alert alert-warning p-1 m-1"><b>Mentor is NOT gov or staff</b>(see roles on discord)</div>
@endif
@if(lastMentorTxByNationId.getOrDefault(mentor.getNation_id(), 0L) == 0)
<div class="alert alert-danger p-1 m-1"><b>Mentor has not mentored</b></div>
@elseif(lastMentorTxByNationId.getOrDefault(mentor.getNation_id(), 0L) < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8))
<div class="alert alert-warning p-1 m-1"><b>Mentor has not mentored in: </b>${TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - lastMentorTxByNationId.getOrDefault(mentor.getNation_id(), 0L))}</div>
@endif
<div class="alert alert-info p-1 m-1">
    <b>Graduated Mentees:</b> ${numPassedMap.getOrDefault(mentor, 0)}
</div>
<table class="table">
    <thead>
        <tr>
            <th>Nation</th>
            <th>City</th>
            <th>MMR[unit]</th>
            <th>MMR[build]</th>
            <th>Off</th>
            <th>Channel</th>
            <th>Category</th>
            <th>Action</th>
<!--            <th>Audit</th>-->
        </tr>
    </thead>
    <tbody>
    @for (DBNation myMentee : myMentees)
        !{var checkupResult = checkup2 == null ? null : checkup2.checkupSafe(myMentee, true, true);}
        @template.guild.ia.mentee(ws = ws, mentee = myMentee, myChan = iaCat.get(myMentee), category = categoryMap.get(myMentee), checkup = checkupResult)
    @endfor
    </tbody>
</table>