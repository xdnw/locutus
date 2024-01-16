@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM
@import link.locutus.discord.web.commands.page.IndexPages
@import java.util.*
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
@import link.locutus.discord.util.PnwUtil
@import java.util.Collection
@import link.locutus.discord.db.guild.GuildKey
@import link.locutus.discord.db.entities.DBWar
@import link.locutus.discord.util.task.war.WarCard
@import link.locutus.discord.config.Settings
@import link.locutus.discord.apiv1.enums.AttackType
@import link.locutus.discord.apiv1.enums.city.JavaCity
@import link.locutus.discord.web.jooby.WebRoot
@import link.locutus.discord.db.entities.announce.Announcement

@param WebStore ws
@param Guild guild
@param GuildDB db
@param  DBNation nation
@param  User author
@param  double[] deposits
@param  Map<IACheckup.AuditType, Map.Entry<Object, String>> checkup
@param  Collection<JavaCity> cities
@param  boolean isFightingActives
@param  Map<DBWar, DBNation> offensives
@param  Map<DBWar, DBNation> defensives
@param  Map<DBWar, WarCard> warCards
@param  Map<DBWar, AttackType> recommendedAttacks
@param  List<Announcement.PlayerAnnouncement> announcements
@template.main(ws = ws, title = "Guild Alliance index", navbar = null, content = @`
@if(checkup != null)
    <div class="container">
        @for (Announcement.PlayerAnnouncement plrAnn : announcements)
            @template.alliance.playerannouncement(ws = ws, db = db, plrAnn = plrAnn, showFooter = true, showReceiver = false, includeDate = true)
        @endfor
        <a class="btn btn-primary btn-sm" href="${WebRoot.REDIRECT}/announcements/-a">View All Announcements</a>
        @if(Roles.ADMIN.has(author, guild))
            <a class="btn btn-danger btn-sm" href="${WebRoot.REDIRECT}/manageannouncements">Manage Announcements</a>
        @endif
    </div>
    <div class="bg-white container mt-3 rounded shadow py-1">
        <h2 class=""><i class="bi bi-exclamation-diamond-fill text-danger px-1 me-2"></i>${checkup.size()} Audits</h2>
        <div>
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
            ${entry.getValue().getValue()};
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        </div>
        @endif
    @endfor
        </div>
    </div>
}
<div class="bg-white container mt-3 rounded shadow py-1">
    <h2 class="">&#127975; ATM</h2>
    <hr>
    <p class="lead">It is recommended to safekeep funds you aren't using, as to avoid becoming a target and taking unnecessary losses.</p>
    <p>
        <b>Your deposits: </b>
        ${PnwUtil.resourcesToString(PnwUtil.normalize(deposits))}
    </p>
    <a href="${WebRoot.REDIRECT}/command/withdraw"  class="btn btn-primary btn">&#128279; Withdraw Funds</a>
    <a href="https://politicsandwar.com/alliance/id=${nation.getAlliance_id()}&display=bank" class="btn btn-primary btn">Deposit Funds <i class="bi bi-box-arrow-up-right text-light"></i></a>
</div>
@if(offensives.size() + defensives.size() > 0)
    @template.guild.mywars(ws = ws, db = db, nation = nation, author = author, cities = cities, isFightingActives = isFightingActives, offensives = offensives, defensives = defensives, warCards = warCards, recommendedAttacks = recommendedAttacks)
@endif
@if(!db.getCoalition(Coalition.ENEMIES).isEmpty())
    <div class="bg-white container mt-3 rounded shadow py-1">

        <h2 class="">&#9876;&#65039; War Finder</h2>
        <hr>
        <p>
            <button class="btn btn-primary" type="button" data-bs-toggle="collapse" data-bs-target="#collapseEnemies" aria-expanded="false" aria-controls="collapseEnemies">
                Show enemies list
            </button>
        </p>
        <div class="collapse" id="collapseEnemies">
            <div class="card card-body">
                <ol>
                    <div class="list-group">
                        @for (int enemyId : db.getCoalition(Coalition.ENEMIES))
                            @if(Locutus.imp().getNationDB().getAllianceName(enemyId) != null)
                                <li class="link-primary list-group-item bg-light">${Locutus.imp().getNationDB().getAllianceName(enemyId)}</li>
                            @endif
                        @endfor
                    </div>
                </ol>
            </div>
        </div>
        <div class="list-group">
        @if(nation.getTankPct() > 0.8 && nation.getAircraftPct() > 0.8)
            @if(db.hasAlliance())
                <a href="javascript:void(0)" cmd="${CM.war.find.enemy.cmd.create("#off>0,~enemies,#getAttacking(~allies,#active_m<7200)>0", null, null, null, null, "true", null, null, null, null, null).toSlashCommand(false)}" class="link-primary list-group-item bg-light">Priority War targets</a>
            @endif
            <a href="javascript:void(0)" cmd="${CM.war.find.enemy.cmd.create(null, null, null, null, null, "true", null, null, null, null, null).toSlashCommand(false)}" class="link-primary list-group-item bg-light">Secondary War targets</a>
            <a href="javascript:void(0)" cmd="${CM.war.find.enemy.cmd.create(null, null, null, null, null, null, null, null, null, null, null).toSlashCommand(false)}" class="link-primary list-group-item bg-light">All enemies</a>
        @endif
        <a href="javascript:void(0)" cmd="${CM.war.find.enemy.cmd.create(null, null, null, null, null, null, "true", "true", null, null, null).toSlashCommand(false)}" class="link-primary list-group-item bg-light">Weak enemies</a>
        <a href="javascript:void(0)" cmd="${CM.war.find.damage.cmd.create("~enemies", null, null, null, null, null, null, null, null, null, null).toSlashCommand(false)}" class="link-primary list-group-item bg-light">Find damage targets</a>
        </div>
    </div>
@endif
<div class="bg-white container mt-3 rounded shadow py-1">
    <h2 class="">&#127919; Raid Finder</h2>
    <a href="javascript:void(0)" cmd="${CM.spy.find.intel.cmd.create(null, null, null, null).toSlashCommand(false)}" class="link-primary list-group-item bg-light">Intel Op</a>
    <hr>
    <p class="lead">
        This is a tool, you are responsible for your own raids. Ask a gov member if you are unsure about a target
    </p>
    <div class="list-group">
    <a href="javascript:void(0)" replace for="raid-out-1" cmd="${CM.war.find.raid.cmd.create(null, "10", null, null, null, null, null, null, null, null, null).toSlashCommand(false)}" class="link-primary list-group-item bg-light">Safe inactive nones/apps</a>
        <div id="raid-out-1" class="bg-light border border-top-0 mx-1 mb-1 slide" style="display:none"></div>
    <a href="javascript:void(0)" replace for="raid-out-2" cmd="${CM.war.find.raid.cmd.create("(*,#color=beige)|(*,#vm_turns>0)", "25", "2d", null, null, null, null, null, null, null, null).toSlashCommand(false)}" class="link-primary list-group-item bg-light">List nations coming out of beige</a>
        <div id="raid-out-2" class="bg-light border border-top-0 mx-1 mb-1 slide" style="display:none"></div>
    <a href="javascript:void(0)" replace for="raid-out-3" cmd="${CM.war.find.raid.cmd.create("#tank%<20,#soldier%<40,*", "25", "2d", "true", null, null, null, null, null, null, null).toSlashCommand(false)}" class="link-primary list-group-item bg-light">List actives with minimal ground (2d inactive)</a>
        <div id="raid-out-3" class="bg-light border border-top-0 mx-1 mb-1 slide" style="display:none"></div>
    <a href="javascript:void(0)" replace for="raid-out-4" cmd="${CM.war.find.raid.cmd.create("#def>0,#RelativeStrength<1,*", "25", "0d", "true", null, null, null, null, null, null, null).toSlashCommand(false)}" class="link-primary list-group-item bg-light">Include actives losing their current wars</a>
        <div id="raid-out-4" class="bg-light border border-top-0 mx-1 mb-1 slide" style="display:none"></div>
    <a href="javascript:void(0)" replace for="raid-out-5" cmd="${CM.war.find.unprotected.cmd.create("*", "25", null, "true", null, null, null, null, "true", null).toSlashCommand(false)}" class="link-primary list-group-item bg-light">List actives possibly unable to counter properly</a>
        <div id="raid-out-5" class="bg-light border border-top-0 mx-1 mb-1 slide" style="display:none"></div>
    </div>
</div>
@if(Roles.ECON_GRANT_SELF.toRole(db.getGuild()) != null)
<div class="bg-white container mt-3 rounded shadow py-1">
    <h2 class="">&#127974; Grants</h2>
    <hr>
    <p class="lead">
        Grants are funds provided by the alliance to help you grow your nation. Debt from grants can expire after a period of time (typically 60 days, see the note when accepting a grant)<br><br>
        <b>note: </b>This interface can only approve <i>some</i> grants.
        @if(db.getOrNull(GuildKey.GRANT_REQUEST_CHANNEL) != null)
        If you would like more general assistance or advice, please <a href="https://discord.com/channels/${db.getIdLong()}/${((GuildMessageChannel) db.getOrNull(GuildKey.GRANT_REQUEST_CHANNEL)).getIdLong()}">open a ticket on discord.</a>
        @endif
    </p>
    <div class="list-group">
    <a href="${WebRoot.REDIRECT}/infragrants/${nation.getNation_id()}" class="link-primary list-group-item bg-light">&#127959;&#65039; Infra grants</a>
    <a href="${WebRoot.REDIRECT}/landgrants/${nation.getNation_id()}" class="link-primary list-group-item bg-light">&#127966;&#65039; Land grants</a>
    <a href="${WebRoot.REDIRECT}/citygrants/${nation.getNation_id()}" class="link-primary list-group-item bg-light">&#127961;&#65039; City grants</a>
    <a href="${WebRoot.REDIRECT}/projectgrants/${nation.getNation_id()}" class="link-primary list-group-item bg-light">&#128508; Project grants</a>
    </div>
</div>
@endif
@endif
`)