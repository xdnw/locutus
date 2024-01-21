@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.user.Roles
@import link.locutus.discord.db.GuildDB
@import net.dv8tion.jda.api.entities.Guild
@import net.dv8tion.jda.api.entities.User
@import link.locutus.discord.db.entities.DBAlliance
@import java.util.List
@import com.google.gson.JsonArray
@import link.locutus.discord.web.jooby.WebRoot
@import link.locutus.discord.config.Settings
@param WebStore ws
@param List<String> errors
@param JsonArray nationNames
@param JsonArray nationIds
@template.main(ws = ws, title = "Select your nation", content = @`
<script src="https://cdnjs.cloudflare.com/ajax/libs/clusterize.js/0.19.0/clusterize.min.js" integrity="sha512-sCslfbDbPoJepZJxo6S3mdJwYYt0SX+C9G1SYez6/yGuXcPrZXM9tqZQMpujvMZlujVve98JSzimWbYAlQzvFQ==" crossorigin="anonymous" referrerpolicy="no-referrer"></script>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/clusterize.js/0.19.0/clusterize.min.css" integrity="sha512-8KLHxyeJ2I3BzL2ma1RZxwT1cc/U5Rz/uJg+G25tCrQ8sFfPz3MfJdKZegZDPijTxK2A3+b4kAXvzyK/OLLU5A==" crossorigin="anonymous" referrerpolicy="no-referrer" />
    <div class="container-flex rounded shadow bg-lightp-1">
    <h3>Select your nation</h3>
    <input class="form-control form-control-sm" id="nationpicker" type="text" placeholder="Search..">

    <div class="clusterize">
        <table class="table" style="background-color:#fff;color:#333">
            <thead>
            <tr>
                <th>Nation</th>
                <th>ID</th>
            </tr>
            </thead>
        </table>
        <div id="scrollArea" class="clusterize-scroll">
            <table class="table" style="background-color:#fff;color:#333">
                <tbody id="contentArea" class="clusterize-content">
                <tr class="clusterize-no-data">
                    <td>Loading data…</td>
                </tr>
                </tbody>
            </table>
        </div>
    </div>
</div>

<script>
var nationNames = $unsafe{nationNames.toString()};
var nationIds = ${nationIds.toString()};
var baseUrl = "${WebRoot.REDIRECT}";

// JavaScript
var rows = [], search = document.getElementById('nationpicker');

/* Fill array with data
 *
 * Params:
 * values *array*  - value of each field (in case use of table)
 *        example: ['1st TD content', '2nd TD content'] for table
 *                 ['list's LI item content'] for list
 * markup *string* - markup that will be added to the DOM
 * active *bool*   - specifies if row is suitable by search phrase
*/

for (var i = 0; i < nationNames.length; i++) {
    var nationName = nationNames[i];
    var nationId = nationIds[i];
    rows.push({
    values: [nationId, nationName],
    markup: "<tr><td><a href='" + baseUrl + "/page/login?nation=" + nationId + "'>" + nationName + "</a></td><td>" + nationId + "</td></tr>",
    active: true
});

}

/*
* Fetch suitable rows
*/
var filterRows = function(rows) {
  var results = [];
  for(var i = 0, ii = rows.length; i < ii; i++) {
    if(rows[i].active) results.push(rows[i].markup)
  }
  return results;
}

/*
* Init clusterize.js
*/
var clusterize = new Clusterize({
  rows: filterRows(rows),
  scrollId: 'scrollArea',
  contentId: 'contentArea'
});

/*
* Attach listener to search input tag and filter list on change
*/
var onSearch = function() {
  for(var i = 0, ii = rows.length; i < ii; i++) {
    var suitable = false;
    for(var j = 0, jj = rows[i].values.length; j < jj; j++) {
      if(rows[i].values[j].toString().indexOf(search.value) + 1)
        suitable = true;
    }
    rows[i].active = suitable;
  }
  clusterize.update(filterRows(rows));
}
search.oninput = onSearch;

</script>
`, navbar = null)