@import com.google.gson.JsonElement
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@param WebStore ws
@param String title
@param JsonElement data
@param boolean time
@template.main(ws = ws, title = title, content = @`
<div class="bg-lightcontainer mt-3 rounded shadow py-1">
    @template.data.timechartdatasrc(ws = ws, title = title, data = data, time = time)
</div>
`, navbar = null)