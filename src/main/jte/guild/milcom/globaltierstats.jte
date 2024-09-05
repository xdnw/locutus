@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble
@import link.locutus.discord.commands.rankings.SphereGenerator
@import link.locutus.discord.commands.manager.v2.table.TableNumberFormat
@import link.locutus.discord.commands.manager.v2.table.TimeFormat
@import link.locutus.discord.db.entities.DBAlliance
@import java.io.IOException
@import java.util.Collections
@import java.util.Set
@import link.locutus.discord.commands.manager.v2.table.TimeNumericTable
@param WebStore ws
@param SphereGenerator spheres
@param Set<DBAlliance> alliances
@param Set<NationAttributeDouble> metrics
@param NationAttributeDouble groupBy
@param boolean total
@param boolean removeVM
@param int removeActiveM
@param boolean removeApps
@template.main(ws = ws, title = "Global Stats", navbar = null, content = @`
<h2 class="text-white">Search</h2>
<input class="form-control" id="myInput" type="text" placeholder="Search alliance or sphere..">
@for (Integer sphereId : spheres.getSpheres())
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
                        @template.data.barchartdatasrc(ws = ws, title = spheres.getSphereName(sphereId) + " total",
                        data = TimeNumericTable.create(spheres.getSphereName(sphereId) + ": ", metrics,
                        spheres.getAlliances(sphereId), groupBy, total, removeVM, removeActiveM, removeApps
                        ).toHtmlJson(TimeFormat.SI_UNIT, TableNumberFormat.SI_UNIT, 0),
                        stacked = false)
                    </div>
                    <hr>
                    @endif
                    @for (DBAlliance alliance : spheres.getAlliances(sphereId))
                        <div class="card searchable" style="display:inline-block;width:36rem;min-height:18rem;vertical-align: top;">
                            @template.data.barchartdatasrc(ws = ws, title = alliance.getName(),
                            data = TimeNumericTable.create(alliance.getName() + ": ", metrics, Collections.singleton(alliance),
                            groupBy, total, removeVM, removeActiveM, removeApps
                            ).toHtmlJson(TimeFormat.SI_UNIT, TableNumberFormat.SI_UNIT, 0),
                            stacked = false)
                        </div>
                    @endfor
                </div>
            </div>
        </div>
    </div>
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