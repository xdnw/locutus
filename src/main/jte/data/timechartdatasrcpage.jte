@import com.google.gson.JsonObject

@param String title
@param JsonObject data
@param boolean time
@template.main(title = title, content = @`
<div class="bg-white container mt-3 rounded shadow py-1">
    @template.data.timechartdatasrc(title = title, data = data, time = time)
</div>
`, navbar = null)