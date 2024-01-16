@import java.util.*
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.util.offshore.Grant
@import link.locutus.discord.apiv1.enums.city.project.Projects
@import link.locutus.discord.apiv1.enums.city.project.Project
@import net.dv8tion.jda.api.entities.User
@import link.locutus.discord.db.entities.DBNation
@import java.util.List
@import java.util.UUID
@import link.locutus.discord.apiv1.enums.DomesticPolicy
@import link.locutus.discord.util.TimeUtil

@param WebStore ws
@param Set<Project> recommendedProjects
@param Collection<Grant> grants
@param User user
@param DBNation nation
@param Map<Grant, List<String>> failedRequirements
@param Map<Grant, List<String>> overrideRequirements
@param Map<Grant, UUID> grantTokens

@template.grant.grants(ws = ws, title = "Project", content = @`
    <div class="row">
        <div class="col-lg-3">
            @if(nation.projectSlots() <= nation.getNumProjects())
                <div class="alert p-1 my-1 alert-danger border border-danger alert-dismissible fade show" role="alert">
                    Nation already has ${nation.getNumProjects()}/${nation.projectSlots()} projects
                </div>
            @endif
            <div class="alert p-1 my-1 alert-info border border-info alert-dismissible fade show" role="alert">
                Projects are nation level improvements that provide benefits to all your cities.<br>
                <a href="https://politicsandwar.com/nation/projects/" class="btn btn-secondary btn-sm">Visit Projects
                    Page 🡕</a>
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
            <div class="alert p-1 my-1 alert-info border border-info alert-dismissible fade show" role="alert">
                <b>City timer: </b>${nation.getCityTurns()} turns (${TimeUtil.turnsToTime(nation.getCityTurns())})<br>
                <b>Project timer: </b>${nation.getProjectTurns()} turns
                (${TimeUtil.turnsToTime(nation.getProjectTurns())})<br>
                <b>Project Slots: </b>${nation.getNumProjects()}/${nation.projectSlots()}<br>
                <b>Infrastructure: </b>${nation.getInfra()} (${5000 - ((nation.getInfra() % 5000))} more infra until
                next
                slot)<br>
                <p>
                    You receive a slot every 5,000 total infrastructure in your nation or when your nation has won or
                    lost 100
                    wars.
                </p>
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
            @if(nation.getDomesticPolicy() != DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT)
                <div class="alert p-1 my-1 alert-warning border border-warning alert-dismissible fade show"
                     role="alert">
                    <p>
                        Go to the <a href="https://politicsandwar.com/nation/edit/" class="btn btn-secondary btn-sm">edit
                            nation
                            🡕</a> page to set your domestic policy to <kbd>Technological Advancement</kbd> to save 5% on
                        projects
                    </p>
                    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                </div>
            @endif
            @if(nation.resourcesProducedProjects().containsValue(false))
                <div class="alert p-1 my-1 alert-info border border-info alert-dismissible fade show" role="alert">
                    <p>Currently producing without project:</p><br>
                    <ul>
                        @for (Map.Entry<Project,Boolean> entry : nation.resourcesProducedProjects().entrySet())
                            <li>${entry.getKey().name()}</li>
                        @endfor
                    </ul>
                    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                </div>
            @endif
            @if(nation.getCities() > 8 && nation.getCities() <= 11 && nation.projectSlots() - nation.getNumProjects() <=1 && !nation.hasProject(Projects.URBAN_PLANNING))
                <div class="alert p-1 my-1 alert-warning border border-warning alert-dismissible fade show"
                     role="alert">
                    The urban planning can purchased at city 11 and reduces future cities by $50M
                </div>
            @endif
            @template.grant.nation(ws = ws, nation = nation)
            @if(nation.getNumProjects() > 0 || !recommendedProjects.isEmpty())
                <div class="bg-white card mt-3 rounded shadow py-1">
                    @if(nation.getNumProjects() > 0)
                        <h4>Existing Projects</h4>
                        <ul class="list-group">
                            @for (Project project : nation.getProjects())
                                <li class="list-group-item">${project.name()}</li>
                            @endfor
                        </ul>
                        @if(!recommendedProjects.isEmpty())
                            <hr>
                        @endif
                    @endif
                    @if(!recommendedProjects.isEmpty())
                        <h4>Recommended Projects</h4>
                        <ul class="list-group">
                            @for (Project project : recommendedProjects)
                                <li class="list-group-item">${project.name()}</li>
                            @endfor
                        </ul>
                    @endif
                </div>
            @endif
        </div>
        <div class="col-lg-9">
            <div class="container-fluid input-group input-group-lg">
                <input class="form-control form-control-lg" id="myInput" type="text" placeholder="Filter..">
            </div>
            @for (Grant grant : grants)
                @template.grant.project(ws = ws, project = Projects.get(grant.getAmount()), grant = grant, user = user, nation = nation, failed = failedRequirements.get(grant), override = overrideRequirements.get(grant), grantToken = grantTokens.get(grant))
            @endfor
        </div>
    </div>
`)