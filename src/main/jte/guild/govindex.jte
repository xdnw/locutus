@import java.util.*
@import link.locutus.discord.apiv1.enums.city.project.Projects
@import link.locutus.discord.apiv1.enums.city.project.Project
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import net.dv8tion.jda.api.entities.User
@import net.dv8tion.jda.api.entities.Guild
@import link.locutus.discord.db.entities.DBNation
@import java.util.List
@import java.util.UUID
@import link.locutus.discord.apiv1.enums.DomesticPolicy
@import link.locutus.discord.util.TimeUtil
@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.guild.GuildKey
@import link.locutus.discord.db.entities.Coalition
@import link.locutus.discord.Locutus
@import link.locutus.discord.util.task.ia.IACheckup
@import link.locutus.discord.util.task.ia.AuditType
@import link.locutus.discord.user.Roles
@import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
@import link.locutus.discord.config.Settings
@import link.locutus.discord.util.PW
@import link.locutus.discord.web.jooby.WebRoot
@param WebStore ws
@param Guild guild
@param GuildDB db
@param DBNation nation
@param User author
@param Map<AuditType, Map.Entry<Object, String>> checkup
@template.main(ws = ws, title = "Guild Gov index", navbar = null, content = @`

@if(Roles.MILCOM.has(author, db.getGuild()))
<div class="bg-lightcontainer mt-3 rounded shadow py-1">
    <h2 class="">Milcom</h2>
</div>
@endif


@if(Roles.FOREIGN_AFFAIRS.has(author, db.getGuild()))

@endif

@if(Roles.INTERNAL_AFFAIRS.has(author, db.getGuild()))



@endif

@if(Roles.ECON.has(author, db.getGuild()))
<div class="bg-white container mt-3 rounded shadow py-1">
    <h2 class="">


    </h2>
    <hr>
<%--    <p class="lead">--%>
<%--        Grants are funds provided by the alliance to help you grow your nation. Debt from grants can expire after a period of time (typically 60 days, see the note when accepting a grant)<br><br>--%>
<%--        <b>note: </b>This interface can only approve <i>some</i> grants.--%>
<%--        @if(db.getOrNull(GuildKey.GRANT_REQUEST_CHANNEL) != null)--%>
<%--        If you would like more general assistance or advice, please <a href="https://discord.com/channels/${db.getIdLong()}/${(db.getOrNull(GuildKey.GRANT_REQUEST_CHANNEL)).getIdLong()}">open a ticket on discord.</a>--%>
<%--        @endif--%>
<%--    </p>--%>
<%--    <div class="list-group">--%>
<%--    <a href="${WebRoot.REDIRECT}/page/infragrants" class="link-primary list-group-item bg-light">&#127959;&#65039; Infra grants</a>--%>
<%--    <a href="${WebRoot.REDIRECT}/page/landgrants" class="link-primary list-group-item bg-light">&#127966;&#65039; Land grants</a>--%>
<%--    <a href="${WebRoot.REDIRECT}/page/citygrants" class="link-primary list-group-item bg-light">&#127961;&#65039; City grants</a>--%>
<%--    <a href="${WebRoot.REDIRECT}/page/projectgrants" class="link-primary list-group-item bg-light">&#128508; Project grants</a>--%>
<%--    </div>--%>
</div>
@endif
`)