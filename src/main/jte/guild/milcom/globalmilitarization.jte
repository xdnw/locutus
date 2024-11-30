@import java.util.Collections
@import java.util.HashSet
@import java.util.Map
@import java.util.Set
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.rankings.SphereGenerator
@import link.locutus.discord.commands.manager.v2.table.TableNumberFormat
@import link.locutus.discord.commands.manager.v2.table.TimeFormat
@import link.locutus.discord.db.entities.metric.AllianceMetric
@import link.locutus.discord.db.entities.DBAlliance
@import link.locutus.discord.util.TimeUtil
@import link.locutus.discord.web.WebUtil
@param WebStore ws
@param SphereGenerator spheres
@param Set<DBAlliance> alliances
@param Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap
@param Set<AllianceMetric> metrics
@param long startTurn
@param long endTurn
@template.main(ws = ws, title = "Global Stats", navbar = null, content = @`


<h2 class="text-white">Search</h2>
<input class="form-control form-control-sm" id="myInput" type="text" placeholder="Search..">

@for (Integer sphereId : spheres.getSpheres())
    @if(!Collections.disjoint(metricMap.keySet(), spheres.getAlliances(sphereId)))
    <div class="bg-lightmt-3 rounded shadow py-1 searchable accordion" id="Accordion${sphereId}">
        <div class="accordion-item">
            <div class="accordion-header" id="heading${sphereId}">
                <button class="accordion-button p-1 btn-lg" type="button" data-bs-toggle="collapse" data-bs-target="#collapse${sphereId}" aria-expanded="true" aria-controls="collapse${sphereId}">
                    <h3>${spheres.getSphereName(sphereId)} total</h3>
                </button>
            </div>
            <div id="collapse${sphereId}" class="accordion-collapse collapse show" aria-labelledby="heading${sphereId}" data-bs-parent="#Accordion${sphereId}">
                <div class="accordion-body bg-light">
                    @if(spheres.getAlliances(sphereId).size() > 1)
                    <div class="bg-light border border-3 border-secondary rounded searchable">
                        @template.data.timechartdatasrc(ws = ws, title = spheres.getSphereName(sphereId) + " total",
                        data = WebUtil.GSON.toJsonTree(AllianceMetric.generateTable(metricMap, metrics, startTurn, endTurn,
                        spheres.getSphereName(sphereId),
                        new HashSet<>(spheres.getAlliances(sphereId))
                        ).convertTurnsToEpochSeconds(startTurn).toHtmlJson(TimeFormat.MILLIS_TO_DATE, TableNumberFormat.PERCENTAGE_ONE, TimeUtil.getTimeFromTurn(startTurn) / 1000L)),
                        time = true)
                    </div>
                    <hr>
                    @endif
                    @for (DBAlliance alliance : spheres.getAlliances(sphereId))
                    @if(metricMap.containsKey(alliance))
                    <div class="card searchable" style="display:inline-block;width:36rem;min-height:18rem;vertical-align: top;">
                        @template.data.timechartdatasrc(ws = ws, title = alliance.getName(),
                        data = WebUtil.GSON.toJsonTree(AllianceMetric.generateTable(metricMap, metrics, startTurn, endTurn, alliance.getName(),
                        Collections.singleton(alliance)
                        ).convertTurnsToEpochSeconds(startTurn).toHtmlJson(TimeFormat.TURN_TO_DATE, TableNumberFormat.PERCENTAGE_ONE, TimeUtil.getTimeFromTurn(startTurn) / 1000L)),
                        time = true)
                    </div>
                    @endif
                    @endfor
                </div>
            </div>
        </div>
    </div>
    @endif
@endfor
<script>
$(document).ready(function(){
    $("#myInput").on("keyup", function() {
        var value = $(this).val().toLowerCase();
        $(".searchable").filter(function() {
        $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)
        });
    });
});
</script>
`)