@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.db.GuildDB
@import net.dv8tion.jda.api.entities.Guild
@import java.util.*
@param WebStore ws
@param Set<GuildDB> guildDbs
@param GuildDB current
@param GuildDB allianceGuild
@param String registerLink
@param String locutusInvite
@param String joinLink
@template.main(ws = ws, title = "Guilds", navbar = null, content = @`
<script>
$(document).ready(function(){
  $("#myInput").on("keyup", function() {
    var value = $(this).val().toLowerCase();
    $(".guild-entry").filter(function() {
      $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)
    });
  });
});
</script>
<div class="container">
    <div class="row">
        <div class="col-sm">
    @if(registerLink != null)
    <div class="p-1 my-1 alert alert-warning alert-dismissible fade show" role="alert">
        <strong>Discord is a chat application.</strong> Register your discord account to access alliance discord functionality
        <a href="https://discord.com" class="btn btn-primary btn-sm" role="button" aria-pressed="true">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-discord" viewBox="0 0 16 16">
                <path d="M13.545 2.907a13.227 13.227 0 0 0-3.257-1.011.05.05 0 0 0-.052.025c-.141.25-.297.577-.406.833a12.19 12.19 0 0 0-3.658 0 8.258 8.258 0 0 0-.412-.833.051.051 0 0 0-.052-.025c-1.125.194-2.22.534-3.257 1.011a.041.041 0 0 0-.021.018C.356 6.024-.213 9.047.066 12.032c.001.014.01.028.021.037a13.276 13.276 0 0 0 3.995 2.02.05.05 0 0 0 .056-.019c.308-.42.582-.863.818-1.329a.05.05 0 0 0-.01-.059.051.051 0 0 0-.018-.011 8.875 8.875 0 0 1-1.248-.595.05.05 0 0 1-.02-.066.051.051 0 0 1 .015-.019c.084-.063.168-.129.248-.195a.05.05 0 0 1 .051-.007c2.619 1.196 5.454 1.196 8.041 0a.052.052 0 0 1 .053.007c.08.066.164.132.248.195a.051.051 0 0 1-.004.085 8.254 8.254 0 0 1-1.249.594.05.05 0 0 0-.03.03.052.052 0 0 0 .003.041c.24.465.515.909.817 1.329a.05.05 0 0 0 .056.019 13.235 13.235 0 0 0 4.001-2.02.049.049 0 0 0 .021-.037c.334-3.451-.559-6.449-2.366-9.106a.034.034 0 0 0-.02-.019Zm-8.198 7.307c-.789 0-1.438-.724-1.438-1.612 0-.889.637-1.613 1.438-1.613.807 0 1.45.73 1.438 1.613 0 .888-.637 1.612-1.438 1.612Zm5.316 0c-.788 0-1.438-.724-1.438-1.612 0-.889.637-1.613 1.438-1.613.807 0 1.451.73 1.438 1.613 0 .888-.631 1.612-1.438 1.612Z"></path>
            </svg>
            Create a Discord Account
        </a>
        <a href="${registerLink}" class="btn btn-secondary btn-sm" role="button" aria-pressed="true">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-link" viewBox="0 0 16 16">
                <path d="M6.354 5.5H4a3 3 0 0 0 0 6h3a3 3 0 0 0 2.83-4H9c-.086 0-.17.01-.25.031A2 2 0 0 1 7 10.5H4a2 2 0 1 1 0-4h1.535c.218-.376.495-.714.82-1z"/>
                <path d="M9 5.5a3 3 0 0 0-2.83 4h1.098A2 2 0 0 1 9 6.5h3a2 2 0 1 1 0 4h-1.535a4.02 4.02 0 0 1-.82 1H12a3 3 0 1 0 0-6H9z"/>
            </svg>
            Link your Discord with Locutus
        </a>
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    </div>
    @endif
    @if(locutusInvite != null)
    <div class="p-1 my-1 alert alert-warning alert-dismissible fade show" role="alert">
        <strong>Locutus is not added setup for your alliance.</strong> Add Locutus to your alliance discord server to access alliance discord functionality
        <a href="${locutusInvite}">
            Invite Locutus
        </a>
        <a href="https://github.com/xdnw/locutus/wiki">
            Wiki
        </a>
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    </div>
    @endif
    @if(joinLink != null)
    <div class="p-1 my-1 alert alert-warning alert-dismissible fade show" role="alert">
        <strong>You are not a member of an alliance.</strong> Find and join an alliance to gain access to alliance functions
        <a href="${joinLink}">
            Join Alliance
        </a>
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    </div>
    @endif
        </div>
    </div>
    <div class="row">
        <div class="col-sm">
    <input class="form-control form-control-sm" id="myInput" type="text" placeholder="Search..">
            </div>
    </div>
    @if(guildDbs.isEmpty())
    <div class="p-1 my-1 alert alert-error alert-dismissible fade show" role="alert">
        <strong>No guilds found</strong>
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    </div>
    @endif
</div>
    </div>
    <div class="row">
        <div class="col-sm">
            <input class="form-control form-control-sm" id="myInput" type="text" placeholder="Search..">
        </div>
    </div>
    @for (GuildDB db : guildDbs)
        <!--  Add argument to the guildentry template which sets the highlight flag and additional note -->
        <!-- if current is not null and matches db, set the highlight to "primary" and note to "Currently Selected"  -->
        <!-- if allianceGuild is not null and matches db, set the highlight to "secondary" and note to "Your Alliance"  -->
        @if(current != null && db.getIdLong() == current.getIdLong())
            @template.guild.guildentry(ws = ws, guild = db.getGuild(), db = db, highlight = true, note = "Currently Selected")
        @elseif(allianceGuild != null && db.getIdLong() == allianceGuild.getIdLong())
            @template.guild.guildentry(ws = ws, guild = db.getGuild(), db = db, highlight = true, note = "Your Alliance")
        @else
            @template.guild.guildentry(ws = ws, guild = db.getGuild(), db = db, highlight = false, note = null)
        @endif
    @endfor
</div>
`)