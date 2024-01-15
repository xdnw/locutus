@import com.google.gson.JsonObject

@param String title
@param JsonObject data
@param boolean stacked
<canvas class="locutus-barchart" data-src="${data.toString()}" title="${title}" stacked="${stacked}"></canvas>