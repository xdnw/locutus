@import java.util.*

@param String title
@param String data

@if(title != null)
    <p class="h3">${title}</p>
@endif
<div class="locutus-table-container" data-src="$unsafe{data}"></div>