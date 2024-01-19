@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.db.entities.DBNation
@import net.dv8tion.jda.api.entities.User
@param WebStore ws
@param long userId
@param User user
@param DBNation nation
@if(user != null)
<a href="javascript:void(0)" type="button" data-bs-toggle="modal" data-bs-target="#modal-${userId}">${user.getName()}#${user.getDiscriminator()}</a>
<div class="modal fade" id="modal-${userId}" tabindex="-1" aria-labelledby="exampleModalLabel" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="exampleModalLabel">${user.getName()}#${user.getDiscriminator()}</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body row">
                <div class="col-auto">
                    <img src="${user.getAvatarUrl()}" class="rounded" alt="Discord Avatar">
                </div>
                    <div class="col-auto">
                    <table class="table">
                        <tr>
                            <td>Discord</td>
                            <td><a href="discord://discordapp.com/users/${userId}">${user.getName()}#${user.getDiscriminator()}</a></td>
                        </tr>
                        @if(nation != null)
                        <tr>
                            <td>Nation</td>
                            <td><a href="${nation.getNationUrl()}">${nation.getNation()}</a></td>
                        </tr>
                        <tr>
                            <td>Alliance</td>
                            <td><a href="${nation.getAllianceUrl()}">${nation.getAllianceName()}</a><br></td>
                        </tr>
                        <tr>
                            <td>Cities</td>
                            <td>${nation.getCities()}</td>
                        </tr>
                        @endif
                    </table>
                </div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
            </div>
        </div>
    </div>
</div>

@elseif (nation != null)
<a href="${nation.getNationUrl()}">${nation.getName()}</a>
@else
<a href="https://discordapp.com/users/${userId}">&lt;@${userId}&gt; (unknown user)</a>
@endif