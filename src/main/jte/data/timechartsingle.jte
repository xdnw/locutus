@import com.google.gson.JsonObject

@param String title, JsonObject data, boolean time
@template.main(title = title, navbar = null, content = @`
<div class="col-sm locutus-chart" data-src="$unsafe{data.toString()}" title="${title}" time="${time}"></div>
`)