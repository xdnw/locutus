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
@import link.locutus.discord.db.entities.InterviewMessage
@import net.dv8tion.jda.api.entities.User

@param Guild guild
@param GuildMessageChannel channel
@param User interviewUser
@param InterviewMessage message
@param boolean isMyChannel

<div class="card mb-4">
    <div class="card-body">
        <div class="d-flex justify-content-between">
            <div class="d-flex flex-row align-items-center">
                <img
                        src="${message.getAvatar()}"
                        alt="avatar"
                        width="25"
                        height="25"
                />
                <p class="small mb-0 ms-2 me-2">${message.getUsername()}</p>
                <i class="bi bi-dot"></i>
                <p class="small text-muted mb-0 ms-2 format-date">${message.date_created}</p>

            </div>
            <div class="d-flex flex-row align-items-center">
                <p class="small text-muted mb-0"><a href="discord://discord.com/channels/${guild.getIdLong()}/${channel.getIdLong()}/${message.message_id}"><kbd>Jump</kbd></a></p>
            </div>
        </div>
        <p>${message.message}</p>
    </div>
</div>