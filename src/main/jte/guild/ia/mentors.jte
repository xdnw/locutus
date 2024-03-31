@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.manager.v2.binding.annotation.Command
@import link.locutus.discord.commands.manager.v2.binding.annotation.Default
@import link.locutus.discord.commands.manager.v2.binding.annotation.Me
@import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance
@import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission
@import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM
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
@param IACategory iaCat
@param GuildDB db
@param List<Map.Entry<DBNation, List<DBNation>>> mentorsSorted
@param Map<DBNation, IACategory.AssignedMentor> menteeMentorMap
@param Map<DBNation, IACategory.SortedCategory> categoryMap
@param Map<DBNation, Boolean> passedMap
@param Map<Integer, Long> lastMentorTxByNationId
@param List<DBNation> mentorsWithRole
@param Map<DBNation, Integer> numPassedMap
@param List<DBNation> membersUnverified
@param List<DBNation> membersNotOnDiscord
@param List<DBNation> nationsNoIAChan
@param List<DBNation> noMentor
@param List<DBNation> idleMentors
@param IACheckup checkup
@template.main(ws = ws, title = "Mentors", navbar = null, content = @`
        <div class="bg-lightcontainer mt-3 rounded shadow py-1">
            <div class=""></div>
        </div>
@for (Map.Entry<DBNation, List<DBNation>> entry : mentorsSorted)
    @if(!entry.getValue().isEmpty())
        <div class="bg-white container mt-3 rounded shadow py-1">
            @template.guild.ia.mentor(ws = ws, mentor = entry.getKey(), myMentees = entry.getValue(), iaCat = iaCat, db = db, categoryMap= categoryMap, passedMap= passedMap,
            lastMentorTxByNationId = lastMentorTxByNationId,
            mentorsWithRole= mentorsWithRole, numPassedMap = numPassedMap,
            checkup2 = checkup)
        </div>
    @endif
@endfor
@if(!idleMentors.isEmpty())
<div class="bg-white container mt-3 rounded shadow py-1">
    <h2>Idle Mentors</h2>
    <table>
        <thead>
        <th>Nation</th>
        <th>User</th>
        <th>City</th>
        <th>Active</th>
        </thead>
        <tbody>
        @for (DBNation nation : idleMentors)
        <tr>
            <td><a href="${nation.getUrl()}">${nation.getNation()}</a></td>
            <td><a href="discord://discordapp.com/users/${nation.getUserId()}">@${nation.getUserDiscriminator()}</a></td>
            <td>${nation.getCities()}</td>
            <td>${TimeUtil.minutesToTime(nation.active_m())}</td>
        </tr>
        @endfor
        </tbody>
    </table>
</div>
@endif
@if(!noMentor.isEmpty())
<div class="bg-white container mt-3 rounded shadow py-1">
    <h2>Members Lacking Mentor</h2>
    <table>
        <thead>
        <th>Nation</th>
        <th>City</th>
        <th>Active</th>
        <th>Channel</th>
        <th>Category</th>
        <th>Action</th>
        </thead>
        <tbody>
        @for (DBNation nation : noMentor)
        <tr>
            <td><a href="${nation.getUrl()}">${nation.getNation()}</a></td>
            <td>${nation.getCities()}</td>
            <td>${TimeUtil.minutesToTime(nation.active_m())}</td>
            <td>@if(iaCat.get(nation) != null && iaCat.get(nation).getChannel() != null)
                <a href="${DiscordUtil.getChannelUrl(iaCat.get(nation).getChannel())}">#${iaCat.get(nation).getChannel().getName()}</a>
                @endif</td>
            <td>
                ${categoryMap.get(nation)}
            </td>
            <td>
                <button class="btn btn-sm btn-primary" cmd="${CM.interview.mentee.cmd.create(nation.getNation_id() + "", "true").toSlashCommand(false)}">Assign Self</button>
            </td>
        </tr>
        @endfor
        </tbody>
    </table>
</div>
@endif
@if(!membersNotOnDiscord.isEmpty())
<div class="bg-white container mt-3 rounded shadow py-1">
    <h2>Members not in Guild</h2>
    <table>
        <thead>
        <th>Nation</th>
        <th>City</th>
        <th>Active</th>
        <th>Channel</th>
        <th>Category</th>
        </thead>
        <tbody>
        @for (DBNation nation : membersNotOnDiscord)
        <tr>
            <td><a href="${nation.getUrl()}">${nation.getNation()}</a></td>
            <td>${nation.getCities()}</td>
            <td>${TimeUtil.minutesToTime(nation.active_m())}</td>
            <td>@if(iaCat.get(nation) != null && iaCat.get(nation).getChannel() != null)
                <a href="${DiscordUtil.getChannelUrl(iaCat.get(nation).getChannel())}">#${iaCat.get(nation).getChannel().getName()}</a>
                @endif</td>
            <td>
                ${categoryMap.get(nation)}
            </td>
        </tr>
        @endfor
        </tbody>
    </table>
</div>
@endif
@if(!membersUnverified.isEmpty())
    <div class="bg-white container mt-3 rounded shadow py-1">
        <h2>Unverified Members</h2>
        <table>
            <thead>
                <th>Nation</th>
                <th>City</th>
                <th>Active</th>
                <th>Channel</th>
                <th>Category</th>
            </thead>
            <tbody>
                @for (DBNation nation : membersUnverified)
                    <tr>
                        <td><a href="${nation.getUrl()}">${nation.getNation()}</a></td>
                        <td>${nation.getCities()}</td>
                        <td>${TimeUtil.minutesToTime(nation.active_m())}</td>
                        <td>@if(iaCat.get(nation) != null && iaCat.get(nation).getChannel() != null)
                            <a href="${DiscordUtil.getChannelUrl(iaCat.get(nation).getChannel())}">#${iaCat.get(nation).getChannel().getName()}</a>
                        @endif</td>
                        <td>
                            ${categoryMap.get(nation)}
                        </td>
                    </tr>
                @endfor
            </tbody>
        </table>
    </div>
@endif
@if(!nationsNoIAChan.isEmpty())
<div class="bg-white container mt-3 rounded shadow py-1">
    <h2>Members without channel</h2>
    <table>
        <thead>
        <th>Nation</th>
        <th>City</th>
        <th>Active</th>
        </thead>
        <tbody>
        @for (DBNation nation : nationsNoIAChan)
        <tr>
            <td><a href="${nation.getUrl()}">${nation.getNation()}</a></td>
            <td>${nation.getCities()}</td>
            <td>${TimeUtil.minutesToTime(nation.active_m())}</td>
        </tr>
        @endfor
        </tbody>
    </table>
</div>
@endif
`)

