@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM
@import link.locutus.discord.web.jooby.WebRoot
@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.entities.announce.Announcement
@import link.locutus.discord.db.entities.DBNation
@import java.util.List
@import java.util.Date
@import link.locutus.discord.config.Settings

@param WebStore ws
@param GuildDB db
@param Announcement announcement
<div class="alert alert-dismissible fade show container-fluid mt-1 shadow border-start border-3 @if(announcement.active)
bg-lightborder-danger
@else
bg-secondary border-dark
@endif">
    <h4 class="bold">${announcement.title}@if(!announcement.active)
        <span class="position-absolute top-0 start-50 translate-middle badge rounded-pill bg-warning">archived</span>
    @endif</h4>
    <figure>
        <blockquote class="blockquote">
            <p class="markup">${announcement.body}</p>
        </blockquote>
        <figcaption class="blockquote-footer text-dark">
            By <cite title="Author">@template.user.userlinkid(ws = ws, userId = announcement.sender)</cite> on <span id="date-${announcement.id}">announcement.date</span>
        </figcaption>
        <script>
            document.getElementById("date-${announcement.id}").innerHTML = new Date(${announcement.date}).toString()
        </script>
    </figure>
    Filter: <kbd>${announcement.filter}</kbd><br>
    Replacements: <kbd>${announcement.replacements}</kbd><br>

    @if(announcement.active)
        <button cmd="${CM.announcement.archive.cmd.create(announcement.id + "", null).toSlashCommand(false)}" type="button" class="btn btn-danger">Archive</button>
    @else
        <button cmd="${CM.announcement.archive.cmd.create(announcement.id + "", "false").toSlashCommand(false)}" type="button" class="btn btn-danger">Unarchive</button>
    @endif
    <a href="${WebRoot.REDIRECT}/announcementvariations/${announcement.id}" type="button" class="btn btn-primary">View Variations</a>
</div>