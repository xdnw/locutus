@import link.locutus.discord.commands.manager.v2.binding.WebStore
@param WebStore ws
@param String title
@param String data
@template.main(ws = ws, title = title, content = @`
@template.data.table_data(ws = ws, title = title, data = data)
`, navbar = null)