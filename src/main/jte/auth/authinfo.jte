@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
!{var auth = ws.auth();}
!{var user = ws.getUser();}
!{var nation = ws.getNation();}
!{var guild = ws.getGuild();}
<table class="table table-striped table-bordered table-responsive table-sm" style="width: auto;">
@if (user != null)
    <tr>
        <td>Username</td>
        <td>${user.getName()}</td>
    </tr>
@elseif(auth.getUserIdRaw() != null)
    <tr>
        <td>User ID</td>
        <td>${auth.getUserId()}</td>
    </tr>
@endif
@if (nation != null)
    <tr>
        <td>Nation</td>
        <td><a href="${nation.getUrl()}">${nation.getName()}</a></td>
    </tr>
    @if (nation.getAlliance_id() != 0)
        <tr>
            <td>Alliance</td>
            <td><a href="${nation.getAllianceUrl()}">${nation.getAllianceName()}</a></td>
        </tr>
    @endif
@elseif (auth.getNationIdRaw() != null)
    <tr>
        <td>Nation ID</td>
        <td>${auth.getNationId()}</td>
    </tr>
@endif
    <tr>
        <td>Expires</td>
        <td>
            !{var instant = java.time.Instant.ofEpochMilli(auth.timestamp);}
            ${link.locutus.discord.util.TimeUtil.YYYY_MM_DD_HH_MM_SS.format(java.util.Date.from(instant))}
        </td>
    </tr>
    <tr>
        <td>Guild</td>
        <td>
            @if (guild != null)
            ${guild.toString()}
            <a href="/page/guildselect" class="btn btn-primary">Switch</a>
            @else
            <a href="/page/guildselect" class="btn btn-primary">Select</a>
            @endif
        </td>
    </tr>
</table>