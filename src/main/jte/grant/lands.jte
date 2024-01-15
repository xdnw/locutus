@import java.util.*
@import link.locutus.discord.util.offshore.Grant
@import link.locutus.discord.apiv1.enums.city.project.Projects
@import link.locutus.discord.apiv1.enums.city.project.Project
@import net.dv8tion.jda.api.entities.User
@import link.locutus.discord.db.entities.DBNation
@import java.util.List
@import java.util.UUID
@import link.locutus.discord.apiv1.enums.DomesticPolicy
@import link.locutus.discord.util.TimeUtil

@param Collection<Grant> grants
@param User user
@param DBNation nation
@param Map<Grant, List<String>> failedRequirements
@param Map<Grant, List<String>> overrideRequirements
@param Map<Grant, UUID> grantTokens

@template.grant.grants(title = "Land", content = @`
    <div class="row">
        <div class="col-lg-3">
            @template.grant.nation(nation = nation)
        </div>
        <div class="col-lg-9">
            <div class="alert p-1 my-1 alert-info border border-info alert-dismissible fade show" role="alert">
                Land increases the output of your farms, as well as lowers the population density (which at low land
                levels can contribute to disease)
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
            @if(nation.getDomesticPolicy() != DomesticPolicy.RAPID_EXPANSION)
                <div class="alert p-1 my-1 alert-warning border border-warning alert-dismissible fade show"
                     role="alert">
                    <p>
                        You can go to the <a href="https://politicsandwar.com/nation/edit/"
                                             class="btn btn-secondary btn-sm">edit nation 🡕</a> page to set your
                        domestic policy to <kbd>Rapid Expansion</kbd> to save 5% on land
                    </p>
                    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                </div>
            @endif
        </div>
    </div>
    <div class="row">
        <div class="col-12">
            <h2>Search</h2>
            <input class="form-control form-control-sm" id="myInput" type="text" placeholder="Search..">
            @for (Grant grant : grants)
                @template.grant.land(grant = grant, user = user, nation = nation, failed = failedRequirements.get(grant), override = overrideRequirements.get(grant), grantToken = grantTokens.get(grant))
            @endfor
        </div>
    </div>
`)