@import com.google.gson.JsonElement
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@param WebStore ws
@param String title
@param JsonElement data
@param boolean time
@template.main(ws = ws, title = title, navbar = null, content = @`
<div class="col-sm locutus-chart" data-src="${data.toString()}" title="${title}" time="${time}"></div>
`)