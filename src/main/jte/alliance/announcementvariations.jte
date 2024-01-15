@import link.locutus.discord.web.jooby.WebRoot
@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.entities.announce.Announcement
@import link.locutus.discord.db.entities.DBNation
@import java.util.List
@import link.locutus.discord.config.Settings
@import java.util.Date

@param GuildDB db
@param Announcement announcement
@param List<Announcement.PlayerAnnouncement> announcements

@template.main(content = @`
<div class="alert alert-dismissible fade show container-fluid mt-1 shadow border-start border-3 @if(announcement.active)
bg-white border-danger
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
            By <cite title="Author">@template.user.userlinkid(userId = announcement.sender)</cite> on <span id="date-${announcement.id}">announcement.date</span>
        </figcaption>
        <script>
            document.getElementById("date-${announcement.id}").innerHTML = new Date(${announcement.date}).toString()
        </script>
    </figure>
    Filter: <kbd>${announcement.filter}</kbd><br>
    Replacements: <kbd>${announcement.replacements}</kbd><br>

    <a href="${WebRoot.REDIRECT}/manageannouncements" type="button" class="btn btn-primary">View All Announcements</a>
</div>
<div class="container-fluid input-group input-group-lg">
    <input class="form-control form-control-sm" id="myInput" type="text" placeholder="Search..">
</div>
@for (Announcement.PlayerAnnouncement plrAnn : announcements)
    <div class="variation-entry">
        @template.alliance.playerannouncement(db = db, plrAnn = plrAnn, showFooter = false, showReceiver = true, includeDate = false)
    </div>
@endfor

<script>
$(document).ready(function(){
  $("#myInput").on("keyup", function() {
    var value = $(this).val().toLowerCase();
    $(".variation-entry").filter(function() {
      $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)
    });
  });
});
</script>
`, title = "Announcement Variations", navbar = null)