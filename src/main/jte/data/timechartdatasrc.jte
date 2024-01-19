@import com.google.gson.JsonObject
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@param WebStore ws
@param String title
@param JsonObject data
@param boolean time
<div class="locutus-chart" data-src="${data.toString()}" title="${title}" time="${time}"></div>