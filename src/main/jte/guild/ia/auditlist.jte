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
@param IACheckup.AuditType type
@param Map<DBNation, String> nationMap

<div class="accordion rounded shadow searchable" id="Accordion${type}">
    <div class="accordion-item">
        <h3 class="accordion-header" id="heading${type}">
            <button class="accordion-button collapsed p-1 btn-lg bg-${type.severity.name().toLowerCase()} text-white" type="button" data-bs-toggle="collapse" data-bs-target="#collapse${type}" aria-expanded="false" aria-controls="collapse${type}">
                ${type} - (${nationMap.size()} nations)
            </button>
        </h3>
        <div id="collapse${type}" class="accordion-collapse collapse" aria-labelledby="heading${type}" data-bs-parent="#Accordion${type}">
            <div class="accordion-body bg-light">
                <table class="table">
                    <thead>
                        <th>Nation</th>
                        <th>Cities</th>
                        <th>Off/Def</th>
                        <th>mmr[unit]</th>
                        <th>mmr[build]</th>
                        <th>avg_infra</th>
                    </thead>
                    <tbody>
                    @for (Map.Entry<DBNation, String> entry : nationMap.entrySet())
                        @template.guild.ia.auditentry(ws = ws, type = type, nation = entry.getKey(), message = entry.getValue())
                    @endfor
                    </tbody>
                </table>

            </div>
        </div>
    </div>
</div>