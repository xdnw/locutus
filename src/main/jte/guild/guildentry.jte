@import link.locutus.discord.db.GuildDB
@import net.dv8tion.jda.api.entities.Guild
@import link.locutus.discord.db.entities.DBAlliance

@param Guild guild
@param GuildDB db
@param boolean highlight
@param String note
@if(highlight)
    <div class="row p-2 m-3 guild-icon guild-entry bg-secondary bg-gradient" style="border-radius:10px">
        <h6>Example heading <span class="badge bg-secondary">${note}</span></h6>
@else
    <div class="row p-2 m-3 guild-icon guild-entry bg-primary bg-gradient" style="border-radius:10px">
@endif
    <div class="col-md-2">
        <img alt="guild-icon" class="img-fluid guild-icon" src="${guild.getIconUrl()}">
    </div>
    <div class="col-md-10 p-2">
        <h4 class="row text-white">${guild.getName()}/${guild.getIdLong()}</h4>
        <div class="row">
            <div class="col">
                <p class="lead"></p>
            </div>
        </div>
        <div class="row">
            <div class="col">
            <a href="/page/setguild/${guild.getIdLong()}" class="m-1 btn btn-primary btn-lg border-2 border-light shadow">Set Guild</a>
            @if(db.isValidAlliance())
                @for (DBAlliance alliance : db.getAllianceList().getAlliances())
                    <a href="https://politicsandwar.com/alliance/id=${alliance.getAlliance_id()}" class="m-1 btn btn-primary btn-lg border-2 border-light shadow">View ${alliance.getQualifiedName()} 🡕</a>
                @endfor
            @endif
            @if(db.getOffshore() != null)
                <a href="/${guild.getIdLong()}/bankindex" class="m-1 btn btn-primary btn-lg border-2 border-light shadow">View Bank</a>
            @endif
            </div>
        </div>
        <div class="row">
            
        </div>
    </div>
</div>