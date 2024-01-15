@import com.google.gson.JsonObject

@param String title
@param JsonObject data
@param boolean time
<div class="locutus-chart" data-src="${data.toString()}" title="${title}" time="${time}"></div>