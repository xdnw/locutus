@import com.google.gson.JsonObject
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@param WebStore ws
@param String title
@param JsonObject data
@param boolean stacked
@template.main(ws = ws, title = title, content = @`
<div class="bg-lightcontainer mt-3 rounded shadow py-1">
<canvas class="locutus-barchart" data-src="${data.toString()}" title="${title}" stacked="${stacked}"></canvas>
</div>
`, navbar = null)