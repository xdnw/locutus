@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import java.util.*

@param WebStore ws
@param String title
@param String data
@template.main(ws = ws, title = "Hello World", content = @`
@template.data.table_data(ws = ws, title = title, data = data)
`, navbar = null)