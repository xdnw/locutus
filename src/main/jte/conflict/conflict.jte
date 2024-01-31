@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.web.jooby.WebRoot
@param WebStore ws
@param String title
@param String data
@template.main(navbar = null, ws = ws, title = title, content = @`
    hello world
    <div class="modal fade" id="colModal" tabindex="-1" aria-labelledby="exampleModalLabel" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title" id="colModalTitle"></h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body" id="colModalBody">
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
                </div>
            </div>
        </div>
    </div>
    <div id="conflict-table-1"></div>
`)
<script>
function formatNumber(data, type, row, meta) {
    return data.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}
/*
CoalitionEntry[]

CoalitionEntry:
- string name
- int[] alliance_ids
- string[] alliance_names
- int[] nation_ids
- string[] nation_names
- counts[]: OffDefStatGroup

OffDefStatGroup
- int wars_total
- int wars_active
- int attacks
- int wars_won
- int wars_lost
- int wars_expired
- int wars_peaced

// format for table data
let dataColumns = dataSetRoot["columns"];
let dataList = dataSetRoot["data"];
let searchableColumns = dataSetRoot["searchable"];
let visibleColumns = dataSetRoot["visible"];
let cell_format = dataSetRoot["cell_format"];
let row_format = dataSetRoot["row_format"];
let sort = dataSetRoot["sort"];
if (sort == null) sort = [0, 'asc'];
*/
function coalitionNames(data, type, row, meta) {
    let button = document.createElement("button");
    button.setAttribute("type", "button");
    button.setAttribute("class", "btn btn-primary btn-sm");
    button.setAttribute("data-bs-toggle", "modal");
    button.setAttribute("data-bs-target", "#colModal");
    button.setAttribute("data-bs-name", data[0]);
    button.setAttribute("data-bs-alliance-ids", JSON.stringify(data[1]));
    button.setAttribute("data-bs-alliance-names", JSON.stringify(data[2]));
    var modalElem = document.getElementById('colModal');
    modalElem.addEventListener('show.bs.modal', function (event) {
        var button = event.relatedTarget;
        var name = button.getAttribute('data-bs-name');
        var alliance_ids = JSON.parse(button.getAttribute('data-bs-alliance-ids'));
        var alliance_names = JSON.parse(button.getAttribute('data-bs-alliance-names'));
        var modalTitle = document.getElementById('colModalTitle');
        modalTitle.textContent = name;
        var modalBody = document.getElementById('colModalBody');
        let ul = document.createElement("ul");
        for (let i = 0; i < alliance_ids.length; i++) {
            let alliance_id = alliance_ids[i];
            let alliance_name = alliance_names[i];
            let a = document.createElement("a");
            a.setAttribute("href", "https://politicsandwar.com/alliance/id=" + alliance_id);
            a.textContent = alliance_name;
            modalBody.appendChild(a);
            let li = document.createElement("li");
            li.appendChild(a);
            ul.appendChild(li);
        }
        modalBody.innerHTML = data[1].toString().replaceAll(",", ", ");
        modalBody.appendChild(ul);
    })
    button.textContent = data[0];
    return button.outerHTML;
}
var _rawData = JSON.parse(atob("${data}"));
function initConflictTables() {
    let coalitions = _rawData["coalitions"];
    let counts_header = _rawData["counts_header"];
    let damage_header = _rawData["damage_header"];

    let rows = [];
    let columns = [];

    let searchable = [];
    let visible = [0];
    let sort = [0, 'asc'];
    let cell_format = {};


    let searchColNames = ["names","wars_off"]

    { // columns
        columns.push("name");
        for (let i = 0; i < counts_header.length; i++) {
            let header = counts_header[i];
            columns.push(header + "_off");
            columns.push(header + "_def");
            columns.push(header + "_total");
        }
        for (let i = 0; i < damage_header.length; i++) {
            let header = damage_header[i];
            columns.push(header);
            columns.push(header + "_dealt");
            columns.push(header + "_net");
        }

        searchable.push(0);
        cell_format["coalitionNames"] = [0];
    }

    // range [1, ... columns length]
    cell_format["formatNumber"] = Array.from({length: columns.length}, (_, i) => i + 1);
    console.log(cell_format["formatNumber"] + " | " + columns.length);

    for (let i = 0; i < coalitions.length; i++) {
        let colEntry = coalitions[i];
        let name = colEntry["name"];
        let alliance_ids = colEntry["alliance_ids"];
        let alliance_names = colEntry["alliance_names"];
        let nation_ids = colEntry["nation_ids"];
        let nation_names = colEntry["nation_names"];
        let stats = colEntry["counts"];
        let damage = colEntry["damage"];

        let row = [];
        row.push([name,alliance_ids,alliance_names]);
        {
            let offStats = stats[0];
            let defStats = stats[1];

            for (let i = 0; i < offStats.length; i++) {
                let offStat = offStats[i];
                let defStat = defStats[i];
                let totalStat = offStat + defStat;
                row.push(offStat);
                row.push(defStat);
                row.push(totalStat);
            }

            let damageTaken = damage[0];
            let damageDealt = damage[1];

            for (let i = 0; i < damageTaken.length; i++) {
                let damageTakenStat = damageTaken[i];
                let damageDealtStat = damageDealt[i];
                let damageNetStat = damageDealtStat - damageTakenStat;
                row.push(damageTakenStat);
                row.push(damageDealtStat);
                row.push(damageNetStat);
            }
        }
       rows.push(row);
    }
    // log rows size
    console.log("rows size: " + rows.length);
    // log row0 length
    console.log("row0 length: " + rows[0].length);
    // log header size
    console.log("header size: " + columns.length);
    let data = {
        "columns": columns,
        "data": rows,
        "searchable": searchable,
        "visible": visible,
        "sort": sort,
        "cell_format": cell_format
    };
    let container = document.getElementById("conflict-table-1");
    setupContainer(container, data);
}

$(document).ready(function() {
    initConflictTables();
    $("button").click(function(e) {
       e.stopPropagation();
    });
});

</script>