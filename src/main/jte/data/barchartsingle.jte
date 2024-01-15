@import com.google.gson.JsonObject

@param String title
@param JsonObject data
@param boolean stacked
@template.main(title = title, content = @`
<div class="bg-white container mt-3 rounded shadow py-1">
<canvas class="locutus-barchart" data-src="${data.toString()}" title="${title}" stacked="${stacked}"></canvas>
</div>
`, navbar = null)