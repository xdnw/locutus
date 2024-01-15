@import link.locutus.discord.util.offshore.Grant
@import link.locutus.discord.apiv1.enums.city.project.Projects
@import link.locutus.discord.apiv1.enums.city.project.Project
@import link.locutus.discord.util.PnwUtil
@import net.dv8tion.jda.api.entities.User
@import link.locutus.discord.db.entities.DBNation
@import link.locutus.discord.util.MathMan
@import java.util.List
@import java.util.UUID
@import link.locutus.discord.util.MarkupUtil
@import link.locutus.discord.util.MathMan

@param Grant grant
@param User user
@param DBNation nation
@param List<String> failed
@param List<String> override
@param UUID grantToken

!{var costFull = PnwUtil.resourcesToString(grant.cost());}
!{var costWorth = MathMan.format(PnwUtil.convertedTotal(grant.cost()));}

<div class="row p-2 m-3 guild-icon guild-entry bg-light" style="border-radius:10px">
    <div class="col-md-2">
        <img alt="guild-icon" class="img-fluid guild-icon" src="https://upload.wikimedia.org/wikipedia/commons/thumb/2/29/Taipei%2C_Taiwan_CBD_Skyline.jpg/220px-Taipei%2C_Taiwan_CBD_Skyline.jpg" onerror="this.style.display='none'">
    </div>
    <div class="col-md-10 p-2">
        <div id="body${grantToken + ""}">
            <h4 class="">City ${nation.getCities()}->${nation.getCities() + MathMan.parseInt(grant.getAmount())}</h4>
            <p class="lead">$unsafe{MarkupUtil.transformURLIntoLinks(grant.getInstructions())}</p>
            <p class="">${costFull} worth: ~$${costWorth}</p>
            <p class="">${grant.getNote()}</p>
            @if(failed != null)
                @for (String message : failed)
                    <div class="alert p-1 my-1 alert-danger border border-danger" role="alert">${message}</div>
                @endfor
            @endif
            @if(override != null)
                @for (String message : override)
                    <div class="alert p-1 my-1 alert-warning border border-warning" role="alert">${message}</div>
                @endfor
            @endif
        </div>
        <div class="row">
            <div class="col">
            @if(failed == null)
                <button onclick="promptGrant('Confirm Grant', document.getElementById('body${grantToken + ""}').innerHTML, '${grantToken + ""}', true)" class="m-1 btn-lg btn
                    @if(override == null)
                        btn-primary">Send Grant
                    @else
                        btn-warning">Send Grant (Admin Override)
                    @endif
                </button>
            @else
                <div class="alert p-1 my-1 alert-danger border border-danger" role="alert">
                    Unable to automatically approve this grant (open a grant channel and discuss with econ for assistance)
                </div>
            @endif
            </div>
        </div>
    </div>
</div>