@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.entities.announce.Announcement
@import link.locutus.discord.db.entities.DBNation
@import java.util.List
@import java.util.Date

@param GuildDB db
@param Announcement.PlayerAnnouncement plrAnn
@param boolean showFooter
@param boolean showReceiver
@param boolean includeDate

<div class="alert alert-dismissible fade show container-fluid mt-1 shadow border-start border-3 @if(plrAnn.isActive())
bg-white border-danger
@else
bg-secondary border-dark
@endif">
    <h4 class="bold">${plrAnn.getParent().title}@if(plrAnn.isActive())
        <span class="position-absolute top-0 start-50 translate-middle badge rounded-pill bg-success">unread</span>
    @elseif (!plrAnn.getParent().active)
        <span class="position-absolute top-0 start-50 translate-middle badge rounded-pill bg-warning">archived</span>
    @endif</h4>
    <figure>
        <blockquote class="blockquote">
            <p class="markup">${plrAnn.getContent()}</p>
        </blockquote>
        @if(showFooter)
            <figcaption class="blockquote-footer text-dark">
                By <cite title="Author">@template.user.userlinkid(userId = plrAnn.getParent().sender)</cite>@if(includeDate) on <span id="date-${plrAnn.receiverNation}-${plrAnn.getParent().id}">plrAnn.getParent().date</span>@endif
            </figcaption>
            @if(includeDate)
            <script>
                document.getElementById("date-${plrAnn.receiverNation}-${plrAnn.getParent().id}").innerHTML = new Date(${plrAnn.getParent().date}).toString()
            </script>
            @endif
        @endif
        @if(showReceiver)
            <figcaption class="blockquote-footer text-dark">
                Received By <cite title="Author">@template.user.userlinknatid(nationId = plrAnn.receiverNation)</cite>@if(includeDate) on <span id="date-${plrAnn.receiverNation}">plrAnn.receiverNation</span> @endif
            </figcaption>
            @if(includeDate)
            <script>
                document.getElementById("date-${plrAnn.receiverNation}").innerHTML = new Date(${plrAnn.getParent().date}).toString()
            </script>
            @endif
        @endif
        @if(showReceiver || showFooter)

        @endif
    </figure>
    @if(showFooter)
        @if(plrAnn.isActive())
        <button cmd="$readannouncement ${plrAnn.getParent().id}" type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        @else
        <button cmd="$readannouncement ${plrAnn.getParent().id} false" type="button" class="btn btn-danger">Mark Unread</button>
        @endif
    @endif
</div>