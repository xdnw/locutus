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

@param DBNation mentee
@param IAChannel myChan
@param IACategory.SortedCategory category
@param Map<IACheckup.AuditType, Map.Entry<Object, String>> checkup

<tr>
    <td><a href="${mentee.getNationUrl()}">${mentee.getNation()}</a></td>
    <td>${mentee.getCities()}</td>
    <td>${mentee.getMMR()}</td>
    <td>${mentee.getMMRBuildingStr()}</td>
    <td>${mentee.getOff()}</td>
    <td>
        @if(myChan != null && myChan.getChannel() != null)
        <a href="${DiscordUtil.getChannelUrl(myChan.getChannel())}">#${myChan.getChannel().getName()}</a>
        @endif
    </td>
    <td>
        @if(category != null)
            ${category.name()}
        @endif
    </td>
    <td>
        <button class="btn btn-sm btn-primary" cmd="${CM.interview.unassignMentee.cmd.create(mentee.getNation_id() + "").toSlashCommand(false)}">Unassign</button>
    </td>
</tr>
@if(checkup != null && !checkup.isEmpty())
<tr>
    <td colspan="100">
        <div class="accordion" id="auditAccordion${mentee.getNation_id()}">
            <div class="accordion-item">
                <h2 class="accordion-header" id="headingaudit${mentee.getNation_id()}">
                    <button class="accordion-button collapsed p-1 btn-sm" type="button" data-bs-toggle="collapse" data-bs-target="#collapseAudit${mentee.getNation_id()}" aria-expanded="false" aria-controls="collapseBeige${mentee.getNation_id()}">
                        ${checkup.size()} Audits (${checkup.keySet().stream().filter(f -> f.severity == IACheckup.AuditSeverity.DANGER).count()} CRITICAL|${checkup.keySet().stream().filter(f -> f.severity == IACheckup.AuditSeverity.WARNING).count()} WARNING)
                    </button>
                </h2>
                <div id="collapseAudit${mentee.getNation_id()}" class="accordion-collapse collapse" aria-labelledby="headingAudit${mentee.getNation_id()}" data-bs-parent="#auditAccordion${mentee.getNation_id()}">
                    <div class="accordion-body bg-light">
        @for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : checkup.entrySet())
            @if(entry.getValue() != null && entry.getValue().getValue() != null)
                <div role="alert" class="alert p-1 my-1 border alert-dismissible fade show
                @if(entry.getKey().severity == IACheckup.AuditSeverity.INFO)
                    alert-info border-info">
                @endif
                @if(entry.getKey().severity == IACheckup.AuditSeverity.WARNING)
                    alert-warning border-warning">
                @endif
                @if(entry.getKey().severity == IACheckup.AuditSeverity.DANGER)
                    alert-danger border-danger">
                @endif
                    <b>${entry.getKey()}</b><br>
                    ${entry.getValue().getValue()}
                    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                </div>
            @endif
        @endfor
                    </div>
                </div>
            </div>
        </div>
    </td>
</tr>
@endif