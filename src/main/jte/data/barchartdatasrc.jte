@import com.google.gson.JsonElement
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@param WebStore ws
@param String title
@param JsonElement data
@param boolean stacked
<span class="d-none">${title}</span>
<canvas class="locutus-barchart" data-src="${data.toString()}" title="${title}" stacked="${stacked}"></canvas>