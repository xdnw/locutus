@import com.google.gson.JsonElement
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@param WebStore ws
@param String title
@param JsonElement data
@param boolean time
<div class="locutus-chart" data-src="${data.toString()}" title="${title}" time="${time}"></div>